// SPDX-License-Identifier: GPL-3.0-only
import pl.ergenos.glosfacet.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CheckOfflineVoice {
    private static void wav(Path path, float[] samples, int rate) throws Exception {
        try (OutputStream out=Files.newOutputStream(path)) {
            ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes()).putInt(36+samples.length*2).put("WAVEfmt ".getBytes());
            header.putInt(16).putShort((short)1).putShort((short)1).putInt(rate).putInt(rate*2);
            header.putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(samples.length*2);
            out.write(header.array());
            ByteBuffer pcm=ByteBuffer.allocate(samples.length*2).order(ByteOrder.LITTLE_ENDIAN);
            for(float sample:samples) { if(!Float.isFinite(sample))throw new AssertionError("Non-finite audio"); pcm.putShort((short)(Math.max(-1,Math.min(1,sample))*32767)); }
            out.write(pcm.array());
        }
    }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]);Path golden=Path.of(args[1]),output=Path.of(args[2]);Files.createDirectories(output);
        PolishPhonemizer frontend=new PolishPhonemizer(new File(root,"espeak-ng-data"));
        List<String> texts=new ArrayList<>();int count=0;
        for(String line:Files.readAllLines(golden)) {
            String[] parts=line.split("\t");String text=new String(Base64.getDecoder().decode(parts[0]),java.nio.charset.StandardCharsets.UTF_8);
            long[] expected=Arrays.stream(parts[1].split(",")).mapToLong(Long::parseLong).toArray();
            long[] actual=frontend.textToIds(text);
            if(!Arrays.equals(expected,actual))throw new AssertionError("Frontend differs from reference: "+text+"\nExpected "+Arrays.toString(expected)+"\nActual "+Arrays.toString(actual));
            texts.add(text);count++;
        }
        System.out.println("Frontend parity: "+count+" cases passed");
        OfflineVoice voice=new OfflineVoice(root);
        try {
            for(int i=0;i<Math.min(7,texts.size());i++) {
                long start=System.nanoTime();OfflineVoice.Audio audio=voice.generate(texts.get(i),1f,new AtomicBoolean());
                if(audio.getSampleRate()!=22050 || audio.getSamples().length==0)throw new AssertionError("Invalid audio");
                Path path=output.resolve(String.format("%02d.wav",i+1));wav(path,audio.getSamples(),audio.getSampleRate());
                System.out.printf(Locale.ROOT,"sample=%02d synthesis_seconds=%.4f audio_seconds=%.4f%n",i+1,(System.nanoTime()-start)/1e9,audio.getSamples().length/22050.0);
            }
            float slow=voice.generate(texts.get(0),0.75f,new AtomicBoolean()).getSamples().length;
            float fast=voice.generate(texts.get(0),1.45f,new AtomicBoolean()).getSamples().length;
            if(slow<=fast*1.5)throw new AssertionError("Tempo does not affect duration");
            AtomicBoolean cancelled=new AtomicBoolean(true);
            try { voice.generate("Dzień dobry.",1f,cancelled);throw new AssertionError("Cancellation ignored"); }
            catch(java.util.concurrent.CancellationException expected) { }
            System.out.println("Tempo and cancellation passed");
        } finally { voice.release(); }
    }
}
