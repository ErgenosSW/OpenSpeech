# OpenSpeech

OpenSpeech to otwartoźródłowa aplikacja na Androida, która powstała jako pomoc dla osób niemówiących. Pozwala wpisać tekst i odczytać go na głos, żeby ułatwić codzienny kontakt z innymi ludźmi.

## Co potrafi?

- Czyta polski tekst bez internetu.
- Ma duże przyciski i szybkie komunikaty odczytywane jednym dotknięciem.
- Pozwala zapisywać, zmieniać i usuwać własne komunikaty.
- Udostępnia nagranie audio innym aplikacjom, np. komunikatorom.
- Pozwala regulować tempo i zapisać nagranie WAV.

## Budowanie

Potrzebne są JDK 17, Android SDK 35 z Build Tools 35.0.0 oraz Python 3.12 lub nowszy. Ustaw zmienne `JAVA_HOME` i `ANDROID_HOME` dla swojego środowiska.

```sh
python3 scripts/prepare-assets.py
./build.sh
```

APK powstanie w `app/build/outputs/apk/debug/app-debug.apk`.

Skrypt pobiera bibliotekę syntezy i model głosu oraz sprawdza ich sumy SHA-256. Narzędzia, duże pliki modelu i lokalne klucze podpisujące są wyłączone z Git. Każdy budujący aplikację tworzy własny klucz testowy.

## Licencja

Kod OpenSpeech jest udostępniony na licencji **GNU GPL v3.0** - zobacz [LICENSE](LICENSE). Możesz go używać, zmieniać i rozwijać zgodnie z jej warunkami.

Biblioteki oraz model głosu mają własne licencje i warunki opisane w [THIRD_PARTY.md](THIRD_PARTY.md) i katalogu [licenses](licenses/). Model głosu nie jest przechowywany w tym repozytorium.

**Niech żyje open source!**
