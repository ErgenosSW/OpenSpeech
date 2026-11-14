// SPDX-License-Identifier: GPL-3.0-only
package pl.ergenos.glosfacet;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Encode the same normalized PCM used for playback into a small AAC/M4A attachment. */
public final class AudioShareEncoder {
    private AudioShareEncoder() {}
    public static void encode(float[] samples, int rate, File target, BooleanSupplier cancelled) throws IOException {
        if (samples.length == 0 || rate <= 0) throw new IllegalArgumentException("Brak dźwięku");
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        boolean codecStarted = false, muxerStarted = false, complete = false;
        try {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start(); codecStarted = true;
            muxer = new MediaMuxer(target.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int offset = 0, track = -1;
            long writtenBytes = 0;
            boolean inputEnded = false;
            long lastProgress = System.nanoTime();
            while (!complete) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                if (!inputEnded) {
                    int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer input = codec.getInputBuffer(index);
                        if (input == null) throw new IOException("Brak bufora kodera");
                        input.clear(); input.order(ByteOrder.LITTLE_ENDIAN);
                        int count = Math.min(input.remaining() / 2, samples.length - offset);
                        long timeUs = offset * 1000000L / rate;
                        for (int n = 0; n < count; n++) {
                            float value = Math.max(-1f, Math.min(1f, samples[offset + n]));
                            input.putShort((short) (value * 32767));
                        }
                        int flags = count == 0 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
                        codec.queueInputBuffer(index, 0, count * 2, timeUs, flags);
                        offset += count; inputEnded = count == 0;
                        lastProgress = System.nanoTime();
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw new IOException("Powtórna zmiana formatu kodera");
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start(); muxerStarted = true; lastProgress = System.nanoTime();
                } else if (index >= 0) {
                    try {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                        if (info.size > 0) {
                            if (!muxerStarted) throw new IOException("Brak formatu dźwięku");
                            ByteBuffer output = codec.getOutputBuffer(index);
                            if (output == null) throw new IOException("Brak danych kodera");
                            output.position(info.offset); output.limit(info.offset + info.size);
                            muxer.writeSampleData(track, output, info);
                            writtenBytes += info.size;
                        }
                        complete = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        lastProgress = System.nanoTime();
                    } finally { codec.releaseOutputBuffer(index, false); }
                }
                if (System.nanoTime() - lastProgress > 30000000000L) throw new IOException("Koder nie odpowiada");
            }
            if (!muxerStarted || writtenBytes == 0) throw new IOException("Nie wygenerowano nagrania");
            // Stop here so a muxer failure is reported rather than publishing a partial attachment.
            muxer.stop(); muxerStarted = false;
        } catch (RuntimeException | IOException e) {
            complete = false;
            throw e;
        } finally {
            if (codec != null) {
                if (codecStarted) try { codec.stop(); } catch (RuntimeException ignored) {}
                codec.release();
            }
            if (muxer != null) {
                if (muxerStarted) try { muxer.stop(); } catch (RuntimeException ignored) {}
                muxer.release();
            }
            if (!complete) target.delete();
        }
    }
}
