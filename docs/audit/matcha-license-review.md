# Przegląd praw do wybranego głosu

Data: 2026-11-15. Wybrano machinekind/Matcha-TTS-PL-Edge-v2, rewizja 8ac4956ca61f91732e99965a8645215f0a8bd383, syntetyczny głos male_voxcpm_2. Używane są niezmienione wagi fp32: encoder.onnx, decoder_t4.onnx i vocoder.onnx. Dokładne sumy zapisano w dependencies.lock.json.

## Podstawa redystrybucji

Autor publikuje plik LICENSE z CC BY-SA 4.0 i sekcję Licence and attribution dotyczącą modelu, w tym adaptacji wokodera. Pełne dokumenty MODEL_CARD, LICENSE i ATTRIBUTION zapisano w third_party/matcha-edge i dołączono do APK. To odrębna deklaracja licencji modelu, a nie wyłącznie opis licencji nagrań.

CC BY-SA 4.0 dopuszcza kopiowanie, redystrybucję i adaptacje, także komercyjne, pod warunkami uznania autorstwa, zachowania informacji o zmianach i odpowiedniego ShareAlike. Niezmienione wagi zachowują CC BY-SA 4.0. OpenSpeech nie oznacza ich jako GPL ani CC0.

## Pochodzenie

Zachowany ATTRIBUTION opisuje bazę Matcha-TTS-PL-Pretrain, VCTK warm start oraz dane Wolnych Lektur i AZON. Wybrany finalny głos jest syntetyczny, zaprojektowany opisem tekstowym w VoxCPM2, a nie bezpośrednim klonem lektora. Wokoder pochodzi z HiFi-GAN V2 VCTK (MIT dla kodu i checkpointu, CC BY 4.0 dla VCTK), następnie był dostrajany do głosu. Pełne przypisanie danych i autorów zachowano bez skracania. Nie korzystamy ze starego eksportu Darkmana, Lessaca ani ich kart jako dowodów dla tego modelu.

Autor wskazuje, że całego zmodyfikowanego forka treningowego nie opublikowano. Dostępne są wagi, grafy ONNX, kod inferencji i opis procedury. Nie deklarujemy pełnej odtwarzalności pierwotnego treningu ani nie utożsamiamy licencji modelu z dostępnością każdego użytego narzędzia treningowego.

## Kod adapterów

Przeniesiono procedurę inferencji i polski frontend do Androida. Zmiany obejmują lokalne sesje, ograniczenia pamięci, anulowanie, usunięcie zależności od Pythona i wordfreq oraz kontrolę zasobów. Źródła oryginalne i atrybucje pozostają w paczce. Własne wkłady do adaptacji są GPL-3.0-only; Creative Commons dopuszcza taką jednokierunkową zgodność adaptacji CC BY-SA 4.0 z GPLv3. Nie zmienia to licencji niezmienionych wag.

Źródła pierwotne:

- https://huggingface.co/machinekind/Matcha-TTS-PL-Edge-v2/tree/8ac4956ca61f91732e99965a8645215f0a8bd383
- https://huggingface.co/machinekind/Matcha-TTS-PL-Pretrain
- https://creativecommons.org/licenses/by-sa/4.0/
- https://creativecommons.org/compatible-licenses/
