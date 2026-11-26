# SRT Prevajalnik GPT – Android

Android aplikacija za prevajanje podnapisov in ustvarjanje podnapisov iz filmov. Podpira Android 13 ali novejši, vključno s Samsung Galaxy A57. Lasten strežnik ni potreben: uporabljaš svoj ključ OpenAI API.

**[Prenesi podpisani APK 1.1.2](downloads/SRT-Prevajalnik-GPT-1.1.2.apk?raw=true)** · [Kontrolne vsote SHA-256](downloads/SHA256SUMS.txt)

Različica 1.1.2 upošteva dejanski izhod zvočnega dekoderja pri večkanalnih posnetkih, tudi če se število kanalov ali PCM oblika razlikuje od izvorne sledi. Pri 4K filmu poskuša pridobiti zmanjšano sličico že pri dekodiranju. Če branje sličice ali zahteva OpenAI predolgo obstane, prikaže napako; potrjeni napredek ostane in delo je mogoče nadaljevati. Film in uporabniški ključ niso vključeni v preizkusne datoteke.

Posodobitev namesti čez prejšnjo različico, brez odstranitve aplikacije. Ohrani shranjeni ključ, izbor SRT in že potrjene prevode. Na voljo so trije zavihki:

- **SRT:** izbira do 25 datotek in prevod v slovenščino, angleščino, hrvaščino, srbščino ali nemščino. Stare čakalne vrste še naprej uporabljajo slovenščino.
- **Slika:** aplikacija bere podnapise, zapečene v sliko izbranega filma do 20 GB, in jih prevede v izbrani jezik. Pregleda eno sličico na pol sekunde, zato lahko kratke podnapise zgreši; časovne oznake so približne.
- **Govor:** aplikacija prepiše zvok iz filma do 20 GB in izdela slovenski SRT. Prepoznavanje vrne časovne oznake govornih odsekov, s prekrivanjem zvoka ob mejah zaradi konteksta; čas in prepis sta še vedno lahko netočna. Uporabi prvi zvočni posnetek.

Naenkrat se izvaja eno opravilo: če je dejaven drug zavihek, počakaj ali tam tapni **Ustavi**. Pri filmskih zavihkih preveri rezultat in časovno usklajenost. Podprti so formati, ki jih telefon zna prebrati in dekodirati. Dolgi filmi zahtevajo veliko zahtev API-ja in lahko povzročijo znatne stroške; aplikacija pred začetkom prikaže število zahtev. Začasni napredek se shrani in ga je mogoče nadaljevati, vendar zadnja nedokončana zahteva lahko nastane ponovno. Med delom izvorne datoteke ne spreminjaj. Film ostane na telefonu, izbrane sličice oziroma zvočni odseki pa se pošljejo OpenAI. Nov prepis govora uporablja model `gpt-4o-transcribe-diarize`, ki mora biti dostopen tvojemu računu API. Prej začeto govorno opravilo nadaljuje po starem postopku z modelom `gpt-transcribe`.

## Uporaba

1. Namesti podpisani APK na telefon. Ob namestitvi po potrebi dovoli nameščanje aplikacij za brskalnik oziroma upravitelja datotek, s katerim si odprl APK.
2. V aplikacijo vnesi svoj **OpenAI API-ključ** in tapni **Shrani ključ**. Ključ pridobiš na https://platform.openai.com/api-keys. API potrebuje svoje dobroimetje; naročnina ChatGPT ne vključuje porabe API-ja. Ključa ne objavljaj na GitHubu ali v pogovoru.
3. Izberi model: privzeti `gpt-4.1-mini` ali `gpt-4.1`.
4. V zavihku **SRT** izberi ciljni jezik in tapni **Izberi SRT datoteke (do 25)**. V Androidovem izbirniku pridrži prvo datoteko in označi druge. Izbor nad 25 je zavrnjen v celoti; obstoječi izbor ostane ohranjen.
5. Začni prevajanje. Aplikacija pokaže napredek po datotekah in vrsticah. Omogoči obvestila za prikaz napredka tudi zunaj aplikacije. Internet mora ostati na voljo.
6. Po potrebi tapni **Ustavi prevajanje**, nato **Nadaljuj / ponovi**. Že potrjeni paketi so shranjeni na telefonu. Zadnja nedokončana zahteva se lahko ponovi in povzroči dodatno porabo API-ja.
7. Tapni gumb za shranjevanje SRT v mapo in izberi izhodno mapo ali **Shrani vse prevedene kot ZIP** ter izberi ime in mesto shranjevanja. Izvoz vsebuje samo v celoti zaključene in preverjene prevode. Če so nekatere datoteke neuspešne, jih lahko ponovno obdelaš.

## Ohranitev podnapisov

- Številčenje in celotne časovne vrstice ostanejo lokalno in se neposredno prepišejo v rezultat. Številke se ne preračunavajo; ohranijo se tudi vodilne ničle in ponovljene številke.
- Vsaka besedilna vrstica dobi svoj ID. Končni rezultat mora vsebovati natanko en neprazen prevod na vsak ID. Veljavni odgovori se shranijo takoj; manjkajoče ali neveljavne vrstice se do trikrat obdelajo v vedno manjših zahtevah, nazadnje posamično s sosednjim besedilom za kontekst. Potrjene vrstice se ob tem ne pošiljajo ponovno. Podvojeni ali neznani ID-ji zavrnejo celoten dvoumen odgovor.
- ID-ji so obvezni ključi v strogi shemi odgovora, s katero API omeji generiranje; niso več poljubno besedilo v polju `id`. Dodatni ključi niso dovoljeni. Branje odgovora preverja podvojene ključe še pred sestavljanjem JSON-objekta, da jih Android ne bi tiho prepisal.
- Dodatni prelomi znotraj odgovora za isti ID se zamenjajo s presledki, brez izpuščanja besedila. Vrstice različnih izvornih ID-jev se nikoli ne združujejo. Prazen prevod ali manjkajoča oznaka oblikovanja še vedno prepreči izvoz nepopolne datoteke.
- Ohranijo se vrstice posameznih govorcev, začetne govorne črtice, oznake HTML, oznake ASS in `\N`. Časovne oznake in številčenje se preverijo še po ponovnem branju končnega SRT.
- Neveljavna izvorna datoteka se zavrne z opisom napake. Aplikacija ne izvozi delno prevedenega SRT. To preverjanje zagotavlja strukturo; pomensko zvestobo strojnega prevoda je treba pregledati.
- Vhod podpira UTF-8 z BOM ali brez, UTF-16 z BOM in starejše angleške datoteke Windows-1252. Izhod je UTF-8, z ohranjenim tipom prelomov LF oziroma CRLF. Največja velikost posameznega SRT je 8 MB.
- Enaka izhodna imena v ZIP-u oziroma enem izvozu dobijo zaporedno pripono. Androidov ponudnik dokumentov pri shranjevanju v mapo ureja trke z že obstoječimi imeni.

## Ključ in podatki

Aplikacija se poveže neposredno z OpenAI API; lasten strežnik ni potreben. Pri prevajanju pošilja dialoge in njihove interne ID-je, pri filmskih zavihkih pa tudi sličice oziroma zvočne odseke. API-ključ je šifriran z AES-GCM in Android Keystore. Ni v APK-ju, dnevnikih, izvornem repozitoriju ali varnostni kopiji aplikacije. Odstraniš ga z **Pozabi ključ**. Varnostno kopiranje aplikacije je izključeno.

Izbrane datoteke SRT se kopirajo v zasebni prostor aplikacije; filmi ostanejo na svojem mestu in aplikacija hrani dovoljenje za njihov dostop. Potrjeni prevodi in napredek ostanejo do novega izbora oziroma odstranitve aplikacije. Nov izbor zamenja staro čakalno vrsto po potrditvi. Izvožene datoteke ostanejo v izbrani mapi. Izvorni SRT-ji se ne spreminjajo. Android lahko dolgo delo prekine; aplikacija ga nadaljuje na zahtevo. Pri Androidu 15 in novejšem velja sistemska časovna omejitev storitve `dataSync`.

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

Preizkusi vključujejo obdelavo in izvoz 25 SRT, filmska postopka z nadomestnimi odgovori API-ja, Androidov vmesnik na Robolectric/Android 15 ter dekodiranje kratkega preskusnega filma na Android emulatorju. To ne nadomesti preverjanja dejanskega prevoda z uporabnikovim API-ključem in fizičnega preizkusa na Samsungu. Podrobnosti so v [TESTING.md](TESTING.md).
