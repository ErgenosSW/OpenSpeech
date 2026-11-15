# Źródła odpowiadające kompilacji

Kod aplikacji i adapterów jest w app/src/main, skrypty w scripts. Pełne źródła eSpeak NG 1.52.0 wraz z ucd-tools, słownikami i źródłami danych fonetycznych są w third_party/espeak-ng. Natywna biblioteka jest kompilowana z tego drzewa; nie pobieramy gotowego fonemizatora. Wyłączono MBROLA, Sonic, speechPlayer, zewnętrzny sterownik audio i asynchroniczny silnik. Dane polskie i bootstrapowe angielskie kompiluje scripts/build-phoneme-data.py.

third_party/matcha-edge zachowuje opublikowane źródła inferencji/frontendu oraz kompletną kartę, licencję i atrybucję modelu. third_party/phonemizer-reference zachowuje źródło referencyjnej obsługi interpunkcji z jego powiadomieniami GPL.

third_party/java-sources zawiera archiwa źródeł rozstrzygniętych bibliotek JVM. To materiały źródłowe, nie dodatkowe zależności wykonywalne APK. Powiązanie modułu z archiwum jest w docs/audit/gradle-runtime-inventory.json.

third_party/source-archives zawiera źródła ONNX Runtime v1.23.2 oraz przypięte w jego cmake/deps.txt archiwa zależności CPU, w tym Eigen na MPL. Suma oryginalnego archiwum, suma przepakowanego pliku i zakres usunięcia niepotrzebnych danych testowych są w onnxruntime-source-provenance.json; sumy zależności i ich pierwotne adresy są w onnxruntime-dependency-sources.json. Nie zmieniano kodu runtime przy przepakowaniu. Nie dołączamy narzędzi SDK/JDK, plików prywatnego podpisu ani pamięci podręcznej kompilacji.

Dokładne wejścia modelu są przypięte w dependencies.lock.json, zależności JVM w app/gradle.lockfile i gradle/verification-metadata.xml, a natywne źródła i archiwa w licenses/native-sources.json. Model można odtworzyć lokalnie przez scripts/prepare-assets.py. Kod OpenSpeech i dołączone źródła pozwalają modyfikować i przebudować aplikację; pakiet nie deklaruje odtworzenia nieopublikowanego treningu modelu.

OpenSpeech.zip należy udostępnić razem z odpowiadającym mu OpenSpeech.apk. Pomiary na komputerze i testowe modele rozpoznawania mowy nie są składnikami APK ani wymaganiami uruchomieniowymi.
