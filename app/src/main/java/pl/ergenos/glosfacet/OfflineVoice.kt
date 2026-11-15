// SPDX-License-Identifier: GPL-3.0-only
// Model inference adapted from the published Matcha-TTS-PL-Edge-v2 pipeline.
package pl.ergenos.glosfacet

import ai.onnxruntime.*
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Properties
import java.util.Random
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Persistent offline sessions. Created, used and released on the speech worker. */
internal class OfflineVoice(private val root: File) {
    data class Audio(val samples: FloatArray, val sampleRate: Int)
    private val environment = OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }
    private val phonemizer = PolishPhonemizer(File(root, "espeak-ng-data"))
    private val settings = Properties().apply { File(root, "voice.properties").inputStream().use { load(it) } }
    private val speaker = settings.getProperty("speaker").split(',').map { it.toFloat() }.toFloatArray()
    private val temperature = settings.getProperty("temperature").toFloat()
    private val lengthScale = settings.getProperty("lengthScale").toFloat()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        setInterOpNumThreads(1)
    }
    private val encoder = environment.createSession(File(root, "encoder.onnx").absolutePath, options)
    private val decoder: OrtSession
    private val vocoder: OrtSession
    private val runLock = Any()
    private var running: OrtSession.RunOptions? = null
    init {
        try {
            decoder = environment.createSession(File(root, "decoder_t4.onnx").absolutePath, options)
            try { vocoder = environment.createSession(File(root, "vocoder.onnx").absolutePath, options) }
            catch (error: Throwable) { decoder.close(); throw error }
        } catch (error: Throwable) { encoder.close(); options.close(); throw error }
        check(speaker.size == 64 && speaker.all { it.isFinite() }) { "Nieprawidłowe dane głosu" }
    }
    fun cancel() { synchronized(runLock) { running?.setTerminate(true) } }
    private fun run(session: OrtSession, tensors: Map<String, OnnxTensor>, cancelled: AtomicBoolean): OrtSession.Result {
        val option = OrtSession.RunOptions()
        try {
            synchronized(runLock) {
                if (cancelled.get()) throw CancellationException()
                running = option
            }
            return session.run(tensors, option)
        } finally {
            synchronized(runLock) { running = null; option.close() }
        }
    }
    private fun floats(values: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), shape)
    private fun longs(values: LongArray, vararg shape: Long) = OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape)
    private fun sentence(text: String, speed: Float, cancelled: AtomicBoolean): FloatArray {
        if (cancelled.get()) throw CancellationException()
        val ids = phonemizer.textToIds(text)
        val inputs = linkedMapOf<String, OnnxTensor>()
        val random = Random(42)
        var frames = 0
        var mu = FloatArray(0)
        try {
            inputs["x"] = longs(ids, 1, ids.size.toLong())
            inputs["x_lengths"] = longs(longArrayOf(ids.size.toLong()), 1)
            inputs["spk_emb"] = floats(speaker, 1, 64)
            inputs["scales"] = floats(floatArrayOf(temperature, lengthScale/speed), 2)
            run(encoder, inputs, cancelled).use { result ->
                val prior = result[0] as OnnxTensor
                frames = (result[1] as OnnxTensor).longBuffer.get().toInt()
                val shape = prior.info.shape
                check(frames in 1..6000 && shape.size == 3 && shape[1] == 80L && shape[2] >= frames) { "Nieprawidłowa długość nagrania" }
                val raw = FloatArray(prior.floatBuffer.remaining()).also { prior.floatBuffer.get(it) }
                mu = FloatArray(80*frames)
                for (channel in 0 until 80) raw.copyInto(mu, channel*frames, channel*shape[2].toInt(), channel*shape[2].toInt()+frames)
            }
        } finally { inputs.values.forEach { it.close() }; inputs.clear() }
        val padded = (frames+3)/4*4
        val muPadded = FloatArray(80*padded)
        val noise = FloatArray(80*padded)
        for (channel in 0 until 80) {
            mu.copyInto(muPadded,channel*padded,channel*frames,(channel+1)*frames)
            for (frame in 0 until frames) noise[channel*padded+frame] = random.nextGaussian().toFloat()
        }
        var mel = FloatArray(0)
        try {
            inputs["mu"] = floats(muPadded,1,80,padded.toLong())
            inputs["z"] = floats(noise,1,80,padded.toLong())
            inputs["mask"] = floats(FloatArray(padded) { if (it < frames) 1f else 0f },1,1,padded.toLong())
            inputs["spk_emb"] = floats(speaker,1,64)
            inputs["temperature"] = floats(floatArrayOf(temperature))
            run(decoder, inputs, cancelled).use { result ->
                val tensor = result[0] as OnnxTensor
                check(tensor.info.shape.contentEquals(longArrayOf(1,80,padded.toLong()))) { "Nieprawidłowe dane syntezy" }
                val raw = FloatArray(80*padded).also { tensor.floatBuffer.get(it) }
                mel = FloatArray(80*frames)
                for (channel in 0 until 80) raw.copyInto(mel,channel*frames,channel*padded,channel*padded+frames)
            }
        } finally { inputs.values.forEach { it.close() }; inputs.clear() }
        try {
            inputs["mel"] = floats(mel,1,80,frames.toLong())
            run(vocoder, inputs, cancelled).use { result ->
                val tensor = result[0] as OnnxTensor
                val count = tensor.floatBuffer.remaining()
                check(count >= frames*256) { "Niepełne nagranie" }
                return FloatArray(frames*256).also { output ->
                    tensor.floatBuffer.get(output)
                    check(output.all { it.isFinite() }) { "Nieprawidłowe próbki nagrania" }
                    for (i in output.indices) output[i] = output[i].coerceIn(-1f,1f)
                }
            }
        } finally { inputs.values.forEach { it.close() } }
    }
    fun generate(text: String, speed: Float, cancelled: AtomicBoolean): Audio {
        require(speed.isFinite() && speed in 0.75f..1.45f)
        // Split at whitespace to bound memory, preserving punctuation for the trained frontend.
        val parts = mutableListOf<String>(); val pending = StringBuilder()
        for (word in text.trim().split(Regex("\\s+"))) {
            require(word.length <= 400) { "Wypowiedź zawiera zbyt długi wyraz" }
            if (pending.length+word.length > 220 && pending.isNotEmpty()) { parts.add(pending.toString()); pending.setLength(0) }
            if (pending.isNotEmpty()) pending.append(' ')
            pending.append(word)
        }
        if (pending.isNotEmpty()) parts.add(pending.toString())
        val chunks = parts.map { sentence(it, speed, cancelled) }
        if (cancelled.get()) throw CancellationException()
        val size = chunks.sumOf { it.size.toLong() }
        check(size in 1..(22050L*600)) { "Nieprawidłowa długość nagrania" }
        val joined = FloatArray(size.toInt()); var at = 0
        for (chunk in chunks) { chunk.copyInto(joined, at); at += chunk.size }
        return Audio(joined,22050)
    }
    fun release() { vocoder.close(); decoder.close(); encoder.close(); options.close() }
}
