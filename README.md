# SRT Prevajalnik GPT – Android

Android aplikacija v slovenščini za prevajanje **1–25 angleških datotek SRT v slovenščino**, z izvozom datotek `.sl.srt` ali skupnega ZIP-a. Podpira Android 13 ali novejši, vključno s Samsung Galaxy A57. APK uporablja Androidovo izvajalno okolje in ne potrebuje knjižnic za posamezno procesorsko arhitekturo.

**[Prenesi podpisani APK 1.0.0](downloads/SRT-Prevajalnik-GPT-1.0.0.apk?raw=true)** · [Kontrolna vsota SHA-256](downloads/SHA256SUMS.txt)

## Uporaba

1. Namesti podpisani APK na telefon. Ob namestitvi po potrebi dovoli nameščanje aplikacij za brskalnik oziroma upravitelja datotek, s katerim si odprl APK.
2. V aplikacijo vnesi svoj **OpenAI API-ključ** in tapni **Shrani ključ**. Ključ pridobiš na https://platform.openai.com/api-keys. API potrebuje svoje dobroimetje; naročnina ChatGPT ne vključuje porabe API-ja. Ključa ne objavljaj na GitHubu ali v pogovoru.
3. Izberi model: privzeti `gpt-4.1-mini` ali `gpt-4.1`.
4. Tapni **Izberi SRT datoteke (do 25)**. V Androidovem izbirniku pridrži prvo datoteko in označi druge. Izbor nad 25 je zavrnjen v celoti; obstoječi izbor ostane ohranjen.
5. Tapni **Prevedi v slovenščino**. Aplikacija pokaže napredek po datotekah in vrsticah. Omogoči obvestila za prikaz napredka tudi zunaj aplikacije. Internet mora ostati na voljo.
6. Po potrebi tapni **Ustavi prevajanje**, nato **Nadaljuj / ponovi**. Že potrjeni paketi so shranjeni na telefonu. Zadnja nedokončana zahteva se lahko ponovi in povzroči dodatno porabo API-ja.
7. Tapni **Shrani slovenske SRT v mapo** in izberi izhodno mapo ali **Shrani vse prevedene kot ZIP** ter izberi ime in mesto shranjevanja. Izvoz vsebuje samo v celoti zaključene in preverjene prevode. Če so nekatere datoteke neuspešne, jih lahko ponovno obdelaš.

## Ohranitev podnapisov

- Številčenje in celotne časovne vrstice ostanejo lokalno in se neposredno prepišejo v rezultat. Številke se ne preračunavajo; ohranijo se tudi vodilne ničle in ponovljene številke.
- Vsaka besedilna vrstica dobi svoj ID. Odgovor mora vsebovati natanko en neprazen prevod na vsak ID. Manjkajoči, podvojeni in neznani ID-ji, dodatni prelomi ter izgubljene oznake oblikovanja povzročijo ponovitev zahteve oziroma napako datoteke.
- Ohranijo se vrstice posameznih govorcev, začetne govorne črtice, oznake HTML, oznake ASS in `\N`. Časovne oznake in številčenje se preverijo še po ponovnem branju končnega SRT.
- Neveljavna izvorna datoteka se zavrne z opisom napake. Aplikacija ne izvozi delno prevedenega SRT. To preverjanje zagotavlja strukturo; pomensko zvestobo strojnega prevoda je treba pregledati.
- Vhod podpira UTF-8 z BOM ali brez, UTF-16 z BOM in starejše angleške datoteke Windows-1252. Izhod je UTF-8, z ohranjenim tipom prelomov LF oziroma CRLF. Največja velikost posameznega SRT je 8 MB.
- Enaka izhodna imena v ZIP-u oziroma enem izvozu dobijo zaporedno pripono. Androidov ponudnik dokumentov pri shranjevanju v mapo ureja trke z že obstoječimi imeni.

## Ključ in podatki

Aplikacija se poveže neposredno z `https://api.openai.com/v1/chat/completions`; lasten strežnik ni potreben. Pošilja dialoge in njihove interne ID-je. API-ključ je šifriran z AES-GCM in Android Keystore. Ni v APK-ju, dnevnikih, izvornem repozitoriju ali varnostni kopiji aplikacije. Odstraniš ga z **Pozabi ključ**. Varnostno kopiranje aplikacije je izključeno.

Izbrane datoteke se kopirajo v zasebni prostor aplikacije; potrjeni prevodi in napredek ostanejo do novega izbora oziroma odstranitve aplikacije. Nov izbor zamenja staro čakalno vrsto po potrditvi. Izvožene datoteke ostanejo v izbrani mapi. Izvorni SRT-ji se ne spreminjajo. Android lahko dolgo delo prekine; aplikacija ga nadaljuje na zahtevo. Pri Androidu 15 in novejšem velja sistemska časovna omejitev storitve `dataSync`.

## Gradnja in preizkusi

Zahteve: JDK 21, Android SDK 35, internet za prvi prenos odvisnosti. Projekt vključuje Gradle Wrapper 8.9 in Android Gradle Plugin 8.7.3.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Za Windows uporabi `gradlew.bat`. Android Studio lahko odpre korensko mapo projekta in prenese SDK. Lokalni debug APK nastane v `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions izvaja enake preizkuse in shrani debug APK kot artefakt **SRT-Prevajalnik-GPT-debug**.

Za podpisano izdajo nastavi `SRT_KEYSTORE`, `SRT_KEYSTORE_PASSWORD` in uporabi ključ z aliasom `srt-release`, nato:

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Izdaja nastane v `app/build/outputs/apk/release/app-release.apk`. Brez nastavitve podpisnega ključa Gradle izdela nepodpisano izdajo. Podpisni ključ hrani ločeno: za nadgradnjo iste namestitve bo potreben isti ključ. Ključev ne dodajaj v repozitorij.

Preizkusi vključujejo obdelavo in izvoz 25 SRT z nadomestnim odgovorom API-ja ter Androidov vmesnik na Robolectric/Android 15. To ne nadomesti preverjanja dejanskega prevoda z uporabnikovim API-ključem in fizičnega preizkusa na Samsungu. Podrobnosti so v [TESTING.md](TESTING.md).
