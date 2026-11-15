#!/usr/bin/env python3
"""
Find English words in ordinary Polish text and wrap them in braces for polish_mixed_cleaners (run time, ~µs per word).

Curated multi-word entries ("flash sale", "pull request") are matched as whole phrases first (an ending may follow:
pull requesta -> {pull request}a), unless every word of the phrase is also a Polish word ("to go", "no problem").
Words that are both Polish and English cannot be told apart without understanding the sentence ("kawa to go"), so the
convention is: a word that is a Polish word is read as Polish unless it is listed on its own line in english_words.txt
or written in braces. "Polish word" = a common word in the SGJP dictionary (Morfeusz 2; names and unknown words do not
count), precomputed for the words of the phrases in english_phrase_words.txt (--rebuild-phrase-words).
A word (or a Polish-inflected form of it: meetingu -> meeting + u) is English when
  - it is a single-word entry of the curated list (english_words.txt: the English words of the training set, brands,
    common loanwords), or a word of a curated phrase that is not a Polish word (pull, request; not sale from flash sale), or
  - it is common in English and clearly rarer in Polish (wordfreq Zipf: en >= 3, en - pl >= MARGIN) and not a common
    Polish word (pl < 4.5: protects "to", "dom", "pies"…; and not in SGJP when Morfeusz 2 is installed), and it has
    no Polish diacritics;
and it is not on the read-as-Polish list (laptop, retro, film…). Explicit braces in the input are kept as they are.

  from english_detect import mark_english;  mark_english("Po meetingu wyślę ci feedback.")  ->  "Po {meeting}u wyślę ci {feedback}."
  python3 voxcpm/english_detect.py --eval <exported set>/metadata.csv
"""
import re
from functools import lru_cache
from pathlib import Path

HERE = Path(__file__).resolve().parent
MARGIN, EN_MIN, PL_MAX = 1.5, 3.5, 3.5
PL_INFLECTED_MAX = 3.0   # an inflected form at least this common in Polish is a Polish word
SUFFIXES = sorted(["ami", "ach", "owi", "iem", "zie", "em", "om", "ów", "ie", "u", "a", "y", "i", "e", "ę", "ze", "owy", "owa", "owe"], key=len, reverse=True)
READ_AS_POLISH = {"retro", "webinar", "duolingo", "lobby", "laptop", "blog", "budżet", "zoom", "canva", "figma", "film", "plan", "super", "stop",
                  "sport", "hotel", "tenis", "golf", "mecz", "lider", "trener", "komputer", "internet", "radio", "taxi", "menu", "pizza",
                  "smartfon", "tablet", "serial", "lunchbox", "jazz", "rock", "sms", "ok", "demo", "sushi", "kebab", "grill", "telegram",
                  "webinaru", "webinarze", "laptopa", "laptopie", "laptopem", "bloga", "figmie"} | {"art", "bank", "data", "internet", "list", "media", "metal", "model", "partner", "pizza", "plan", "premier", "pro", "problem", "star", "super", "talent", "test", "tour", "video", "zero"}
PHRASE_WORDS_FILE = HERE / "english_phrase_words.txt"
# established loans whose inflected forms are frequent in Polish text (maila, weekendu, Facebooku) but still English-sounding
LOAN_STEMS = ("mail", "weekend", "facebook", "lunch", "router", "link", "youtube", "google", "instagram", "podcast", "playlist", "meeting",
              "smartphone", "deadline", "feedback", "update", "spotify", "netflix", "whatsapp", "messenger", "tiktok", "linkedin", "iphone")
PL_DIACRITICS = re.compile(r"[ąćęłńóśźż]", re.I)
WORD = re.compile(r"\{[^{}]*\}|[A-Za-zÀ-ÿĄĆĘŁŃÓŚŹŻąćęłńóśźż][A-Za-zÀ-ÿĄĆĘŁŃÓŚŹŻąćęłńóśźż'’-]*")


def _load_curated():
    """(single-word entries, multi-word phrases) of english_words.txt"""
    f = HERE / "english_words.txt"
    if not f.exists():
        return set(), []
    single, phrases = set(), set()
    for l in f.read_text(encoding="utf-8").splitlines():
        l = " ".join(l.strip().lower().split())
        if l and not l.startswith("#"):
            (phrases if " " in l else single).add(l)
    return single, sorted(phrases, key=len, reverse=True)


CURATED, PHRASES = _load_curated()
PHRASE_EN = {l.strip() for l in PHRASE_WORDS_FILE.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")} if PHRASE_WORDS_FILE.exists() else set()
CURATED |= PHRASE_EN                                                         # pull request -> pull, request (not: sale)
PHRASES = [ph for ph in PHRASES if any(w in PHRASE_EN or w in CURATED for w in ph.split())]   # all-Polish phrases ("to go") are left out
_PHRASE_RX = None


def phrase_rx():
    global _PHRASE_RX
    if _PHRASE_RX is None:
        alt = "|".join(r"\s+".join(map(re.escape, ph.split())) for ph in PHRASES) or r"(?!)"
        suf = "|".join(map(re.escape, SUFFIXES))
        L = "A-Za-zÀ-ÿĄĆĘŁŃÓŚŹŻąćęłńóśźż"
        _PHRASE_RX = re.compile(rf"(?<![{L}'’-])({alt})((?:'|’)?(?:{suf}))?(?![{L}'’-])", re.I)
    return _PHRASE_RX


def known(w):
    lw = w.lower()
    return lw in CURATED and lw not in READ_AS_POLISH


@lru_cache(maxsize=200000)
def polish_word(lw):
    """a common Polish word in SGJP (Morfeusz 2): not unknown, not only a name. None when Morfeusz is not installed."""
    global _MORF
    try:
        if _MORF is None:
            import morfeusz2
            _MORF = morfeusz2.Morfeusz()
    except ImportError:
        return None
    for _, _, (_, _, tag, quals, _) in _MORF.analyse(lw):
        if tag.split(":")[0] not in ("ign", "frag") and not (quals and set(quals) <= NAME_QUALS):
            return True
    return False


_MORF = None
NAME_QUALS = {"imię", "nazwisko", "nazwa_geograficzna", "nazwa_organizacji", "nazwa_rzeczy", "nazwa_własna", "nazwa_osobowa"}


def rebuild_phrase_words():
    """english_phrase_words.txt: words of curated phrases that are not Polish words (needs Morfeusz 2)"""
    assert polish_word("sala") is True, "Morfeusz 2 (pip install morfeusz2) is needed to rebuild"
    words = sorted({w for ph in _load_curated()[1] for w in ph.split() if len(w) > 2})
    en = [w for w in words if polish_word(w) is False]
    PHRASE_WORDS_FILE.write_text("# words of the curated phrases (english_words.txt) that are not Polish words in SGJP (Morfeusz 2):\n"
                                 "# English on their own too. Rebuilt by: python3 voxcpm/english_detect.py --rebuild-phrase-words\n"
                                 + "\n".join(en) + "\n", encoding="utf-8")
    return len(words), len(en), [w for w in words if w not in en]


@lru_cache(maxsize=200000)
def by_frequency(w):
    """fallback for whole words not on the list: clearly English, rare in Polish"""
    lw = w.lower()
    if lw in READ_AS_POLISH or PL_DIACRITICS.search(lw) or len(lw) < 4:
        return False
    try:
        from wordfreq import zipf_frequency
    except ImportError:
        return False
    en, pl = zipf_frequency(lw, "en"), zipf_frequency(lw, "pl")
    return en >= EN_MIN and en - pl >= MARGIN and pl < PL_MAX and polish_word(lw) is not True


@lru_cache(maxsize=200000)
def pl_word(lw):
    try:
        from wordfreq import zipf_frequency
    except ImportError:
        return False
    return zipf_frequency(lw, "pl") >= PL_INFLECTED_MAX


def is_english(w):
    return known(w) or by_frequency(w)


def split_word(w):
    """English stem + Polish ending, or None"""
    if is_english(w):
        return w, ""
    lw = w.lower()
    if "'" in w or "’" in w:                                       # deadline'u, pendrive'a
        st = w.replace("’", "'").split("'", 1)
        if known(st[0]) or by_frequency(st[0]):
            return st[0], "'" + st[1] if st[1] else ""
    if pl_word(lw) and not any(lw.startswith(x) and len(lw) - len(x) <= 3 for x in LOAN_STEMS):
        return None      # wina, legendy, deskę, worki: Polish words whose stem happens to be English
    for k in SUFFIXES:   # inflected forms only of listed words (stripping endings of arbitrary words: nowy -> "now" + y)
        if lw.endswith(k) and len(w) - len(k) >= 3 and known(w[:-len(k)]):
            return w[:-len(k)], w[-len(k):]
    return None


def mark_phrases(text):
    """wrap curated multi-word phrases (outside existing braces): 'pull requesta' -> '{pull request}a'"""
    parts = re.split(r"(\{[^{}]*\})", text)
    return "".join(p if p.startswith("{") else phrase_rx().sub(lambda m: "{" + m.group(1) + "}" + (m.group(2) or ""), p) for p in parts)


def mark_english(text):
    text = mark_phrases(text)

    def rep(m):
        w = m.group(0)
        if w.startswith("{"):
            return w
        parts = [p for p in re.split(r"(-)", w)] if "-" in w and not is_english(w) else [w]   # Wi-Fi stays whole if known
        if len(parts) > 1:
            return w if split_word(w) is None else "{" + split_word(w)[0] + "}" + split_word(w)[1]
        s = split_word(w)
        return w if s is None else "{" + s[0] + "}" + s[1]
    out = WORD.sub(rep, text)
    return re.sub(r"\}(\s+)\{", r"\1", out)      # adjacent English words -> one span: {pull request}


def js_data():
    """english_data.json for the browser port (english.js): the curated list, phrases, and the inflected forms that are
    common Polish words (pl_word, precomputed because the browser has no frequency table)"""
    block = sorted({c + k for c in CURATED if c not in READ_AS_POLISH for k in SUFFIXES
                    if pl_word(c + k) and not any(c.startswith(x) and len(k) <= 3 for x in LOAN_STEMS)})
    return {"curated": sorted(CURATED), "phrases": PHRASES, "read_as_polish": sorted(READ_AS_POLISH), "suffixes": SUFFIXES,
            "loan_stems": list(LOAN_STEMS), "pl_inflected_block": block}


if __name__ == "__main__":
    import argparse, csv, json
    ap = argparse.ArgumentParser(); ap.add_argument("--eval", type=Path); ap.add_argument("--show", type=int, default=15)
    ap.add_argument("--js-data", type=Path, help="write english_data.json for english.js")
    ap.add_argument("--rebuild-phrase-words", action="store_true"); a = ap.parse_args()
    if a.rebuild_phrase_words:
        n, k, pl = rebuild_phrase_words(); print(f"{k} of {n} phrase words are English on their own; Polish: {pl}"); raise SystemExit
    if a.js_data:
        a.js_data.write_text(json.dumps(js_data(), ensure_ascii=False), encoding="utf-8"); print("wrote", a.js_data); raise SystemExit
    rows = list(csv.DictReader(a.eval.open(encoding="utf-8"), delimiter="|"))
    tp = fp = fn = 0; fps, fns = [], []
    for r in rows:
        gold = {s.lower() for s in re.findall(r"\{([^}]*)\}", r["marked"])}
        pred = {s.lower() for s in re.findall(r"\{([^}]*)\}", mark_english(r["text"]))}
        gw = {w for s in gold for w in s.split()}; pw = {w for s in pred for w in s.split()}
        tp += len(gw & pw); fp += len(pw - gw); fn += len(gw - pw)
        fps += [(w, r["text"]) for w in pw - gw]; fns += [(w, r["text"]) for w in gw - pw]
    print(f"words: precision {tp / max(1, tp + fp):.3f}, recall {tp / max(1, tp + fn):.3f} (tp {tp}, fp {fp}, fn {fn})")
    from collections import Counter
    print("most common false positives:", Counter(w for w, _ in fps).most_common(a.show))
    print("most common misses:", Counter(w for w, _ in fns).most_common(a.show))
