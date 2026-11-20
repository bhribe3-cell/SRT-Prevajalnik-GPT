# Preverjanje izdaje 1.0.0

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
