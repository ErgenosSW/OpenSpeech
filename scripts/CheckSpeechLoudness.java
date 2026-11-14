// SPDX-License-Identifier: GPL-3.0-only
import pl.ergenos.glosfacet.SpeechLoudness;
import javax.sound.sampled.*;
import java.io.*;

public class CheckSpeechLoudness {
    static double rms(float[] x) {
        double e = 0;
        for (float s : x) e += (double) s * s;
        return Math.sqrt(e / Math.max(1, x.length));
    }
    static double peak(float[] x) {
        double peak = 0;
        for (float s : x) {
            if (!Float.isFinite(s) || Math.abs(s) > 0.950001) throw new AssertionError("Invalid/overloaded sample");
            peak = Math.max(peak, Math.abs(s));
        }
        return peak;
    }
    public static void main(String[] args) throws Exception {
        float[] silence = new float[1000];
        SpeechLoudness.normalize(silence);
        if (rms(silence) != 0) throw new AssertionError("Silence changed");
        float[] quiet = new float[22050];
        for (int i = 0; i < quiet.length; i++) quiet[i] = (float)(0.01 * Math.sin(i * 0.1));
        double before = rms(quiet);
        SpeechLoudness.normalize(quiet);
        if (rms(quiet) < before * 10) throw new AssertionError("Quiet speech not boosted");
        peak(quiet);
        float[] extremes = {0f, 1f, -1f, 5f, -5f};
        SpeechLoudness.normalize(extremes);
        peak(extremes);
        if (extremes[1] != -extremes[2]) throw new AssertionError("Asymmetric limiter");
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(args[0]))) {
            AudioFormat format = in.getFormat();
            if (format.getChannels() != 1 || format.getSampleSizeInBits() != 16 || format.isBigEndian()) throw new AssertionError("Expected PCM16 LE mono");
            byte[] bytes = in.readAllBytes();
            float[] samples = new float[bytes.length / 2];
            for (int i = 0; i < samples.length; i++) samples[i] = (short)((bytes[i*2] & 255) | bytes[i*2+1] << 8) / 32768f;
            before = rms(samples);
            SpeechLoudness.normalize(samples);
            double after = rms(samples);
            double peak = peak(samples);
            if (after <= before) throw new AssertionError("Real speech not louder");
            for (int i = 0; i < samples.length; i++) {
                int pcm = Math.round(samples[i] * 32767);
                bytes[i*2] = (byte)pcm; bytes[i*2+1] = (byte)(pcm >> 8);
            }
            try (var stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, samples.length)) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, new File(args[1]));
            }
            System.out.printf("PASS: silence, quiet speech, bounded symmetric peaks; real speech RMS %.4f -> %.4f (+%.2f dB), peak %.4f%n", before, after, 20*Math.log10(after/before), peak);
        }
    }
}
