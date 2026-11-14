# Regeln für diesen Code

Diese Regeln gelten für jede Änderung an der Wallet. Die Kommentare im
Quelltext verweisen mit ihrer Nummer auf sie („Regel 19").

Jede steht hier aus einem von zwei Gründen: Ihr Bruch würde Geld kosten
oder das Vertrauen in die Wallet – oder der Fehler ist schon einmal
passiert. Wo ein Datum steht, ist es der Tag der Entscheidung oder der
Messung.

Einige Regeln nennen `bilder\`, `UMBAU.md` und `design-neu\`. Das sind
Bildschirmbilder und Arbeitsunterlagen, die nicht in diesem Repository
liegen.

1. **Das Paket `com.yskar.wallet.kern` benutzt keine Android-API.** Nur
   `java.*`, `javax.crypto` und BouncyCastle. Sonst laufen die Unittests nicht
   mehr auf der JVM, und genau das macht sie schnell genug, um sie wirklich
   bei jeder Änderung laufen zu lassen.
2. **Jede Änderung am Kern muss gegen die Vektoren grün bleiben.** Sie liegen
   in `app/src/test/resources/vergleichswerte.json` und stammen aus der
   laufenden Wallet. Schlägt ein Vektor fehl, ist der Port falsch — nicht
   der Vektor.
3. **Echte Merkwörter gehören NIE in den Quellcode**, auch nicht
   vorübergehend zum Testen. Im Selbsttest steht der öffentliche
   BIP39-Vektor `abandon … about`; dahinter liegt kein Guthaben.
4. **`Tx.verifyOwnSignature` darf keine fremden Signaturen bewerten.** Der
   Konsens prüft nach ZIP-215 (mit Kofaktor), BouncyCastle nach RFC 8032.
   In Grenzfällen entscheiden beide unterschiedlich. Ohne echten
   ZIP-215-Prüfer bleibt diese Entscheidung beim Knoten.
5. **Beträge immer ganzzahlig.** `Long`, nie `Double`. Fließkomma im
   Konsens ist ein Fehler mit Ansage.
6. Kein Testnetz erreichbar. Der erste echte Versand geht mit
   Kleinstbetrag an eine zweite eigene Adresse — und erst, wenn alles
   andere steht.

7. **Der Tresor läuft nie ohne Pfeffer.** Der Tresorschlüssel entsteht aus PIN
   UND einem nicht auslesbaren Keystore-Schlüssel. Wer diese Bindung löst,
   macht aus sechs Ziffern wieder das, was die Vorlage selbst als in Stunden
   knackbar beschreibt.
8. **Keine Selbstzerstörung nach n Fehlversuchen.** Nur Wartezeiten. Ein
   Löschen träfe fast immer den Besitzer, nicht den Angreifer.
9. **Nichts Geheimes ins Log.** `Selbsttest.protokolliere` schreibt nur
   Testwerte und öffentliche Adressen. Abschnitt 3 des Selbsttests liest
   seit dem 11.11.2026 das Konto der EIGENEN Wallet; diese Zeilen sind als
   `eigen` gekennzeichnet und stehen auf dem Bildschirm, aber nicht im Log.

10. **Netzantworten nie nach den TypeScript-Typen parsen.** Die echte API
    schickt `balance` als Zahl, `nextNonce` daneben als Zeichenkette und
    `memo` als Hex. Mitschnitte liegen in `app/src/test/resources/netz/`
    (Form echt; die Werte zu einem Konto sind erfunden, Regel 42).
11. **Beträge nie über Gleitkomma und nie aufrunden.** `Long` in kleinsten
    Einheiten, abgeschnitten: 0,99999999 YSR ist nicht 1,00.
12. **Die Knotenadresse aus der 410-Antwort wird geprüft, bevor sie gilt.**
    Sie bestimmt, wohin signierte Überweisungen gehen.

13. **Entsperrt heißt nicht: Schlüssel im Speicher.** Nach dem Entsperren
    werden die zwölf Wörter sofort überschrieben; die App behält nur die
    öffentliche Adresse. Zum Unterschreiben wird erneut entsperrt.
14. **`FLAG_SECURE` auf jedem Bildschirm, der die Wörter zeigt**
    (`BildschirmSchutz()`), und nur dort — mit einer Ausnahme vor
    Android 13, wo der Hintergrund denselben Schalter braucht (Regel 35).
    Die vier Seiten sind zweigeteilt:
    `…Screen` setzt den Schutz und ist das, was die App aufruft; `…Bild`
    ist nur das Aussehen, `internal`, und wird außer vom Screen allein vom
    Testrahmen `Bildschirmbilder` gezeichnet — mit dem öffentlichen
    BIP39-Vektor. **Ein `…Bild` nie von anderswo aufrufen.** `SchutzTest`
    prüft auf dem Gerät, dass jeder der vier Screens das Fenster sperrt und
    wieder freigibt; er fasst den Tresor nicht an und läuft wie
    `SendenOberflaecheTest` über `adb shell am instrument`.
15. **PIN nur über das eigene Ziffernfeld**, nie über die Systemtastatur.
16. **Angezeigte Texte mit richtigen Umlauten.** Kommentare bleiben ASCII,
    Textliterale nicht. PowerShell eignet sich NICHT zum Ersetzen von
    Umlauten in Quelldateien — es hat hier mehrfach Zeichenketten
    abgeschnitten; dafür den Editor benutzen.
17. **Oberflächentests laufen nur auf dem Entwicklungsgerät.**
    `OberflaecheTest` löscht den Tresor, bevor er startet — also **nicht**
    laufen lassen, solange eine echte Wallet auf dem Gerät liegt.
    `Bildschirmbilder` ist davon ausgenommen: Es fasst den Tresor nicht an,
    sondern zeichnet jeden Bildschirm mit Beispieldaten und legt PNGs unter
    `/sdcard/Android/data/com.yskar.wallet.debug/files/bilder` ab. Das ist
    der einzige Weg, die Bildschirme zu beurteilen — HyperOS verweigert
    `adb shell input` mit `SecurityException: INJECT_EVENTS`, von außen ist
    in der App nichts zu bedienen.

    Zwei Bedingungen für den Lauf: Das Telefon muss **wach und entsperrt**
    sein (schläft es, scheitert jede Aufnahme mit „No compose hierarchies
    found"), und für `scanner` muss die Kameraerlaubnis schon erteilt sein.
    Beides muss der Besitzer selbst tun — das Script kann es nicht.
    `adb shell pm grant … CAMERA` scheitert auf HyperOS mit
    `SecurityException: GRANT_RUNTIME_PERMISSIONS` — also einmal von Hand
    am Gerät erlauben, danach bleibt sie.

    Den Rest des Ablaufs erledigt `.\werkzeug\bilder-holen.ps1`: bauen,
    installieren, zeichnen, holen und prüfen, dass nichts im Querformat
    zurückkam. Die Bilder gehören **ins Projekt**
    (`YskarWallet\bilder\`, mit `LIESMICH.md` daneben), nicht in einen
    Temp-Ordner — eine Behauptung über die Oberfläche ohne das Bild, an
    dem sie gemessen wurde, ist später nicht mehr nachzuprüfen.

18. **Kein Test spricht mit dem echten Netz, und keiner sendet.** Der
    Transport ist im Test eine Attrappe (`Briefkasten`), die die Bytes
    festhält, statt sie wegzuschicken.
19. **Was im Prüfschritt steht, wird unterschrieben.** Der Entwurf wandert
    unverändert von `Senden.pruefe` bis `unterschreibenUndSenden`. Wer dort
    etwas nachrechnet oder umformt, bricht das Versprechen.
20. **Nie „erledigt" schreiben, wenn der Knoten nur „angenommen" sagt.**
    Das ist Schritt 2 von fünf; Reorganisationen sind nicht ausgeschlossen.
21. **Vor dem Absenden drei Gegenproben**: abgeleitete Adresse gleich
    angezeigter, eigene Signatur gültig, echte Größe nicht größer als die
    berechnete. Jede davon bricht ab, statt zu senden.

22. **Ein gescannter QR-Code ist kein Beweis.** Jeder kann einen drucken.
    Was nicht `Address.isValid` besteht, wird nicht ins Empfängerfeld
    geschrieben. Ein Betrag im Code (`yskar:ysr1…?amount=…`) wird
    **verworfen, nicht ausgewertet** — wie viel gesendet wird, entscheidet
    der Besitzer am Gerät.
23. **Der Scanner braucht keinen fremden Dienst.** CameraX liefert die
    Bilder, der ZXing-Kern entziffert sie auf dem Gerät. Kein ML Kit, kein
    Google-Code-Scanner, keine Play Services. Nichts wird gespeichert, und
    die Kameraerlaubnis wird erst beim Öffnen des Scanners gefragt.

24. **Kein Push, und das ist Absicht.** Firebase hieße: ein Server kennt
    die Adresse dieses Geräts und beobachtet sie. Stattdessen wacht das
    Gerät selbst auf (`Waechterdienst`, WorkManager, 15 min) und fragt
    denselben Spiegel wie die App. Der Wächter braucht nur die **Adresse**
    aus der Tresorakte — kein Tresor, keine PIN, kein Wort. Er ist **aus**,
    bis der Besitzer ihn einschaltet: Er kostet Akku und fragt regelmäßig
    nach genau dieser Adresse.
25. **Was gemeldet wird, rechnet `Waechter.pruefe` aus** — reine Funktion,
    ohne Android-API, damit es Unittests gibt. Der Schlüssel eines Eintrags
    ist `txid:idx:art`, nicht die txid: Eine Überweisung an sich selbst
    steht zweimal im Verlauf. Und der erste Durchgang meldet **nichts**,
    sonst kommt beim Einschalten der ganze Verlauf als Mitteilung.
    **Ein Tipp auf die Mitteilung öffnet genau diesen Eintrag** – nach dem
    Entsperren, wie ein Anmelde-Link (`ui/Mitteilungsziel.kt`, rein und in
    `MitteilungszielTest` getestet). Die Absicht trägt nur den Schlüssel;
    sie ist kein Beweis (MainActivity ist exportiert): streng geprüft,
    gezeigt nur, was im geladenen Verlauf der eigenen Wallet steht, und
    nie mitten aus Senden, Signieren oder den zwölf Wörtern heraus.
26. **Automatisch heißt leise.** Der Vordergrundtakt (30 s, nach dem Senden
    10 s) setzt den Knopf nicht auf „lädt …", meldet kein Funkloch und
    wirft die nachgeladenen Verlaufsseiten nicht weg. Die Schleife hängt an
    `repeatOnLifecycle(RESUMED)` — nie im Hintergrund.
27. **Der Bildschirm zum Unterschreiben zeigt die Zahlen.** Ein Ziffernfeld
    ohne Betrag, Gebühr, Gesamt und Empfänger daneben ist eine
    Blankounterschrift. Biometrie und PIN laufen dort durch dieselbe
    Funktion (`WalletModel.unterschreiben`), damit sie nicht auseinander
    driften.
28. **`FLAG_SECURE` gilt auch für den Bildschirm unter „Einstellungen"**, der die
    zwölf Wörter noch einmal zeigt. Davor steht PIN oder Finger, danach
    wird die Ansicht verworfen.

29. **Kein Sendebildschirm darf rollen müssen.** Gefragt wird der
    Bildschirm selbst, nicht das Bild: `Bildschirmbilder` schreibt je
    Aufnahme nach `bilder\rollen.txt`, wie weit sich der Bildschirm rollen
    lässt (Weg) und wie weit das tiefste Element mit Text oder Knopf
    unter den sichtbaren Bereich ragt (Überhang). **Nur ein Überhang
    heißt, dass jemand rollen muss**; ein Weg ohne Überhang ist Luft am
    Ende. `werkzeug\bilder-holen.ps1` zeigt das Ergebnis an. Am
    Bildschirmfoto ließ sich die Frage nie entscheiden — ein Bildschirm
    mit fest stehendem Fuß reicht immer bis unten. Der Testrahmen
    zeichnet seit dem 10.11.2026 durch dieselbe Funktion wie die App
    (`Buehne`), mit den Rändern für Status- und Navigationsleiste; wer
    daran etwas ändert, macht die Probe wertlos. Geprüft wird auch mit
    Systemschrift 1,3 — und seit dem 11.11.2026 bestehen alle
    Sendebildschirme auch das, deutsch wie englisch. Der Weg dorthin war
    nicht kleinere Schrift, sondern KEIN UMBRUCH: Über 1,15 hält der
    Prüfschritt die Adresse in zwei Zeilen und jeden Wert neben seiner
    Beschriftung (`einpassen`), und die Namen der fünf Schritte bleiben
    einzeilig. Wer dort einen Text verlängert oder eine Zeile ergänzt,
    sieht in `rollen.txt` nach. Gekürzt wird die Form, nie die Wahrheit
    — Beträge, Adresse, Kennung und alle fünf Schritte bleiben vollständig.
    Der Rollbalken bleibt als Netz für größere Schrift und kleinere
    Geräte: Niemand soll rollen MÜSSEN, nicht niemand rollen KÖNNEN. Im
    `Seite`-Rahmen rollt nur die Mitte, der Hauptknopf steht fest am Fuß.
30. **Der QR-Code bekommt seine Ruhezone von der weißen Karte**, nicht vom
    Bild. `MARGIN = 0` und `encode(…, 0, 0, …)` liefern die Matrix in
    Modulauflösung; vergrößert wird in Compose mit `fillMaxSize`,
    `ContentScale.FillBounds` und `FilterQuality.None`. Mit `fillMaxWidth`
    und `Fit` rechnet Compose auf die kleinere Seite — der Code stand dann
    als Briefmarke mitten auf der Karte. **Die Ruhezone sind vier Module**,
    wie die Norm verlangt — gerechnet in Modulen, nicht in dp
    (`QrCode.RUHEZONE`, `QrKarte`). Die früheren 22 dp waren genau das,
    stimmten aber nur für eine Modulgröße. Nachgemessen mit
    `werkzeug\messen.ps1 bilder\06_empfangen.png -QrFuellung`: Karte 864,
    Code 703 Bildpunkte (37 Module zu je 19), Ruhezone 80 bis 81 auf allen
    vier Seiten.
31. **Das Y in der Mitte des QR-Codes steht unter Vorbehalt.** Der Code
    wird dafür mit der höchsten Fehlerkorrektur geschrieben (H); das
    Fenster nimmt 22 % der Kante. `QrCodeTest` belegt, dass der Leser der
    App den Code mit zerstörtem Fenster entziffert — auch mit einem
    deutlich größeren — und dass sich das Bildschirmfoto vom Gerät lesen
    lässt. Das ersetzt nicht die Probe mit einem zweiten Telefon. Liest
    es den Code nicht zuverlässig, kommt das Zeichen heraus; wer das
    Fenster vergrößert oder die Fehlerkorrektur senkt, lässt vorher den
    Test laufen.

32. **Der Guthabenverlauf schätzt nichts.** `netz/Guthabenverlauf.kt`
    rechnet rückwärts vom heutigen Guthaben: Ein Ausgang kostet Betrag
    **plus** Gebühr, ein Eingang bringt den Betrag — die Gebühr, die der
    Spiegel auch an Eingängen nennt, hat der Absender bezahlt. Gemessen am
    10.11.2026 am vollständigen Verlauf eines echten Kontos: Nur so kam die
    Rechnung bei genau 0 an. Die Testdaten unter
    `test/resources/netz/vollstaendig` sind danach gebaut, mit erfundenen
    Werten (1068 Bewegungen); der Test hält die Regel fest, er beweist sie
    nicht mehr. Wo sie
    nicht weiterkommt (Seiten fehlen, Art unbekannt, keine Zeit), hört sie
    auf und sagt es; geht sie nicht auf, wird **keine** Linie gezeichnet.
    Sie verlässt sich darauf, dass der geladene Verlauf **lückenlos** ist.
    Dafür sorgt `Verlaufslader.erneuert` — wer in `kontoLaden` anders mit
    den nachgeladenen Seiten umgeht, lässt vorher `VerlaufsladerTest`
    laufen. Der Spiegel gibt höchstens 200 Bewegungen je Anfrage, auch
    wenn mehr verlangt wird. Nichts aus dieser Rechnung erreicht
    `Senden.pruefe`.

33. **Der Schriftzug auf dem Startbildschirm ist ein Bild, kein Text.**
    Android erlaubt dort ab Version 12 ein Markenbild von 200 × 80 dp.
    `res/drawable-xxxhdpi/marke_start.png` hat der Gerätetest
    `Schriftzugbild` in Hanken Grotesk 600 gezeichnet; ändert sich Schrift oder
    Vorlage, erzeugt er es neu. `res/values-v31/themes.xml` ERSETZT
    `Theme.Start` aus `res/values/themes.xml` — wer eine der beiden
    Dateien ändert, ändert beide. `StartbildTest` prüft auf dem Gerät, dass
    sich das Thema auflöst, ohne die App zu starten. Wie der
    Startbildschirm aussieht, zeigt kein Test und kein Bild.

34. **Wiederherstellen nimmt genau zwölf Wörter** (Entscheidung des
    Besitzers, 11.11.2026). Zwölf Felder, eines je Wort
    (`ui/Merkwortfelder.kt`, rein und getestet), und
    `WalletModel.woerterUebernehmen` lehnt jede andere Zahl ab. Diese App
    und die Vorlage legen nur Wallets mit zwölf Wörtern an. `kern/Bip39.kt`
    kann längere Wortfolgen weiterhin — das bleibt so, es wird nur nicht
    mehr danach gefragt.

35. **Im Hintergrund ist die Wallet gesperrt und nicht zu erkennen**
    (Wunsch des Besitzers, 11.11.2026). Drei Dinge gehören zusammen
    (`ui/Hintergrundschutz.kt`): Bei `ON_STOP` sperrt das Modell; die
    App-Übersicht bekommt ab Android 13 kein Vorschaubild
    (`setRecentsScreenshotEnabled(false)`); und solange die App nicht vorn
    ist, liegt ein Vorhang über dem Inhalt (`Sichtschutz`).
    **Durchgesetzt wird die Sperre in `WalletModel.setze`, nicht beim
    Ereignis:** Arbeit läuft weiter, während die App draußen ist — eine
    Überweisung auf dem Weg zum Knoten kommt zurück und will ihren Status
    zeigen. Jede Zustandsänderung geht darum durch `setze`, und im
    Hintergrund wird dort aus jeder offenen Ansicht die Sperre. Wer am
    Zustand vorbei schreibt (`_stand.value = …`), öffnet die Wallet im
    Hintergrund. **Und aus der Sperre führt nur das Entsperren:** Dieselbe
    Arbeit kann auch erst fertig werden, wenn die App schon wieder vorn
    ist (PIN zum Senden eingegeben, kurz weg, zurück, bevor der Knoten
    antwortet). Darum verlässt eine Änderung die Sperre nur mit
    `setze(entsperrt = true)`, und das ruft allein `verarbeiteOeffnen`.
    Diese Lücke stand einen halben Tag offen; gefunden hat sie die
    Durchsicht, nicht der Test. Was gesperrt wird und was danach wiederkommt, rechnet
    `Hintergrundregel` aus (rein, `HintergrundregelTest`): Aufgehoben wird
    **nur** der Status einer abgeschickten Überweisung und die Einführung
    gleich nach dem Einrichten; Entwurf und zwölf Wörter werden verworfen. Ohne Wallet (beim Einrichten) wird nicht
    gesperrt — es gäbe keine PIN, zu der man zurückkehrt.
    `ON_STOP`, nicht `ON_PAUSE`: Die Frage des Systems nach Finger oder
    Erlaubnis hält die App nur an; dabei darf sie nicht sperren.
    `MainActivity` sammelt den Zustand mit `collectAsState`, **nicht**
    `…WithLifecycle` — sonst zeichnet das erste Bild nach der Rückkehr noch
    den offenen Bildschirm. `FLAG_SECURE` setzt niemand mehr direkt, nur
    über `Fensterschutz`: Der Schalter hat zwei Gründe (die zwölf Wörter;
    vor Android 13 der Hintergrund), und keiner darf dem anderen den Schutz
    nehmen. `HintergrundTest` prüft Vorhang, Lebenslauf und Fenster auf
    dem Gerät. **Was in der App-Übersicht wirklich zu sehen ist, zeigt kein
    Test.**

36. **Angezeigte Texte stehen in `sprache/`, nicht im Bildschirm.** Die App
    spricht Deutsch und Englisch; gewählt wird unter „Einstellungen" (und
    auf dem Einstieg), ohne Neustart, unabhängig von der Sprache des
    Telefons. Je Bereich ein Interface mit `…De` und `…En` — ein Text, der
    in einer Sprache fehlt, ist ein **Baufehler**. Bildschirme lesen
    `LocalTexte.current`; reine Funktionen bekommen `t: Texte` als letzten
    Parameter, **ohne Vorgabewert**. Zahlen und Daten folgen `t.locale`:
    jeder Aufruf von `Betrag.genau/stellen/anzeige/…` und `betragText` im
    Oberflächencode gibt sie mit — die Vorgabe dort ist Deutsch und fiele
    auf Englisch nicht auf, bis jemand „1.284,57" liest. Keine
    `strings.xml` außer `app_name`. `netz/Senden.kt` meldet weiter auf
    Deutsch und bleibt unberührt; `Sendemeldungen` übersetzt beim
    Anzeigen — wer dort einen Satz ändert, ändert ihn in
    `Sendemeldungen.kt` mit (`SendemeldungenTest` lässt `Senden.kt` seine
    Sätze selbst sagen und merkt es). `TexteTest` prüft alle Texte beider
    Sprachen auf einmal. **Die Oberflächentests suchen deutsche Wörter**;
    ohne `LocalTexte` ist die Sprache Deutsch. Regel 16 gilt weiter — für
    die deutschen Fassungen in `sprache/`.

37. **Im Betragsfeld steht immer das Dezimalzeichen der Sprache**
    (`betragImFeld`): auf Deutsch das Komma, auf Englisch der Punkt; das
    andere Zeichen wird beim Tippen zu diesem. `Betrag.ausEingabe` liest
    beide als Dezimalzeichen und kennt keine Tausendertrenner — „1.500"
    wäre anderthalb. So sieht man es im Feld, nicht erst im Prüfschritt.
    Welche Taste die Zifferntastatur anbietet, hängt an der Sprache des
    Telefons, nicht der App; darum schreiben beide Tasten das
    Dezimalzeichen. Kommas auf Englisch einfach zu verwerfen, sperrt auf
    einem deutschen Telefon jede Nachkommastelle aus.

38. **In den Explorer führt nur ein Tipp, und nur eine geprüfte Adresse.**
    `ui/Explorer.kt` bildet `…/explorer#tx-<Kennung>`, `#adr-<Adresse>`,
    `#block-<Höhe>` (an der Seite selbst nachgesehen, 11.11.2026). Eine
    Kennung ist genau 64 kleine Hex-Zeichen, eine Adresse besteht
    `Address.isValid` — sonst null, und dann gibt es keinen Knopf. Die
    Kennung kommt vom Spiegel; ohne diese Prüfung könnte eine Antwort des
    Netzes einen Verweis formen, der woandershin führt. Der Browser ist
    ein fremdes Programm: Die Wallet sperrt sich dabei (Regel 35), und
    von selbst wird der Explorer nie aufgerufen — er liegt unter einem
    anderen Rechnernamen als der Spiegel. Auf den Sendebildschirmen
    stehen Kopieren und Explorer UNTEREINANDER neben der Kennung:
    Nebeneinander bricht sie bei großer Schrift um, und der Status wird
    höher (Regel 29).

39. **Signiert wird nur, was mit `YSKAR Signed Message` beginnt — und
    geprüft wird unmittelbar vor dem Unterschreiben.** Das Format kommt
    aus der bestehenden Wallet (`reference/…/src/wallet/nachricht.ts`),
    nicht von hier; `signatur/Nachricht.kt` ist ihr Port, und
    `NachrichtTest`/`AnmeldecodeTest` sind die Fälle der Vorlage. Die
    Kopfzeile ist der Grund, warum eine Signatur nie eine Überweisung
    sein kann; Rohdaten (Hex) werden nicht signiert. Anmeldungen gibt es
    nur für `yskar.app` und Subdomains (`ERLAUBTE_HOSTS`), nur für die
    eigene Adresse, höchstens 15 Minuten gültig. **Der Bildschirm
    entscheidet nicht, was signiert wird:** Er reicht die ROHE Eingabe
    ans Modell, und `signatur/Unterschrift.kt` prüft sie mit der Uhr
    von jetzt — so wird eine Anfrage, die während der PIN-Eingabe
    abläuft, nicht mehr signiert. Drei Gegenproben wie in Regel 21.
    **Der Rückruf bestimmt, wohin eine Signatur geht:** nur https, Host
    genau die Domain der Nachricht, geprüft in `Nachricht` UND noch
    einmal in `Rueckruf.senden`, strenger als die Vorlage (kein Port,
    kein `#`, kein Rückstrich). Ein POST, keine Weiterleitung. **Ein
    Link ist kein Beweis:** `yskar:login…` führt nur dazu, dass die
    Anfrage GEZEIGT wird, nach dem Entsperren; unterschrieben wird erst
    nach einer weiteren PIN. Kein Test spricht dabei mit dem Netz —
    die Plattform ist im Test nachgebaut (Regel 18).

40. **Einfach zuerst, Einzelheiten auf Wunsch** (Wunsch des Besitzers,
    12.11.2026: Die Wallet darf nicht technisch wirken; Anfänger wie
    Profis sollen sich zurechtfinden). Hauptbildschirm, Prüfen, der
    Bildschirm nach dem Senden und die einzelne Zahlung zeigen nur, was
    man zum Verstehen und Entscheiden braucht. Blocknummer, Platz im
    Block, Kennung, laufende Nummer, Fassung, Größe, „danach verfügbar",
    die fünf Schritte des Netzes und die drei Schilder des Zeichens
    stehen hinter `Einzelheiten` (`ui/Teile.kt`) – **vollständig, nichts
    ist weggefallen**. **Nie hinter „Einzelheiten":** Betrag, die ganze
    Adresse, Gebühr, Summe – das sind die Dinge, die man prüft (Regeln
    19, 21, 27). Beträge in Übersichten setzt `betragGeteilt`
    (`ui/Zeiten.kt`, getestet): die ganze Zahl groß, **alle**
    Nachkommastellen klein dahinter (Wunsch des Besitzers, 12.11.2026) –
    **nie gerundet, nie weggelassen** (Regel 11);
    beim Prüfen und Bestätigen stehen alle Stellen in einer Größe. Nach
    dem Senden: ein Wort, ein Satz, drei Schritte. „Bestätigt" steht erst
    nach zwei Bestätigungen da (Regel 20); „Abgeschickt" heißt: vom Knoten
    angenommen. Neue Texte sind kurz und kommen ohne Fachwort aus –
    Knoten, Spiegel, Tresor, Kette, Kennung gehören unter „Einzelheiten",
    in den Selbsttest und in die Fehlermeldungen aus `netz/Senden.kt`
    (die bleiben, Regel 36). Der Hauptbildschirm zeigt weder die eigene
    Adresse (die steht unter „Empfangen") noch das Diagramm (das steht im
    Verlauf).

41. **Ein Kontakt ist eine Lesehilfe, kein Beweis** (Wunsch des Besitzers,
    12.11.2026: Namen statt Adressen). `kontakte/Kontakte.kt` ist reine
    Logik und in `KontakteTest` getestet. **Der Name ersetzt die Adresse
    nirgends**: Beim Prüfen steht er ÜBER der Adresse, die weiter
    vollständig dasteht; unterschrieben wird, was im Entwurf steht (Regel
    19), und der Entwurf kennt nur die Adresse. **Ein Name entsteht nur
    durch Eintippen** – kein QR-Code, kein Link und keine Antwort des
    Netzes legt einen Kontakt an oder benennt ihn um (Regel 22).
    Gespeichert wird nur, was `Address.isValid` besteht; eine Adresse hat
    höchstens einen Namen; Namen werden bereinigt (keine Steuerzeichen,
    nichts, was die Schreibrichtung dreht – sonst sähe ein Name auf dem
    Bildschirm anders aus, als er gespeichert ist). Beim Lesen aus dem
    Speicher wird jede Zeile geprüft wie eine neue Eingabe. Die Auswahl
    beim Senden setzt die ADRESSE ins Feld. Kontakte liegen in eigenen
    SharedPreferences (`Kontaktspeicher`), **nicht** bei den Vorlieben –
    die liest auch der Wächter im Hintergrund, und der braucht keine
    Kontakte. Sie verlassen das Gerät nie, stehen in keinem Log und
    werden gelöscht, wenn die Wallet vom Gerät entfernt wird.

42. **Kein echtes Konto im Repository** (Entscheidung des Besitzers,
    13.11.2026). Adressen, Kennungen von Überweisungen, Blockhöhen,
    Beträge und Guthaben eines echten Kontos stehen weder im Quelltext
    noch in Testdaten, Dokumenten oder Bildern – auch nicht als Beispiel
    und auch nicht gekürzt. Die Kette ist öffentlich: Wer eine Kennung,
    eine Höhe mit Betrag oder ein Guthaben auf acht Stellen kennt, findet
    das Konto im Explorer. Beispieladressen sind gültig gebildet, aber
    erfunden – **nichts dorthin senden, niemand hat den Schlüssel**. Die
    Testdaten unter `test/resources/netz` haben die Form echter
    Antworten und erfundene Werte. Wer neu mitschneidet, verfremdet,
    bevor er sichert: Adressen und Kennungen neu würfeln, Höhen und
    Zeiten neu verteilen, Beträge neu ziehen und das Guthaben daraus
    rechnen. Eine Messung an einem echten Konto wird beschrieben, nicht
    abgedruckt.
