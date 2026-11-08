# SRT Prevajalnik GPT – Android

To je **izvorna koda Android aplikacije**, ne že prevedena APK datoteka.

## Kaj vsebuje
- Pravo Android aplikacijo (Java / Android WebView), prilagojeno Samsung Galaxy A57 5G.
- Slovenski začetni zaslon, vnos HTTPS naslova strežnika.
- Izbiro več SRT datotek prek Androidovega izbirnika dokumentov.
- Prenos prevedenih SRT in ZIP datotek v mapo Prenosi.
- Dostop do spletnega prevajalskega vmesnika, ki prikazuje napredek po datotekah, omogoča ponovne poskuse in preverja časovne oznake.
- API ključ **ni** v APK: konfigurira se samo na strežniku.

## Kako izdelati APK
1. Na Windows računalnik namesti **Android Studio** (uradna stran https://developer.android.com/studio).
2. Razširi arhiv in odpri mapo `SRT-Prevajalnik-GPT-Android` z ukazom **Open**.
3. Počakaj, da Android Studio prenese Android SDK in Gradle ter sinhronizira projekt (zahteva internet).
4. Izberi **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK najdeš v `app/build/outputs/apk/debug/app-debug.apk`.
6. Prenesi APK na telefon, odpri datoteko in po potrebi dovoli namestitev iz izbranega vira. Nameščaj samo lastno sestavljeno datoteko.

## Nastavitev strežnika
1. Strežniško aplikacijo dobiš v arhivu `SRT-Prevajalnik-GPT-v1.zip` (ločena datoteka).
2. Namesti jo na Python gostovanje z HTTPS, nastavi skrivni `OPENAI_API_KEY` in omogoči dostop samo sebi.
3. Ob prvem zagonu Android aplikacije vpiši **HTTPS URL** svojega strežnika.
4. V Android aplikaciji izberi podnapise, prevedi in prenesi rezultate.

## Omejitve in varnost
- Brez delujočega HTTPS strežnika aplikacija ne more prevajati.
- Potrebna je internetna povezava, OpenAI API pa se obračuna posebej.
- Strežniška različica 1.0 nima prijave ali omejitev porabe. **Ne objavljaj je javno brez dodatne zaščite**.
- Prikaz napredka in ponovni poskusi so odvisni od strežniške aplikacije.
- Android projekt v tem okolju ni bil preveden v APK, ker Android SDK/Gradle nista nameščena; funkcionalni preizkus na telefonu še ni opravljen.
