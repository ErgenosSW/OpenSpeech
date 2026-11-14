// SPDX-License-Identifier: GPL-3.0-only
package pl.ergenos.glosfacet;

/** Static speech gain with a soft peak limiter; never changes pitch or timing. */
public final class SpeechLoudness {
    private SpeechLoudness() {}

    public static void normalize(float[] samples) {
        double peak = 0;
        for (float sample : samples) {
            if (Float.isFinite(sample)) peak = Math.max(peak, Math.abs(sample));
        }
        if (peak < 0.0001) return; // Leave silence and near-silence alone.
        double threshold = peak * 0.02;
        double energy = 0;
        int count = 0;
        for (float sample : samples) {
            if (Float.isFinite(sample) && Math.abs(sample) >= threshold) {
                energy += (double) sample * sample;
                count++;
            }
        }
        if (count == 0) return;
        double gain = Math.max(1.0, Math.min(12.0, 0.24 / Math.sqrt(energy / count)));
        for (int i = 0; i < samples.length; i++) {
            double value = Float.isFinite(samples[i]) ? samples[i] * gain : 0;
            double magnitude = Math.abs(value);
            if (magnitude > 0.8) {
                magnitude = 0.8 + 0.15 * Math.tanh((magnitude - 0.8) / 0.15);
            }
            samples[i] = (float) Math.copySign(magnitude, value);
        }
    }
}
