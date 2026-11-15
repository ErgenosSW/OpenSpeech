# OpenSpeech

OpenSpeech to darmowa, otwartoźródłowa aplikacja na Androida dla osób niemówiących. Pozwala wpisać tekst i odczytać go na głos, zapisywać własne komunikaty oraz udostępniać nagrania. Mowa jest generowana lokalnie, bez internetu, konta i płatnych usług.

## Stan pierwszego wydania

Poprzednie wydanie zostało usunięte. Przygotowana nowa wersja zastępuje stary model polskim syntetycznym głosem męskim na licencji CC BY-SA 4.0. Nowy klucz wydania został zatwierdzony przez właściciela. Kod, licencje i zależności opisano w [THIRD_PARTY.md](THIRD_PARTY.md).

Kompilacja podpisanego APK do testów i zgoda na publiczną publikację są oddzielne. Przed publikacją wymagane są testy na telefonie i sprawdzenie końcowego APK zgodnie z [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). Testy na komputerze nie zastępują testów urządzenia.

**Przejście ze starego APK wymaga odinstalowania go i usuwa jego zapisane dane.** Poprzedniego klucza Android Debug nie odnaleziono; kolejne aktualizacje nowej wersji muszą używać tego samego nowego klucza.

## Funkcje

- Polska mowa offline i duże przyciski.
- Zapisywanie, edycja i usuwanie własnych komunikatów.
- Regulacja tempa bez zmiany wysokości głosu.
- Udostępnianie audio innym aplikacjom i zapis WAV.
- Weryfikacja plików głosu przy pierwszym przygotowaniu.

## Budowanie

Potrzebne: pełny JDK 17, Python 3.12+, CMake i kompilator C/C++ na komputerze, Android SDK 35, Build Tools 35.0.0, NDK 28.2.13676358 oraz SDK CMake 3.22.1. Ustaw JAVA_HOME i ANDROID_HOME. Pierwsze pobranie zależności wymaga sieci; zainstalowana aplikacja jej nie potrzebuje.

```sh
python3 scripts/prepare-assets.py
./build.sh
```

Skrypt pobiera przypięte wagi, kompiluje dane językowe z dołączonych źródeł i sprawdza sumy SHA-256. Przygotowany model to około 88 MB; dokładny rozmiar APK zależy od dołączonych ABI i bibliotek. Pliki modelu i lokalne narzędzia są wyłączone z Git.

Testowe APK powstaje w app/build/outputs/apk/debug/app-debug.apk i używa lokalnego klucza debug. Nie zastępuj nim stałego klucza wydania.

## Podpisane APK

```sh
./build-release.sh
```

Skrypt korzysta z prywatnego katalogu `${XDG_DATA_HOME:-$HOME/.local/share}/OpenSpeech/signing`, albo z OPENSPEECH_SIGNING_DIR / jawnych zmiennych OPENSPEECH_STORE_FILE, OPENSPEECH_STORE_PASSWORD, OPENSPEECH_KEY_ALIAS i OPENSPEECH_KEY_PASSWORD. Klucz i hasło pozostają poza repozytorium. Skrypt nie tworzy zastępczego klucza i niczego nie publikuje.

Pakowanie podpisanego kandydata i odpowiadających źródeł (bez publikacji):

```sh
python3 scripts/package-release.py --output /wybrany/katalog
```

Archiwum obejmuje źródła bibliotek JVM, fonemizatora i natywnych zależności. Nie obejmuje kluczy, haseł ani lokalnych narzędzi. Przed publiczną publikacją:

```sh
python3 scripts/check-release.py --publication
```

Oczekiwany błąd tej kontroli przed testami telefonu nie blokuje przygotowania podpisanego kandydata do takich testów. Nie oznaczaj testów urządzenia jako zakończonych bez ich wykonania.

## Licencja

Kod aplikacji: GPL-3.0-only, zobacz [LICENSE](LICENSE). Model i komponenty zewnętrzne zachowują własne wolne licencje oraz wymagania dotyczące autorstwa, źródeł i redystrybucji. Pakiet źródeł OpenSpeech.zip musi odpowiadać udostępnianemu OpenSpeech.apk.

Commit, tag, push i publikację wykonuje właściciel.
