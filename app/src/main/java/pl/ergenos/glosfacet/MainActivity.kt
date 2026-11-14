// SPDX-License-Identifier: GPL-3.0-only
package pl.ergenos.glosfacet

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    companion object { private val modelCopyLock = Any() }
    // JNI in sherpa-onnx 1.12.20 looks up invoke(float[]): Integer explicitly.
    // An invokedynamic lambda only exposes invoke(Object), which breaks that lookup.
    private class SynthesisCallback(private val cancelled: AtomicBoolean) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int = if (cancelled.get()) 0 else 1
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private var engine: OfflineTts? = null
    private var player: MediaPlayer? = null
    private var ready = false
    private var busy = false
    @Volatile private var dead = false
    private var generation = 0
    private var speed = 1f
    private var audio: File? = null
    private var audioSamples: FloatArray? = null
    private var audioRate = 22050
    private var audioText: String? = null
    private var audioSpeed = 1f
    private var attachment: File? = null
    private var pendingExport: File? = null
    private val phrases = mutableListOf<String>()
    private val phraseButtons = mutableListOf<Button>()
    private lateinit var phraseGrid: LinearLayout
    private lateinit var addPhrase: Button
    private lateinit var share: Button
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var counter: TextView
    private lateinit var speak: Button
    private lateinit var stop: Button
    private lateinit var export: Button
    private lateinit var progress: ProgressBar
    private lateinit var audioManager: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val ink = Color.rgb(235, 245, 239)
    private val muted = Color.rgb(157, 179, 166)
    private val green = Color.rgb(145, 228, 188)
    private val surface = Color.rgb(26, 39, 33)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change -> if (change < 0) stopReading() }.build()
        volumeControlStream = AudioManager.STREAM_MUSIC
        val prefs = getPreferences(MODE_PRIVATE)
        speed = prefs.getFloat("speed", 1f).coerceIn(0.75f, 1.45f)
        loadPhrases()
        buildUi()
        savedInstanceState?.getString("pendingExport")?.let {
            val file = File(it)
            if (file.parentFile == cacheDir && file.name.startsWith("export-") && file.exists()) pendingExport = file
        }
        // Keep shared attachments available after the activity closes; prune only old files.
        File(cacheDir, "shared_audio").listFiles()?.filter {
            System.currentTimeMillis() - it.lastModified() > 48 * 60 * 60 * 1000L
        }?.forEach { it.delete() }
        input.setText(savedInstanceState?.getString("text") ?: prefs.getString("text", ""))
        input.setSelection(input.length())
        initializeVoice()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Float = 18f) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius.toInt()).toFloat()
    }
    private fun label(value: String, size: Float, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
    }
    private fun button(value: String, primary: Boolean = false) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 18f; minHeight = dp(64)
        setTextColor(if (primary) Color.rgb(16, 35, 24) else ink)
        backgroundTintList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Color.rgb(43, 56, 48), if (primary) green else surface))
        setPadding(dp(12), dp(4), dp(12), dp(4))
    }
    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, view ->
            addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i < views.lastIndex) marginEnd = dp(8)
            })
        }
    }
    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true; setBackgroundColor(Color.rgb(16, 24, 22))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        scroll.addView(column)
        if (android.os.Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        } else scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        fun add(view: View, top: Int = 0) {
            column.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
        }
        add(label("OpenSpeech", 30f).apply { setTypeface(null, Typeface.BOLD) })
        input = EditText(this).apply {
            hint = "Co chcesz powiedzieć?"; textSize = 22f
            setTextColor(ink); setHintTextColor(muted); gravity = Gravity.TOP or Gravity.START
            minLines = 3; maxLines = 7
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(2000))
            background = rounded(surface); setPadding(dp(16), dp(14), dp(16), dp(14))
            contentDescription = "Wpisz tekst do powiedzenia"
        }
        add(input, 16)
        counter = label("0 / 2000", 12f, muted).apply { gravity = Gravity.END }
        add(counter, 4)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                counter.text = getString(R.string.character_count, s?.length ?: 0); updateButtons()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        speak = button("Mów", true).apply { isEnabled = false; setOnClickListener { readText() } }
        stop = button("Stop").apply { isEnabled = false; setOnClickListener { stopReading() } }
        add(row(speak, stop), 6)
        share = button("Udostępnij audio").apply {
            isEnabled = false; setOnClickListener { readText(shareAfter = true) }
        }
        add(share, 4)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true; visibility = View.GONE
        }
        add(progress, 4)
        status = label("Przygotowuję głos…", 14f, muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        add(status, 4)
        add(row(button("Wklej").apply { setOnClickListener {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            if (clip != null && clip.itemCount > 0) {
                input.setText(clip.getItemAt(0).coerceToText(this@MainActivity).take(2000))
            } else Toast.makeText(this@MainActivity, "Schowek jest pusty", Toast.LENGTH_SHORT).show()
        } }, button("Wyczyść").apply { setOnClickListener { input.text.clear() } }), 8)
        add(label("Szybkie komunikaty", 19f).apply { setTypeface(null, Typeface.BOLD) }, 20)
        phraseGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(phraseGrid, 8)
        addPhrase = button("Zapisz tekst jako komunikat").apply {
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener
                if (phrases.contains(text)) {
                    Toast.makeText(this@MainActivity, "Ten komunikat jest już zapisany", Toast.LENGTH_SHORT).show()
                } else if (phrases.size < 16) {
                    phrases.add(text); savePhrases(); renderPhrases()
                    Toast.makeText(this@MainActivity, "Komunikat zapisany", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this@MainActivity, "Możesz zapisać do 16 komunikatów", Toast.LENGTH_SHORT).show()
            }
        }
        add(addPhrase, 4)
        add(label("Tempo czytania", 16f), 20)
        val tempo = label("1,00×", 14f, muted)
        val speedControl = SeekBar(this).apply {
            max = 70; progress = ((speed - 0.75f) * 100).toInt().coerceIn(0, 70)
            contentDescription = "Tempo czytania"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    speed = 0.75f + p / 100f
                    tempo.text = getString(R.string.speed_value, String.format(Locale.forLanguageTag("pl"), "%.2f", speed))
                    updateButtons()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        tempo.text = getString(R.string.speed_value, String.format(Locale.forLanguageTag("pl"), "%.2f", speed))
        add(speedControl, 4); add(tempo)
        export = button("Zapisz nagranie WAV").apply {
            isEnabled = false; setOnClickListener {
                val current = audio ?: return@setOnClickListener
                // Own copy: a new utterance may replace the preview while the document picker is open.
                val snapshot = File.createTempFile("export-", ".wav", cacheDir)
                try {
                    current.copyTo(snapshot, overwrite = true)
                    pendingExport?.delete(); pendingExport = snapshot
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "audio/wav"
                        putExtra(Intent.EXTRA_TITLE, "OpenSpeech.wav")
                    }, 100)
                } catch (e: Exception) {
                    snapshot.delete(); Toast.makeText(this@MainActivity, "Nie można otworzyć zapisu", Toast.LENGTH_SHORT).show()
                }
            }
        }
        add(export, 12)
        renderPhrases()
        setContentView(scroll)
    }
    private fun loadPhrases() {
        val defaults = listOf("Tak", "Nie", "Dziękuję", "Proszę poczekać", "Potrzebuję pomocy", "Chcę coś powiedzieć", "Nie mogę mówić", "Proszę napisać")
        val saved = getPreferences(MODE_PRIVATE).getString("phrases", null)
        if (saved == null) { phrases.addAll(defaults); return }
        try {
            val items = org.json.JSONArray(saved)
            for (i in 0 until minOf(items.length(), 16)) {
                val value = items.optString(i).trim().take(2000)
                if (value.isNotEmpty()) phrases.add(value)
            }
        } catch (e: Exception) { phrases.addAll(defaults) }
    }
    private fun savePhrases() {
        getPreferences(MODE_PRIVATE).edit().putString("phrases", org.json.JSONArray(phrases).toString()).apply()
    }
    private fun renderPhrases() {
        phraseGrid.removeAllViews(); phraseButtons.clear()
        phrases.chunked(2).forEachIndexed { rowIndex, pair ->
            val buttons = pair.mapIndexed { columnIndex, phrase ->
                val index = rowIndex * 2 + columnIndex
                button(phrase).apply {
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    isEnabled = ready && !busy
                    setOnClickListener {
                        input.setText(phrase); input.setSelection(input.length()); readText()
                    }
                    setOnLongClickListener { editPhrase(index); true }
                    contentDescription = "$phrase. Przytrzymaj, aby zmienić komunikat."
                    phraseButtons.add(this)
                }
            }
            val views = buttons.map { it as View }.toMutableList()
            if (views.size == 1) views.add(Space(this))
            phraseGrid.addView(row(*views.toTypedArray()))
        }
        updateButtons()
    }
    private fun editPhrase(index: Int) {
        if (busy) return
        val field = EditText(this).apply {
            setText(phrases[index]); textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(2000))
            setSelectAllOnFocus(true)
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Edytuj komunikat").setView(field)
            .setPositiveButton("Zapisz", null)
            .setNeutralButton("Usuń") { _, _ -> phrases.removeAt(index); savePhrases(); renderPhrases() }
            .setNegativeButton("Anuluj", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text.toString().trim()
                if (value.isEmpty()) { field.error = "Wpisz komunikat" } else {
                    phrases[index] = value; savePhrases(); renderPhrases(); dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun initializeVoice() {
        worker.execute {
            try {
                val root = File(filesDir, "voice-v1")
                // Copy once: native phonemizer needs ordinary filesystem paths.
                val marker = File(root, ".complete")
                synchronized(modelCopyLock) {
                    if (!marker.exists()) {
                        root.mkdirs(); copyAssets("voice", root); marker.writeText("1")
                    }
                }
                val model = root.listFiles()?.singleOrNull { it.extension == "onnx" }
                    ?: error("Brak lub niejednoznaczny model głosu")
                engine = OfflineTts(config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = File(root, "tokens.txt").absolutePath,
                        dataDir = File(root, "espeak-ng-data").absolutePath
                    ), numThreads = 2), maxNumSentences = 1
                ))
                check(engine!!.sampleRate() > 0) { "Nie można załadować głosu" }
                ui { ready = true; status.text = getString(R.string.ready); updateButtons() }
            } catch (e: Throwable) {
                android.util.Log.e("OpenSpeech", "Voice initialization", e)
                ui { status.text = getString(R.string.init_error) }
            }
        }
    }
    private fun copyAssets(path: String, dest: File) {
        val children = assets.list(path) ?: emptyArray()
        if (children.isNotEmpty()) {
            dest.mkdirs(); children.forEach { copyAssets("$path/$it", File(dest, it)) }
        } else {
            assets.open(path).use { src -> dest.outputStream().use { src.copyTo(it) } }
        }
    }
    private fun ui(block: () -> Unit) { runOnUiThread { if (!dead) block() } }
    private fun hasCurrentAudio(): Boolean = audio?.exists() == true &&
        audioText == input.text.toString().trim() && audioSpeed == speed

    private fun updateButtons() {
        if (!::export.isInitialized) return
        val hasText = input.text.isNotBlank()
        speak.isEnabled = ready && !busy && hasText
        share.isEnabled = ready && !busy && hasText
        stop.isEnabled = busy && !cancelled.get()
        export.isEnabled = hasCurrentAudio() && !busy
        addPhrase.isEnabled = !busy && hasText && phrases.size < 16
        phraseButtons.forEach { it.isEnabled = ready && !busy }
    }
    private fun readText(shareAfter: Boolean = false) {
        if (busy || !ready) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        val cached = hasCurrentAudio()
        val cachedWav = if (cached) audio else null
        val cachedSamples = if (cached) audioSamples else null
        val cachedRate = audioRate
        val cachedAttachment = if (cached) attachment?.takeIf { it.exists() } else null
        busy = true; cancelled.set(false)
        val ticket = ++generation
        val requestedSpeed = speed
        progress.visibility = View.VISIBLE
        status.text = getString(if (shareAfter) R.string.preparing_share else R.string.preparing)
        updateButtons()
        if (cachedWav != null && !shareAfter) { play(cachedWav, ticket); return }
        worker.execute {
            var newWav: File? = null
            var newAttachment: File? = null
            try {
                var samples = cachedSamples
                var rate = cachedRate
                var wav = cachedWav
                if (samples == null || wav == null) {
                    val generated = engine!!.generateWithCallback(
                        text = text, speed = requestedSpeed, callback = SynthesisCallback(cancelled)
                    )
                    if (cancelled.get()) throw java.util.concurrent.CancellationException()
                    check(generated.samples.isNotEmpty()) { "Puste nagranie" }
                    SpeechLoudness.normalize(generated.samples)
                    samples = generated.samples; rate = generated.sampleRate
                    wav = File.createTempFile("speech-", ".wav", cacheDir)
                    newWav = wav; writeWav(wav, samples, rate)
                }
                val completeSamples = requireNotNull(samples)
                val completeWav = requireNotNull(wav)
                val completeRate = rate
                var shared = cachedAttachment
                if (shareAfter && shared == null) {
                    val folder = File(cacheDir, "shared_audio").apply { mkdirs() }
                    shared = File.createTempFile("OpenSpeech-", ".m4a", folder)
                    newAttachment = shared
                    AudioShareEncoder.encode(completeSamples, rate, shared) { cancelled.get() || dead }
                }
                if (cancelled.get() || dead) throw java.util.concurrent.CancellationException()
                val shareFile = shared
                runOnUiThread {
                    if (dead || ticket != generation) {
                        newWav?.delete(); newAttachment?.delete(); return@runOnUiThread
                    }
                    if (audio != completeWav) audio?.delete()
                    audio = completeWav; audioSamples = completeSamples; audioRate = completeRate
                    audioText = text; audioSpeed = requestedSpeed; attachment = shareFile
                    if (shareAfter && shareFile != null) {
                        finishReading(getString(R.string.choose_app)); shareAudio(shareFile)
                    } else play(completeWav, ticket)
                }
            } catch (e: Throwable) {
                newWav?.delete(); newAttachment?.delete()
                if (e !is java.util.concurrent.CancellationException) android.util.Log.e("OpenSpeech", "Speech preparation", e)
                ui {
                    if (ticket == generation) finishReading(getString(
                        if (cancelled.get()) R.string.stopped else if (shareAfter) R.string.share_error else R.string.speech_error
                    ))
                }
            }
        }
    }
    private fun shareAudio(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.audio", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("OpenSpeech", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_title)))
        } catch (e: Exception) {
            android.util.Log.e("OpenSpeech", "Share chooser", e)
            status.text = getString(R.string.share_error)
        }
    }
    private fun play(wav: File, ticket: Int) {
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            finishReading("Nie można teraz odtworzyć dźwięku. Nagranie możesz zapisać."); return
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setVolume(1f, 1f)
                setDataSource(wav.absolutePath)
                setOnPreparedListener {
                    if (ticket == generation && !dead) {
                        progress.visibility = View.GONE; status.text = getString(R.string.reading); it.start()
                    }
                }
                setOnCompletionListener { finishReading(getString(R.string.finished)) }
                setOnErrorListener { _, _, _ -> finishReading("Błąd odtwarzania. Spróbuj ponownie."); true }
                prepareAsync()
            }
        } catch (e: Exception) { finishReading("Nie można odtworzyć nagrania.") }
    }
    private fun stopReading() {
        if (!busy) return
        cancelled.set(true)
        // The callback cancels native synthesis. Keep buttons locked until it returns.
        if (player != null) {
            ++generation; finishReading("Zatrzymano. Nagranie możesz zapisać.")
        } else {
            status.text = getString(R.string.stopping); stop.isEnabled = false
        }
    }
    private fun finishReading(message: String) {
        player?.release(); player = null
        audioManager.abandonAudioFocusRequest(focus)
        busy = false; progress.visibility = View.GONE; status.text = message; updateButtons()
    }
    private fun writeWav(file: File, samples: FloatArray, rate: Int) {
        val size = samples.size * 2
        FileOutputStream(file).use { out ->
            val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            head.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
            head.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
            head.putShort(2).putShort(16).put("data".toByteArray()).putInt(size)
            out.write(head.array())
            val buf = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach {
                if (buf.remaining() < 2) { out.write(buf.array(), 0, buf.position()); buf.clear() }
                buf.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            }
            out.write(buf.array(), 0, buf.position())
        }
    }
    @Deprecated("Legacy Activity result API keeps this app dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100) return
        val source = pendingExport ?: return
        pendingExport = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) { source.delete(); return }
        worker.execute {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: error("Brak dostępu do pliku")
                ui { if (!busy) status.text = getString(R.string.saved) }
            } catch (e: Exception) { ui { if (!busy) status.text = getString(R.string.save_error) } }
            finally { source.delete() }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("text", input.text.toString())
        pendingExport?.let { outState.putString("pendingExport", it.absolutePath) }
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        getPreferences(MODE_PRIVATE).edit().putString("text", input.text.toString()).putFloat("speed", speed).apply()
        stopReading(); super.onPause()
    }
    override fun onDestroy() {
        dead = true; cancelled.set(true); ++generation
        player?.release(); player = null
        audioManager.abandonAudioFocusRequest(focus)
        worker.execute { engine?.release(); engine = null; audio?.delete() }
        worker.shutdown()
        super.onDestroy()
    }
}
