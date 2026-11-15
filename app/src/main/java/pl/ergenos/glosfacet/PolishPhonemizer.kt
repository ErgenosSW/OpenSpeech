// SPDX-License-Identifier: GPL-3.0-only
// Polish cleaners/symbol inventory adapted from machinekind/Matcha-TTS-PL-Edge-v2 frontend/text.py.
// Punctuation behavior also follows phonemizer (GPL-3.0-or-later, Mathieu Bernard).
// Upstream attribution and CC BY-SA 4.0/MIT notices are retained under third_party/matcha-edge.
package pl.ergenos.glosfacet

import java.io.File
import java.util.Locale

internal class PolishPhonemizer(directory: File) {
    companion object {
        init { System.loadLibrary("openspeech-phonemes") }
        private const val SYMBOLS = "_;:,.!?¡¿—…\"«»“” ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" +
            "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ̃"
        // The published symbol list contains duplicate apostrophes; its map uses the last index.
        private val ids = SYMBOLS.withIndex().associate { it.value to it.index.toLong() }
        private val punctuation = Regex("(\\s*(?:[;:!?¡¿—…\"«»“”]|(?<![0-9])[,.]|[,.](?![0-9]))+\\s*)+")
        private val abbreviations = listOf("np." to "na przykład", "tzn." to "to znaczy", "tj." to "to jest",
            "itd." to "i tak dalej", "itp." to "i tym podobne", "m.in." to "między innymi", "dr" to "doktor",
            "prof." to "profesor", "inż." to "inżynier", "mgr" to "magister", "ul." to "ulica", "al." to "aleja",
            "godz." to "godzina", "min." to "minut", "św." to "święty", "nr" to "numer", "tel." to "telefon", "zł" to "złotych")
    }
    init { initialize(directory.absolutePath.toByteArray(Charsets.UTF_8)) }
    private external fun initialize(directory: ByteArray)
    private external fun phonemes(text: ByteArray): ByteArray

    fun textToIds(input: String): LongArray {
        var text = input.replace(Regex("\\s+\\?"), "?").lowercase(Locale.forLanguageTag("pl-PL")).replace(Regex("\\s*(?:—|–| - )\\s*"), ", ")
            .replace("…", "...").replace(Regex("[„”“\"«»]"), "").replace("{", "").replace("}", "")
        for ((short,full) in abbreviations) {
            val tail = if (short.endsWith('.')) "" else "(?![0-9a-ząćęłńóśźż])"
            text = text.replace(Regex("(?<![0-9a-ząćęłńóśźż])"+Regex.escape(short)+tail), full)
        }
        text = text.replace(Regex("(?<=[0-9ivx])\\s?r\\."), " roku").replace(Regex("(?<=[0-9ivx])\\s?w\\."), " wieku")
        val ipa = StringBuilder(); var position = 0
        fun appendSpeech(value: String) {
            if (value.isBlank()) return
            val converted = String(phonemes(value.trim().toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
                .replace(Regex("\\([a-z-]+\\)"), "")
            ipa.append(converted)
        }
        for (match in punctuation.findAll(text)) {
            appendSpeech(text.substring(position, match.range.first))
            ipa.append(match.value); position = match.range.last+1
        }
        appendSpeech(text.substring(position))
        val cleaned = ipa.toString().replace(Regex("\\s+"), " ").trim()
        val sequence = cleaned.mapNotNull { ids[it] }
        check(sequence.isNotEmpty()) { "Nie można odczytać tekstu" }
        return LongArray(2*sequence.size+1).also { result -> sequence.forEachIndexed { index,id -> result[2*index+1]=id } }
    }
}
