"""
Text front end of Matcha-TTS-PL-Edge: Polish text (+ English words) -> phoneme ids for the encoder.
  text_to_ids("Po meetingu wyślę ci feedback.")  ->  [0, 46, 0, ...]   (interspersed with 0, as the model was trained)
English words are found by english_detect.mark_english (curated list + rules; optional wordfreq fallback) and written in braces
("Po {meeting}u wyślę ci {feedback}."); you can also brace words yourself. Braced spans are phonemized by espeak-ng en-us, the
rest by espeak-ng pl (with the Polish ending of an inflected English word glued on, unstressed).
Needs: phonemizer + libespeak-ng (macOS: brew install espeak-ng; Debian/Ubuntu: apt install espeak-ng). If phonemizer can't find
the library, set PHONEMIZER_ESPEAK_LIBRARY (e.g. /usr/lib/aarch64-linux-gnu/libespeak-ng.so.1).
Copied from the Matcha-TTS fork used for training (matcha/text/cleaners.py "polish_mixed_cleaners" + symbols.py), so ids match.
"""
import re
from phonemizer.backend import EspeakBackend

_pl_backend = EspeakBackend(
    language="pl",
    preserve_punctuation=True,
    with_stress=True,
    words_mismatch="ignore",
    language_switch="remove-flags",
)

_PL_WORD = "0-9a-ząćęłńóśźż"


def _abbr(k: str, v: str):
    """Regex dla skrótu: musi stać na granicy słowa. 'r.'/'w.' tylko po liczbie/liczbie rzymskiej
    (inaczej 'Piotr.' -> 'Piotroku')."""
    if k in ("r.", "w."):
        return re.compile(rf"(?<=[0-9ivx])\s?{re.escape(k)}"), f" {v}"
    tail = "" if k.endswith(".") else rf"(?![{_PL_WORD}])"
    return re.compile(rf"(?<![{_PL_WORD}]){re.escape(k)}{tail}"), v


_PL_ABBREV = [
    _abbr(k, v)
    for k, v in [
        ("np.", "na przykład"),
        ("tzn.", "to znaczy"),
        ("tj.", "to jest"),
        ("itd.", "i tak dalej"),
        ("itp.", "i tym podobne"),
        ("m.in.", "między innymi"),
        ("dr", "doktor"),
        ("prof.", "profesor"),
        ("inż.", "inżynier"),
        ("mgr", "magister"),
        ("ul.", "ulica"),
        ("al.", "aleja"),
        ("r.", "roku"),
        ("w.", "wieku"),
        ("godz.", "godzina"),
        ("min.", "minut"),
        ("św.", "święty"),
        ("nr", "numer"),
        ("tel.", "telefon"),
        ("zł", "złotych"),
    ]
]


def _expand_pl_abbreviations(text: str) -> str:
    for rx, rep in _PL_ABBREV:
        text = rx.sub(rep, text)
    return text


def _collapse_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _normalize_punct(text: str) -> str:
    text = re.sub(r"\s*(?:—|–| - )\s*", ", ", text).replace("…", "...")
    text = text.replace("„", "").replace("”", "").replace("“", "").replace('"', "").replace("«", "").replace("»", "")
    return text


def polish_cleaners(text: str) -> str:
    """Tekst PL -> IPA (espeak-ng). Liczby zostaw espeakowi – czyta je poprawnie po polsku
    w mianowniku; jeśli potrzebujesz odmiany, zamień je wcześniej w prepare_dataset."""
    text = text.lower()
    text = _normalize_punct(text)
    text = _expand_pl_abbreviations(text)
    phonemes = _pl_backend.phonemize([text], strip=True, njobs=1)[0]
    return _collapse_whitespace(phonemes)


# --- tts-pl: Polish text with English words (appended by patch_matcha.py after polish_cleaners) ---------------------
# English words are marked in braces: "Dzięki za {feedback}, wrzucę to na {Slack}a." The braced part is phonemized by
# espeak-ng en-us, the rest by the Polish pipeline above; a Polish ending glued to a brace ("{Slack}a") is joined to the
# English phonemes without a space. All en-us phones are in the symbol table (checked: voxcpm/pilot_phonemes.py).
_en_backend = EspeakBackend(
    language="en-us",
    preserve_punctuation=True,
    with_stress=True,
    words_mismatch="ignore",
    language_switch="remove-flags",
)
_EN_SPAN = re.compile(r"\{([^{}]+)\}")


def _pl_phon(text: str) -> str:
    text = _expand_pl_abbreviations(_normalize_punct(text.lower()))
    return _pl_backend.phonemize([text], strip=True, njobs=1)[0] if text.strip() else ""


_VOICELESS = ("p", "t", "k", "f", "s", "ʃ", "θ", "h", "tʃ", "ˈp", "ˈt", "ˈk", "ˈf", "ˈs", "ˈʃ", "ˈθ", "ˈh", "ˌp", "ˌt", "ˌk", "ˌf", "ˌs", "ˌh")


def _one_letter(w: str, next_ph: str) -> str:
    voiceless = next_ph.startswith(_VOICELESS)
    return {"w": "f" if voiceless else "v", "z": "s" if voiceless else "z", "i": "i", "a": "a", "o": "ɔ", "u": "u"}[w]


def polish_mixed_cleaners(text: str) -> str:
    """Polish IPA with English IPA for {braced} spans. Without braces identical to polish_cleaners."""
    if "{" not in text:
        return polish_cleaners(text)
    out, pos = "", 0
    for m in _EN_SPAN.finditer(text):
        pl = text[pos:m.start()]
        prep = ""
        mm = re.search(r"(?:^|\s)([wzaiouWZAIOU])\s*$", pl)          # a one-letter word right before the English span: espeak
        if mm:                                                 # would read it alone as a letter name ("w" -> "vɨ")
            prep, pl = mm.group(1).lower(), pl[:mm.start(1)]
        if pl.strip():
            glue = not pl[0].isspace() and out != ""            # Polish ending right after the previous brace
            ph = _pl_phon(pl)
            if glue:                                             # the ending is unstressed: part of the English word
                head, _, rest = ph.partition(" ")
                ph = head.replace("ˈ", "").replace("ˌ", "") + (" " + rest if rest else "")
            out += ("" if glue else " ") + ph
        en = _en_backend.phonemize([m.group(1)], strip=True, njobs=1)[0]
        if prep:                                                 # "w {weekend}": the preposition, voiced or not by the next sound
            out += " " + _one_letter(prep, en)
        glue = m.start() > 0 and not text[m.start() - 1].isspace() and out != ""
        out += ("" if glue else " ") + en
        pos = m.end()
    tail = text[pos:]
    if tail.strip():
        glue = not tail[0].isspace()
        ph = _pl_phon(tail)
        if glue:
            head, _, rest = ph.partition(" ")
            ph = head.replace("ˈ", "").replace("ˌ", "") + (" " + rest if rest else "")
        out += ("" if glue else " ") + ph
    return _collapse_whitespace(out)


# ---- symbols (matcha/text/symbols.py of the training fork) ----
_pad = "_"
_punctuation = ';:,.!?¡¿—…"«»“” '
_letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
_letters_ipa = (
    "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ"
)


_letters_ipa += "\u0303"  # tts-pl: Polish nasal vowels (COMBINING TILDE)

# Export all symbols:
symbols = [_pad] + list(_punctuation) + list(_letters) + list(_letters_ipa)
_symbol_to_id = {s: i for i, s in enumerate(symbols)}


def text_to_ids(text, english=True, tagger=None):
    """text -> interspersed phoneme ids. english=False reads every word as Polish; tagger: an optional contextual English
    tagger with .mark(text) (frontend/tagger), used instead of the rules"""
    if english:
        if tagger is not None:
            text = tagger.mark(text)
        else:
            from english_detect import mark_english
            text = mark_english(text)
    else:
        text = text.replace("{", "").replace("}", "")
    text = re.sub(r"\s+\?", "?", text)          # "?" glued to the word, as in training (EXPERIMENTS.md E43)
    ids = [_symbol_to_id[c] for c in polish_mixed_cleaners(text) if c in _symbol_to_id]
    out = [0] * (2 * len(ids) + 1); out[1::2] = ids
    return out
