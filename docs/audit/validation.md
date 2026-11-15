# Bieżący wynik walidacji

Wybrany głos: polski syntetyczny male_voxcpm_2, Matcha-TTS-PL-Edge-v2, fp32, deterministyczne długości i cztery kroki dekodera. Darkman i próba ZONOS3 nie są składnikami nowej aplikacji.

Przeszły: osiem testów narzędzi zależności/przeglądu, 17 przypadków identyczności polskiego frontendu z referencją autora, siedem prób syntezy wykonywanych przez kod aplikacji, kontrola skończonych próbek i formatu, tempo, anulowanie, kompilacja natywna dla obu ABI, debug oraz lint. Surowe wyniki hosta są w host-synthesis-results.txt i host-asr-results.json.

Dane fonemizatora są kompilowane z dołączonych źródeł. Wagi, źródła, biblioteki i licencje mają przypięte sumy. W APK jest osobny ekran licencji i źródeł. Przegląd przed kompilacją podpisanego kandydata nie oznacza zatwierdzenia publikacji.

Właściciel zatwierdził nowy stały klucz wydania. Jego prywatny plik, hasło i kopia są poza repozytorium, w prywatnym katalogu użytkownika. Certyfikat publiczny jest identyfikowany przez release-certificate.sha256. Starego klucza nie odzyskano, więc przejście ze starego APK wymaga odinstalowania i usuwa stare dane; właściciel zaakceptował ten skutek.

Nie było podłączonego telefonu ani emulatora. Do wykonania pozostają odsłuch i pełne przepływy na urządzeniu, w tym działanie offline, udostępnianie, WAV, zapisane komunikaty, orientacja i pomiary pamięci/opóźnień. Kontrola publikacji pozostaje z tego powodu zamknięta.

Nie wykonano commitów, tagów, pushów, publikacji ani wysyłania wiadomości do osób trzecich.
