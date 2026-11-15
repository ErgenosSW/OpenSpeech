# Kontrola komponentów APK

Sprawdzono rzeczywistą kompilację nowej aplikacji. Wynik maszynowy jest w component-selection.json, pełne sumy plików w development-apk-inventory.json. Zależności JVM i ich archiwa źródeł opisuje gradle-runtime-inventory.json. Odpowiadające źródła i ich hashe opisują licenses/native-sources.json oraz dokument corresponding-source.md.

W APK są wyłącznie trzy biblioteki natywne na każde ABI (arm64-v8a i x86_64): ONNX Runtime, jego most JNI oraz kompilowany z dołączonych źródeł fonemizator OpenSpeech/eSpeak NG. Nie ma sherpa-onnx, Darkmana, Lessaca ani ZONOS3. Nie dołączono kodu Pythona, korpusu wordfreq, gotowych głosów MBROLA ani usług chmurowych.

Zweryfikowano sumy każdego dołączonego pliku modelu i danych. Manifest nie wymaga internetu ani mikrofonu. Biblioteki JVM mają dołączone źródła, deklaracje licencji oraz powiadomienia. Metadane/BOM nie są liczone jako osobne biblioteki kodu. Jawne wersje i sumy artefaktów są przypięte przez Gradle.

Kontrola końcowego APK release obejmuje dodatkowo certyfikat, wyłączone debugowanie, identyfikator aplikacji, biblioteki i zasoby oraz sumy końcowego pliku. Nie jest to test funkcjonowania urządzenia; ten punkt pozostaje oddzielny.
