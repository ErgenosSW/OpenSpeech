# Testy na komputerze - 2026-11-15

Platforma: Linux x86_64, Intel Core i5-12400F, CPU, do czterech wątków. To nie są pomiary telefonu.

- Składnia i kompilacja Kotlin/Java oraz natywny fonemizator dla arm64-v8a i x86_64 przeszły z Android SDK 35, NDK 28.2.13676358 i JDK 17.
- 17 polskich przypadków dało dokładnie takie same identyfikatory fonemów w kodzie aplikacji i opublikowanym frontendzie autora modelu. Obejmują polskie znaki, krótkie odpowiedzi, liczby dziesiętne, godziny, skróty i interpunkcję.
- Przetestowano dokładny kod inferencji aplikacji z ONNX Runtime 1.23.2 na komputerze, nie tylko przykład w Pythonie. Wygenerowano siedem nagrań, potwierdzono poprawny format, niepuste i skończone próbki, zmianę długości przy zmianie tempa oraz anulowanie.
- Czas syntezy siedmiu komunikatów: około 0,029-0,266 s, przy długości audio 0,232-4,539 s. Wartości nie obejmują pierwszego załadowania bibliotek i modelu.
- Lokalny Whisper small, użyty wyłącznie do kontroli, rozpoznał polskie komunikaty bez obcych dopowiedzeń. Różnice obejmowały m.in. zapis „Potrzebuje” zamiast „Potrzebuję”, zapis cyfr i interpunkcję. Automatyczna transkrypcja nie jest niezależną oceną odsłuchową ani gwarancją poprawności każdej wypowiedzi.
- Oddzielna próba ZONOS3 została odrzucona: mimo kontroli wyrównania generowała błędne krótkie odpowiedzi. Nie jest dołączona do APK ani aktywnych zależności.

Surowe wyniki bieżących testów: host-synthesis-results.txt i host-asr-results.json. Przed publikacją pozostają odsłuch, szybkość, pamięć i pełne przepływy użytkownika na telefonie.
