#!/usr/bin/env python3
"""
Matcha-TTS-PL-Edge: Polish text-to-speech on a CPU (onnxruntime + numpy), whole sentences or streamed.

  python tts.py "Dzień dobry! W czym mogę pomóc?" -o out.wav                      # int8, 2 ODE steps, streamed
  python tts.py "…" --precision fp32 --steps 4 --no-stream -o out.wav             # best quality, whole sentences
  python tts.py "…" --play                                                         # play while it is generated (pip install sounddevice)

  from tts import EdgeTTS
  tts = EdgeTTS(voice="male_voxcpm_2", precision="int8")
  for audio in tts.stream("Dzień dobry! Mam dla ciebie kilka informacji."):   # float32 pieces at 22 050 Hz
      play(audio)
  wav = tts.synthesize("Dzień dobry!")                                            # the whole utterance at once

Pipeline per sentence: text encoder + duration predictor on the whole sentence -> flow-matching decoder (2 or 4 ODE steps) ->
HiFi-GAN V2. Streaming runs the decoder on chunks of `chunk` mel frames (11.6 ms each) with `ode_ctx` frames of context on each
side, and the vocoder on the mel as it arrives, `voc_ctx` frames behind (12 = bit-exact); text is split into sentences (merged up
to `min_sentence_chars`) and the next sentence is prepared while the previous one plays. Defaults are in each voice's
voices.json ("stream"): chunk 43, ode_ctx 16 (suggested 8–32), voc_ctx 12 (suggested 8–12), min_sentence_chars 40.
"""
import argparse
import json
import os
import re
import sys
import time
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "frontend"))
SR, HOP = 22050, 256


def split_sentences(text, min_chars=40):
    """sentences; one shorter than min_chars is merged with the next (very short inputs synthesise less stably)"""
    parts = [x.strip() for x in re.findall(r"[^.!?…]+[.!?…]+[”\"»)]*|[^.!?…]+$", text) if x.strip()]
    out = []
    for x in parts:
        if out and len(out[-1]) < min_chars:
            out[-1] += " " + x
        else:
            out.append(x)
    if len(out) > 1 and len(out[-1]) < min_chars:
        last = out.pop(); out[-1] += " " + last
    return out or [text]


class EdgeTTS:
    def __init__(self, voice="male_voxcpm_2", precision="int8", steps=2, threads=None, vocoder=None, root=HERE, tagger=None, durations=None):
        import onnxruntime as ort
        self.dir = Path(root) / voice; self.vj = json.loads((self.dir / "voices.json").read_text(encoding="utf-8"))
        g = self.vj["graphs"][precision]
        so = ort.SessionOptions(); so.inter_op_num_threads = 1
        if threads:
            so.intra_op_num_threads = int(threads)
        mk = lambda f: ort.InferenceSession(str(self.dir / f), so, providers=["CPUExecutionProvider"])   # noqa: E731
        # durations: "stochastic" = the flow-matching duration predictor baked into encoder_sdp (voices that ship one, E64 / E79-v4)
        dj = self.vj.get("durations", {}); self.durations = durations or dj.get("default", "deterministic")
        self.sdp = self.durations == "stochastic" and "encoder_sdp" in g
        self.dur_temperature = float(dj.get("sdp", {}).get("dur_temperature", 0.667))
        self.enc, self.dec = mk(g["encoder_sdp" if self.sdp else "encoder"]), mk(g["decoder"][str(steps)])
        self.voc = mk(self.vj["graphs"]["vocoder"][vocoder or g["vocoder"]])
        self.defaults = {**self.vj["defaults"], **self.vj["stream"]}
        self.tagger = tagger

    def speaker(self, style=None):
        s = self.defaults["style"] if style is None else style
        return (np.array(self.vj["speakers"]["0"]["emb"]) + np.array(self.vj["styles"][str(s)]["emb"])).astype(np.float32)[None]

    def stream(self, text, stream=True, chunk=None, ode_ctx=None, voc_ctx=None, first=None, min_sentence_chars=None, gap_s=0.2,
               temperature=None, tempo=None, style=None, seed=None, english=True, split=True, dur_temperature=None):
        """yields float32 audio pieces; stream=False: one piece per sentence (whole-sentence decoding)"""
        from text import text_to_ids
        d = self.defaults; chunk = chunk or d["chunk"]; ode_ctx = d["ode_ctx"] if ode_ctx is None else ode_ctx
        voc_ctx = d["voc_ctx"] if voc_ctx is None else voc_ctx; first = first or chunk
        temperature = d["temperature"] if temperature is None else temperature; tempo = d["length_scale"] if tempo is None else tempo
        spk = self.speaker(style); rng = np.random.default_rng(seed)
        sents = split_sentences(text, d["min_sentence_chars"] if min_sentence_chars is None else min_sentence_chars) if split else [text]
        for si, sentence in enumerate(sents):
            ids = text_to_ids(sentence, english=english, tagger=self.tagger)
            feed = {"x": np.array([ids], np.int64), "x_lengths": np.array([len(ids)], np.int64), "spk_emb": spk,
                    "scales": np.array([temperature, tempo], np.float32)}
            if self.sdp:   # duration noise for the flow-matching duration predictor, from the same seeded generator
                feed["scales"] = np.array([temperature, tempo, self.dur_temperature if dur_temperature is None else dur_temperature], np.float32)
                feed["z_dur"] = rng.standard_normal((1, 1, len(ids))).astype(np.float32)
            mu, ml = self.enc.run(None, feed)
            T = int(ml[0]); mu = mu[..., :T]; z = rng.standard_normal((1, 80, T)).astype(np.float32)
            gap = np.zeros(int(gap_s * SR), np.float32) if si < len(sents) - 1 else np.zeros(0, np.float32)
            if not stream:
                yield np.concatenate([self._vocode(self._decode(mu, z, 0, T, 0, spk, temperature, T), 0, T, T, 0), gap]); continue
            mel = np.zeros((1, 80, T), np.float32); bounds = [0]
            while bounds[-1] < T:
                bounds.append(min(T, bounds[-1] + (first if len(bounds) == 1 else chunk)))
            p = 0
            for s, e in zip(bounds[:-1], bounds[1:]):
                mel[..., s:e] = self._decode(mu, z, s, e, ode_ctx, spk, temperature, T)
                q = T if e >= T else e - voc_ctx                    # the vocoder runs voc_ctx frames behind the decoder
                if q > p:
                    a = self._vocode(mel, p, q, T, voc_ctx); p = q
                    yield np.concatenate([a, gap]) if q >= T else a

    def warmup(self):
        """load the phonemizer / word lists and run every graph once (the first call is slow); a robot does this at start-up"""
        for _ in self.stream("Dzień dobry, jak się masz?", seed=0):
            pass

    def synthesize(self, text, **kw):
        return np.concatenate(list(self.stream(text, **kw)))

    def _decode(self, mu, z, s, e, ctx, spk, temperature, T):
        a, b = max(0, s - ctx), min(T, e + ctx); L = (b - a + 3) // 4 * 4
        mu_c = np.zeros((1, 80, L), np.float32); z_c = np.zeros((1, 80, L), np.float32); m_c = np.zeros((1, 1, L), np.float32)
        mu_c[..., :b - a] = mu[..., a:b]; z_c[..., :b - a] = z[..., a:b]; m_c[..., :b - a] = 1
        return self.dec.run(None, {"mu": mu_c, "mask": m_c, "z": z_c, "spk_emb": spk, "temperature": np.array(temperature, np.float32)})[0][..., s - a:e - a]

    def _vocode(self, mel, p, q, T, ctx):
        a, b = max(0, p - ctx), min(T, q + ctx)
        w = self.voc.run(None, {"mel": np.ascontiguousarray(mel[..., a:b])})[0].reshape(-1)[(p - a) * HOP:(q - a) * HOP]
        return np.clip(w, -1, 1).astype(np.float32)


def write_wav(path, audio):
    with wave.open(str(path), "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes((np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes())


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("text"); ap.add_argument("-o", "--out", default="out.wav"); ap.add_argument("--voice", default="male_voxcpm_2")
    ap.add_argument("--precision", choices=["int8", "fp32"], default="int8"); ap.add_argument("--steps", type=int, choices=[2, 4], default=2)
    ap.add_argument("--threads", type=int); ap.add_argument("--no-stream", action="store_true", help="decode whole sentences")
    ap.add_argument("--chunk", type=int, help="mel frames per chunk (default 43 = 0.5 s)"); ap.add_argument("--ode-ctx", type=int, help="decoder context frames per side (16; 8–32)")
    ap.add_argument("--voc-ctx", type=int, help="vocoder context frames (12 = exact; 8–12)"); ap.add_argument("--first-chunk", type=int)
    ap.add_argument("--min-sentence-chars", type=int, help="merge shorter sentences with the next (40)"); ap.add_argument("--no-split", action="store_true")
    ap.add_argument("--temperature", type=float); ap.add_argument("--tempo", type=float, help="length scale, lower = faster (0.8)")
    ap.add_argument("--style", type=int, help="style token (8 = neutral; see voices.json)"); ap.add_argument("--seed", type=int)
    ap.add_argument("--no-english", action="store_true", help="read English words as Polish"); ap.add_argument("--play", action="store_true")
    ap.add_argument("--durations", choices=["deterministic", "stochastic"], help="default: voices.json (stochastic where the voice ships encoder_sdp)")
    ap.add_argument("--dur-temperature", type=float, help="spread of the sampled durations (0.667)")
    a = ap.parse_args()
    os.environ.setdefault("PHONEMIZER_ESPEAK_LIBRARY", "/opt/homebrew/lib/libespeak-ng.dylib" if sys.platform == "darwin" else "")
    if not os.environ["PHONEMIZER_ESPEAK_LIBRARY"]:
        del os.environ["PHONEMIZER_ESPEAK_LIBRARY"]
    t_load = time.perf_counter(); tts = EdgeTTS(a.voice, a.precision, a.steps, a.threads, durations=a.durations); tts.warmup(); t_load = time.perf_counter() - t_load
    player = None
    if a.play:
        import sounddevice as sd
        player = sd.OutputStream(samplerate=SR, channels=1, dtype="float32"); player.start()
    t0 = time.perf_counter(); first, pieces = None, []
    for piece in tts.stream(a.text, stream=not a.no_stream, chunk=a.chunk, ode_ctx=a.ode_ctx, voc_ctx=a.voc_ctx, first=a.first_chunk,
                            min_sentence_chars=a.min_sentence_chars, temperature=a.temperature, tempo=a.tempo, style=a.style, seed=a.seed, dur_temperature=a.dur_temperature,
                            english=not a.no_english, split=not a.no_split):
        if first is None:
            first = time.perf_counter() - t0
        pieces.append(piece)
        if player:
            player.write(piece[:, None])
    total = time.perf_counter() - t0; audio = np.concatenate(pieces); write_wav(a.out, audio)
    print(f"{a.out}: {len(audio) / SR:.2f} s audio · first audio after {1000 * first:.0f} ms · compute {1000 * total:.0f} ms (RTF {total / (len(audio) / SR):.3f})"
          f" · start-up {t_load:.1f} s (once)")
    if player:
        player.stop()


if __name__ == "__main__":
    main()
