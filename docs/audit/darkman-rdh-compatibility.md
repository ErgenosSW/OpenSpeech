# Darkman rdh - wynik wstępnego testu

Data: 2026-11-15. Test na komputerze Linux x86_64, nie na Androidzie.

## Tożsamość plików

Źródło: rhasspy/piper-voices, rewizja `cf910fb59f1d79ab3b625a63993a108751c6c25d`.

- ONNX wejściowy: 63221984 bajty, SHA-256 `cf757ee307e3a7c23d5d8f69934deab51683d01c1c6be376d3e13146020f1530`.
- Konfiguracja JSON: SHA-256 `0d98a67829a1b7ffbce7faacf4ea7bb7a7ce2ebc1598ad0289c0f6c1f352dbd0`.
- MODEL_CARD: SHA-256 `a2c89d3059a830d47fb0159973088c02ae3a4c7e56235a3abe63b9507340bdab`.
- Linux sherpa-onnx v1.12.20 shared: SHA-256 archiwum `13a2c502f5ef373f514a5fc5c5ad03cc0798dcb564a6f8cbd54f8f6ce9e47462`.

## Przygotowanie testu

Surowy ONNX nie zawierał metadanych. Wejścia: input, input_lengths, scales. Dodano metadane według oficjalnego `scripts/piper/add_meta_data.py` dla v1.12.20: model_type=vits, comment=piper, language=Polish, voice=pl, has_espeak=1, n_speakers=1, sample_rate=22050. Tokeny wygenerowano z phoneme_id_map, pomijając znak nowej linii tak jak narzędzie upstream. Nie zmieniano grafu ani wag.

SHA-256 pliku po dodaniu metadanych: `8f444c41e5eeadcdfe8ad539475f0252f20151d37312ad036259fcd172f2ee30`.

Użyto zasobów espeak-ng-data z lokalnej kopii wcześniejszego APK. Ich wersja nie została jeszcze ustalona. To dodatkowy powód, dla którego próba nie stanowi pełnego testu przyszłego wydania.

## Wynik

Próba syntezy zdania „Dzień dobry. Potrzebuję pomocy. Proszę o chwilę cierpliwości.” zakończyła się kodem 255 przed wygenerowaniem audio:

```text
piper-phonemize-lexicon.cc:ReadTokens:107 Error when reading tokens at Line aɪ 161. size: 2
```

Nowa konfiguracja ma pięć tokenów składających się z więcej niż jednego znaku Unicode: aɪ, aʊ, ɔɪ, eɪ, oʊ (identyfikatory 161-165). Czytnik v1.12.20 wymaga pojedynczego znaku z wyjątkiem specjalnego tokenu <BLNK>. Zwykła konwersja według dotychczasowego narzędzia nie daje więc działającego modelu.

Nie usuwano tych tokenów w celu ukrycia błędu. Należy zweryfikować zgodny fonemizator/tokenizację i ewentualną nowszą wersję sherpa-onnx przed dalszymi pomiarami.

Brak potwierdzenia jakości, szybkości i zgodności na Androidzie. Brak zatwierdzenia praw do redystrybucji wag. Model nie został podmieniony w aplikacji.

Źródła:

- https://huggingface.co/rhasspy/piper-voices/tree/cf910fb59f1d79ab3b625a63993a108751c6c25d/pl/pl_PL/darkman/medium
- https://github.com/k2-fsa/sherpa-onnx/blob/v1.12.20/scripts/piper/add_meta_data.py
- https://github.com/k2-fsa/sherpa-onnx/blob/v1.12.20/sherpa-onnx/csrc/piper-phonemize-lexicon.cc
