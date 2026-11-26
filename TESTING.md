# Preverjanje izdaje 1.1.3

Uporabnik je prijavil zastoj pri 184/11818 potrjenih sličicah: dekoder ni prebral sličice pri 93500 ms. Uporabnikove datoteke in naprave nismo imeli na voljo. Novi regresijski preizkus s simulirano napako nadaljuje s 184 potrjenih sličic, ponovno prebere isto časovno točko po obnovitvi dekoderja in ob neuspehu preskusi sličico do 100 ms stran. Če noben poskus ne uspe, ne pošlje nepopolnega paketa in ne premakne potrjenega napredka. Ponovni poskus bližnje sličice lahko vpliva na zelo kratke dialoge, zato to ni zagotovilo popolnega prepoznavanja.

Uspešnih je vseh 88 enotskih preizkusov, lint je brez napak, APK je podpisan z istim ključem in ima `versionCode=7`. Android emulator je uspešno odprl preskusni video, prebral sličico, ponovno odprl dekoder in prebral naslednjo sličico; uspešen je bil tudi preizkus 4K HEVC. Sintetični primer ne dokazuje, da bo uporabnikova konkretna datoteka uspešno prebrana na njegovem Samsungu. Dejanski OpenAI API in ta film nista bila na voljo za preizkus.

# Preverjanje izdaje 1.1.2

Uporabnik je prijavil napako »Oblika dekodiranega zvoka ni podprta« pri datoteki MKV z zvočno sledjo DDP 5.1 ter zastoj branja podnapisov po 72 sličicah. Dejanska uporabnikova datoteka in telefon nista bila priložena. Prejšnja različica je zahtevala, da ima dekodirani zvok enako število kanalov in frekvenco kot stisnjena sled in da je PCM16; dekoder lahko namesto tega vrne spremenjeno obliko. Popravek uporablja dejansko obliko izhoda ter PCM8, PCM16, PCM24, PCM32 in PCM float pretvori v PCM16 WAV. Časovne oznake ostanejo v časovni osnovi filma.

Pripravljeni so sintetični preskusni posnetki: MKV z E-AC-3 5.1, MKV z AAC 5.1 in kratek 4K HEVC video. Android emulator API 35 je uspešno dekodiral 4K sličico in AAC 5.1, preizkus E-AC-3 pa je preskočen, ker emulator te zvočne sledi MKV ne ponudi. To ne potrjuje delovanja E-AC-3 na Samsungu. Omejitev čakanja na sličico je 60 sekund, za zahtevo API-ja tri minute; sličice se potrjujejo po paketih in staro stanje je združljivo. Stanje prikaže, ali aplikacija bere sličico ali čaka na OpenAI, ter pojasni onemogočene gumbe, če teče drugo opravilo. Samodejno ne ponavlja zahtev. Kadar se težava pojavi v vnovičnem preizkusu, sta za diagnozo pomembna stopnja in prikazano sporočilo.

Samodejni preizkusi: 84 uspešnih, brez neuspešnih ali preskočenih. Testirani so različni izhodni formati PCM, pretvorba šestih kanalov, meje čakanja, preklic in prikaz zasedenega drugega zavihka. Android lint brez napak; gradnja APK uporablja `versionCode=6` in podpis prejšnjih izdaj. Uporabnikov API-ključ in fizični Samsung nista bila na voljo.

# Preverjanje izdaje 1.1.1

Zavihek za govor pri novih opravilih uporablja model `gpt-4o-transcribe-diarize` in časovne oznake odsekov iz `diarized_json`. Posamezne zahteve vključujejo še zvok okoli meje odseka; podnapis se razporedi glede na sredino časovno označenega govora. Prekrivajoči se govorci se ohranijo v večvrstičnem podnapisu. Časovne oznake in prepoznava so kljub temu ocene, prehod na meji odsekov pa lahko povzroči netočnosti ali podvojitve. Opravila, začeta z različico 1.1.0, nadaljujejo po starem postopku; predpomnilnik in obstoječa čakalna vrsta SRT se ne spreminjata.

Gradnja `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` in podpisana izdaja sta uspešni, vseh 78 enotskih preizkusov je uspešnih. Preizkusi preverjajo pretvorbo časov API-ja, glasove s prekrivanjem, meje odsekov, preprečevanje izvoza neveljavnih časov, mono mešanje kanalov, nadaljevanje po prekinitvi in združljivost shranjenih opravil. Uporabljajo nadomestne odgovore API-ja. Lint ni poročal o napakah. APK ima `versionCode=5` in enak podpis kot 1.1.0. Plačljivega dejanskega klica API in fizičnega Samsunga nismo imeli na voljo.

# Preverjanje izdaje 1.1.0

Samodejni preizkusi: 71 uspešnih, brez neuspešnih ali preskočenih. `lintDebug` je uspešen brez napak, z opozorili. Preizkusi pokrivajo izbiro petih ciljnih jezikov, združljivost stare čakalne vrste, omejitev 25 SRT, shranjevanje in nadaljevanje filmskih nalog, preverjanje odgovorov, časovnice, sestavljanje podnapisov, branje zvoka in zavrnitev prevelikih datotek. Omejitev 20 GB je preizkušena s sintetično redko datoteko, ne z dejanskim 20 GB filmom.

Na Android emulatorju API 35 je izveden preskus z majhnim ustvarjenim MP4: branje sličice in dekodiranje zvoka ter potek do SRT. Odgovori storitve OpenAI so nadomestni. Plačljivi klici API-ja in fizična naprava Samsung Galaxy A57 niso bili na voljo za preizkus. Prepoznava vsebine in časovne oznake filmskih podnapisov so približne; preveriti jih je treba na dejanskem filmu. Izdaja uporablja isti paket in podpisni certifikat kot različica 1.0.2 ter višji `versionCode=4`.

# Preverjanje izdaje 1.0.2

Popravek z dne 21. novembra 2026 obravnava prijavljeno ustavitev pri `c270_l0` po 440 od 1032 vrsticah zaradi napačnega ali podvojenega ID-ja. Prejšnja shema je omejila obliko odgovora, ID pa je bil prosto besedilo. Nova shema za vsako zahtevo določi natanko zahtevane ID-je kot obvezne ključe, vsak s prevodom kot vrednostjo; dodatni ključi so prepovedani.

Novi preizkusi preverjajo pogodbo odgovora s ključem `c270_l0`, napačne zapise (`c270_10`, `c270_I0`), podvojene surove JSON-ključe, vrstni red dialogov, prazne ali nebesedilne vrednosti, neveljavno ovojnico, delne odgovore ter nadaljevanje dokumenta s 1032 vrsticami iz 440 potrjenih vrstic. Podvojeni ključi se dodatno preverijo v Androidovem okolju Robolectric, kjer bi jih običajni `JSONObject` prepisal. Nobena napačno označena vrstica se ne preslika po položaju ali samodejno preimenuje.

Paket in podpisni certifikat ostaneta enaka kot v 1.0.0 in 1.0.1, `versionCode` je 3. Shranjeni ključ, predpomnilnik, izbrane datoteke in končani prevodi uporabljajo isti format. Preizkusi uporabljajo nadomestne odgovore API-ja in ustvarjen SRT, ne uporabnikove celotne datoteke.

Rezultat: **37/37 uspešnih preizkusov**, brez preskočenih, `lintDebug` brez napak ali opozoril in uspešna gradnja podpisanega APK-ja. Preizkus, ki zahteva natančno določene ID-je v shemi, je na ločeni kopiji 1.0.1 padel (`object` namesto starega `array`), z novim formatom pa uspe.

## Preverjanje izdaje 1.0.1

Popravek z dne 20. novembra 2026 obravnava prijavljeno ustavitev pri `c40_l1`, ko je bilo od 2126 vrstic potrjenih 41. Preizkus z dodatnim prelomom v odgovoru za en ID je pred popravkom padel. Novi preizkusi preverjajo tudi shranitev veljavnega dela odgovora, posamično popravljanje prazne ali izpuščene vrstice s kontekstom, nadaljevanje po prekinitvi ter končni SRT z 2126 vrsticami. Izvorni blok 41 iz uporabnikove datoteke ni bil priložen; primer s tem ID-jem je ponovljen z nadomestnimi odgovori API-ja.

Različica uporablja isto ime paketa in isti podpisni ključ kot 1.0.0 ter višji `versionCode=2`. Format predpomnilnika in Android Keystore se ne spreminjata. Nadgradnja ohrani uporabnikov ključ, izbrane datoteke in potrjene vrstice.

Končni rezultat: **29/29 uspešnih samodejnih preizkusov**, `lintDebug` brez opozoril ali napak ter uspešna gradnja podpisanega APK-ja. Deset novih preizkusov posebej obravnava dodatne prelome, prazne ali izpuščene vrstice, kontekst čez mejo predpomnilnika, trajno neveljaven odgovor brez delnega izvoza, prekinitev med popravljanjem, napačne ID-je, napako prijave in dokument z 2126 vrsticami.

## Preverjanje prvotne izdaje 1.0.0

Preverjeno 16. novembra 2026 v oblačnem Linux okolju z JDK 21 in Android SDK 35. Izhodiščni repozitorij: commit `d7998966dddb00d4c6ba40c7c226c02ea9d2c6de`; vseboval je samo README in tri nastavitvene datoteke Gradle, brez modula `app` ali strežnika.

## Samodejni preizkusi

`testDebugUnitTest`: **19 uspešnih preizkusov**, brez preskočenih preizkusov.

- Nespremenjene časovne vrstice, številčenje z vodilnimi ničlami, ponovljene številke, število blokov in vse vrstice večgovornih dialogov.
- Ohranitev HTML, ASS, `\N`, govornih črtic ter vrstic z glasbenimi simboli in ločili.
- Zavrnitev manjkajočih, podvojenih in neznanih ID-jev; praznega besedila, dodatnih prelomov in spremenjenega oblikovanja.
- Zavrnitev neveljavnega SRT in neveljavnih časov brez tihega izpuščanja blokov.
- Branje UTF-8/BOM, UTF-16/BOM in Windows-1252.
- Prekinitev in nadaljevanje dokumenta s 125 podnapisi: že potrjenih 40 vrstic se ne pošlje ponovno.
- Prekinitev pred zahtevo prepreči klic API-ja; neveljaven predpomnilnik prepreči izvoz.
- Izbor 1 oziroma 25 datotek uspe; 26 datotek se zavrne in prejšnja čakalna vrsta ostane ohranjena.
- Androidova aktivnost v Robolectric/Android 15 uvozi 25 datotek prek ponudnika vsebine, jih obdela z nadomestnim odgovorom API-ja, shrani ZIP prek izbirnika in ponovno prebere vseh 25 slovenskih SRT iz ZIP-a.
- Androidova aktivnost izvozi posamezne SRT prek `DocumentsProvider` v izbrano mapo; shranjena besedila se ponovno preberejo in preverijo.
- Neveljaven nov izbor in preklican izbirnik ohranita prejšnje podatke. Ponovna ustvaritev aktivnosti ohrani uvožene datoteke.
- Imena za izvoz se očistijo in razločijo, da ZIP ne vsebuje podvojenih poti ali poti izven arhiva.

Preizkusi API-ja uporabljajo nadomestne odgovore in ne porabijo dobroimetja. Preverjajo sestavo zahteve, obdelavo odgovorov, potek in celovitost, ne kakovosti prevajanja modela.

## Gradnja

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Za podpisano izdajo sta potrebni spremenljivki `SRT_KEYSTORE` in `SRT_KEYSTORE_PASSWORD`; alias ključa je `srt-release`. Podpisni ključ in geslo nista del repozitorija. APK je samostojna namestitvena datoteka za Android 13+ z `targetSdk=35` in brez izvornih knjižnic, omejenih na eno arhitekturo.

Končna gradnja je uspela. `lintDebug` je uspešen brez napak ali opozoril. Podpis in poravnava izdaje se dodatno preverita z orodjema Android SDK `apksigner verify` in `zipalign -c`.

## Preverjanje na telefonu

Fizični Samsung Galaxy A57 ni bil povezan s tem okoljem. Prav tako ni bil na voljo uporabnikov OpenAI API-ključ, zato dejanski plačljivi prevod še ni bil izveden. Android Keystore, obvestila in Samsungovo upravljanje procesov potrebujejo tudi preizkus na napravi.

Prvi preizkus po namestitvi:

1. Shrani svoj API-ključ in izberi en kratek angleški SRT.
2. Prevedi ga, shrani v mapo Prenosi in odpri rezultat. Primerjaj število podnapisov, številke in časovne vrstice z izvirnikom ter preglej slovenski prevod.
3. Izberi 25 datotek, med prevajanjem zamenjaj aplikacijo oziroma zavrti telefon, nato preveri napredek in ZIP.
4. Ustavi in nadaljuj; že potrjene vrstice morajo ostati ohranjene. Ob prekinitvi omrežja preveri opis napake in ponoven poskus.
