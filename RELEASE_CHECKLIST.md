# Pierwsze publiczne wydanie OpenSpeech

Dystrybucja pozostaje wstrzymana do zakończenia kontroli publikacji. Repozytorium i kod pozostają publiczne. Nie wykonujemy commitów, pushów, tagów ani publikacji za właściciela.

## Kandydat do testów

- [x] Wybrany polski głos ma jawną licencję modelu CC BY-SA 4.0.
- [x] Zachowano kartę, licencję i pełną atrybucję modelu oraz opis jego pochodzenia.
- [x] Usunięto poprzedni model i sherpa-onnx z aktywnych zależności i APK.
- [x] Fonemizator i jego dane są budowane z dołączonych źródeł GPL.
- [x] Właściciel zatwierdził nowy stały klucz wydania. Zapisano go poza repozytorium.
- [x] Zachowano applicationId pl.ergenos.glosfacet i zwiększono versionCode ponad wcześniejsze 5.
- [x] Przetestowano polski frontend, syntezę, tempo i anulowanie na komputerze.
- [x] Sprawdzono końcowe podpisane APK, jego manifest, architektury, zasoby i certyfikat.

`python3 scripts/check-release.py` kontroluje dowody i zgodność plików przed zbudowaniem podpisanego kandydata. `python3 scripts/check-release.py --publication` dodatkowo wymaga dowodów testów telefonu i przeglądu końcowego APK. Kontrola zapisów nie zastępuje oceny treści licencji i testów.

## Przed publikacją

- [ ] Na telefonie potwierdzono zrozumiałość krótkich i długich komunikatów, polskich znaków, liczb, skrótów i interpunkcji.
- [ ] Zmierzono start, czas do dźwięku, czas syntezy, pamięć i działanie trybu offline.
- [ ] Sprawdzono zatrzymanie mowy, zmianę tempa, zapis i edycję komunikatów, WAV, udostępnianie audio i ponowne uruchomienie.
- [ ] Sprawdzono, że nowa instalacja i kolejne aktualizacje używają stałego certyfikatu. Przejście ze starego APK wymaga odinstalowania i usuwa stare dane; właściciel zatwierdził tę zmianę.
- [ ] OpenSpeech.apk i OpenSpeech.zip opisują tę samą wersję; źródła obejmują dołączone komponenty GPL, dane i skrypty budowania, a licencje i NOTICE są dostępne w APK oraz paczce źródeł.
- [ ] Kontrola --publication przechodzi. Właściciel samodzielnie wykonuje commit, tag, push i publikację.

## Powiązanie dowodów

release-review.json zawiera sumę dependencies.lock.json, odcisk aktualnych źródeł i sumy każdego dokumentu dowodowego. Odcisk można odczytać poleceniem `python3 scripts/check-release.py --fingerprint`. Zmiana kodu, modelu, natywnych źródeł, licencji lub blokad zależności wymaga ponownego przeglądu. Nie wpisywać approved dla nieprzeprowadzonych kontroli.
