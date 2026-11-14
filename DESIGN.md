# Das Aussehen der YSKAR-Wallet — Design „Erstes Licht"

Dieses Dokument beschreibt die Oberfläche, wie sie gebaut ist: Farben,
Schrift, Bausteine, Bildschirme, und wie geprüft wird, dass sie stimmt.

**Seit dem 12.11.2026 trägt die Wallet den Stil der Website** (Vorschau 2,
„Erstes Licht"): Nachtblau statt Graphit, Hanken Grotesk statt Geist, ein
warmer Hauptknopf statt eines blauen, Linien statt Kästen. Aufbau, Maße
und Abläufe stammen weiter aus dem Umbau „Prägung" vom 10. und 11.11.2026
und sind geblieben — wo dieses Dokument Maße nennt, sind es dessen. Die
Abschnitte 3 bis 5 und 19 beschreiben den neuen Stil; wo ein älterer
Abschnitt noch von Karte, Lichtkante oder Linienmuster spricht, gilt
Abschnitt 19. **Abschnitt 20 („Einfacher") hat danach den Aufbau von vier
Bildschirmen geändert** – wo Abschnitt 7 oder 8 sie anders beschreibt,
gilt Abschnitt 20.

- **Die Vorgabe für den Stil** ist die Website; die Skizze, nach der
  gebaut wurde, liegt unter `entwurf/erstes-licht-vorschau.png`.
- **Die Vorgabe für Aufbau und Maße** liegt in `../design-neu/`:
  `DESIGN-NEU.md`, die Vorlagen unter `html/`.
- **Der Weg hierher** — was gefragt, entschieden, gebaut, gefunden und
  nicht geprüft wurde — steht Schritt für Schritt in `UMBAU.md`.
- **Die Bilder vom Gerät** liegen in `bilder/`, beschrieben in
  `bilder/LIESMICH.md`.
- **Frühere Stände** liegen in Git: `vor-erstes-licht` (Design „Prägung"),
  `vor-umbau` (das Design davor).

---

## 1. Was der Umbau geändert hat und was nicht

Geändert hat sich das Aussehen. Abläufe, Prüfungen und Texte sind
geblieben, wo `DESIGN-NEU.md` nichts anderes sagt; jede Abweichung steht
mit Grund in `UMBAU.md`.

Nicht angefasst: `kern/`, `tresor/`, `netz/Senden.kt`,
`netz/Sendestatus.kt` und alles, was unterschreibt oder prüft. Der Entwurf
einer Überweisung wandert unverändert vom Prüfschritt bis zum Senden
(`REGELN.md`, Regel 19).

Dazugekommen sind drei Dinge, die es vorher nicht gab: der Guthabenverlauf
(Abschnitt 8), die Einführung (Abschnitt 9) und der Schriftzug auf dem
Startbildschirm (Abschnitt 10).

## 2. Grundsätze

- **Die App ist immer dunkel.** Die Vorlage ist nur dunkel gezeichnet; ein
  helles Schema liegt unbenutzt in `ui/Theme.kt`, bis es jemand zeichnet.
- **1 px der Vorlage ist 1 dp**, Schrift in sp. Die Vorlagen sind 390 × 844;
  das Entwicklungsgerät hat 400 × 869 dp.
- **Gekürzt wird die Form, nie die Wahrheit.** Beträge stehen vollständig
  bis zur achten Stelle da, Adressen und Kennungen ganz. Reicht der Platz
  nicht, wird die Schrift kleiner oder die Zeile bricht um — abgeschnitten
  wird nichts (Regeln 11 und 29).
- **Nichts heißt „endgültig".** Eine Überweisung ist „angenommen", „in
  einem Block", „zweimal bestätigt" (Regel 20).
- **Wo etwas fehlt, sagt die Oberfläche es.** Ein alter Stand trägt sein
  Datum, ein teilweise geladener Verlauf heißt „belegt ab …", und ein
  Verlauf, der das Guthaben nicht erklärt, bekommt keine Linie.

## 3. Farben — `object Farbe` in `ui/Theme.kt`

Die Werte sind die der Website (`:root` ihres Stylesheets); in Klammern
ihr Name dort.

| Name | Wert | Wofür |
|---|---|---|
| Grund | `#040A1A` (night-900) | jeder Bildschirm; oben `#0B1C40` (night-700) bis 38 % der Höhe, ganz unten `#02050F` (night-950). Oben links und rechts je ein kaltes Licht (`Grund` in `Teile.kt`). |
| Fläche | Hauch 7,5 % → 3,5 % | Karten und Felder: durchscheinend, kaum mehr als der Grund |
| Tafel | `#0A1630` | was ÜBER anderem Inhalt liegt (Rückfrage) — deckend |
| Linie | Hauch 15 % (line) | Ränder, Trennlinien |
| Hauch | `rgb(170,198,255)` mit Deckkraft | alles Durchscheinende. Reines Weiß sähe auf Nachtblau grau aus. |
| Schrift | `#EEF4FF` (ice) | Text |
| Weich | `#ADB6C8` (ice-2 auf dem Grund) | Absätze, Zweitrangiges |
| Leise | `#8792AB` (ice-3 auf dem Grund) | Beschriftungen, Hinweise |
| Verweis | `#63D8FF` (ribbon-1) | „Alle anzeigen", „Zeichen für Zeichen vergleichen" |
| Eingang | `#5BE3AB` (ok) | nur für Eingänge. Nie als Schmuck. |
| Ausgang | `#8792AB` | der Punkt an einem Ausgang |
| Warnung | `#FF8E8E` (bad), Schrift `#FFC9C9` | Fehler und Unwiderrufliches |
| Blau des Zeichens | `#63D8FF → #2F7BFF → #1B4FD1` (ribbon) | das Y, die Linie des Diagramms, Schrittpunkte, Schalter „an" |
| Lampe | `#FFC47A` (lamp), Schrift darauf `#1B1205` (lamp-ink) | der eine gefüllte Knopf je Bildschirm; die Beschriftung über der Hauptsache („Guthaben", „Du sendest", „Betrag", „Gesamt") |

**Zwei Lichter, zwei Bedeutungen — wie auf der Website.** Kaltes Blau ist
die Kette: Zeichen, Kurve, Adressen, Fortschritt. Warmes Lampenlicht ist
der Mensch: der Knopf, mit dem du handelst, und die Beschriftung über dem,
worum es geht. **Das Lampenlicht ist keine Warnfarbe.** Rot bleibt allein
für Fehler und Unwiderrufliches; auf dem Prüfschritt stehen beide
untereinander (`bilder/09_senden_pruefen.png`).

## 4. Schrift — `object Schrift` in `ui/Theme.kt`

Hanken Grotesk (die Schrift der Website) und Geist Mono als variable
Schriften unter `res/font`; Hanken in den Schnitten 300, 400, 500 und 600
über `FontVariation.weight`. Ziffern mit fester Breite (`tnum`), damit
Beträge untereinander stehen — nachgesehen an
`bilder/00d_bausteine_schrift.png`. Lizenzen: `assets/lizenzen/`.

**Groß und leicht für Zahlen und Aussagen, klein und kräftig für
Bedienung.** Die Größen sind die des Umbaus „Prägung" geblieben, weil sie
am Gerät vermessen sind (Regel 29); geändert haben sich Gewicht und
Laufweite: `Guthaben` (58/300), `BelegBetrag` (54/300), `BetragEingabe`
(38/300), `Riesig` (36/300), `Gross` (26/300), `Mittel` (20/400), `Ziffer`
(28/300); `Titel` (16/600), `Kopf` (15/600), `Zeile` (15/500), `Betrag`
(16/500), `Text` (15), `Name` (14), `Klein` (13), `Hinweis` (12,5), `Marke`
(11/600); neu `Auftakt` (14/500) für die warme Beschriftung. In Geist Mono
`Adresse` (15), `AdresseKlein` (13,5), `Kurz` (12,5), `Winzig` (11).

**Große Systemschrift.** Geprüft wird jeder Bildschirm auch mit
Systemschrift 1,3. Ab Android 14 wächst große Schrift dabei kaum noch mit:
Das Guthaben (58 sp) bleibt fast gleich, Text von 15 sp wächst voll.

## 5. Bausteine — `ui/Teile.kt`

Jede Form der Vorlage ist einmal gebaut und wird überall benutzt:

| Baustein | Was er ist |
|---|---|
| `Buehne` | der Rahmen jedes Bildschirms: Grund bis hinter die Systemleisten, Inhalt mit deren Rändern. **App und Testrahmen zeichnen durch dieselbe Funktion** (Regel 29). |
| `Flaeche`, `Abschnitt` | Feld mit Haarlinie, durchscheinend; sein Titel mit Symbol. Keine Glanzkante mehr. |
| `Kartenkopf` | der Kopf eines Bildschirms oder Belegs (Guthaben, Betrag). Zeichnet seit dem 12.11.2026 nur noch seinen Inhalt — die Hauptsache steht ohne Kasten auf der Nacht. |
| `GuthabenZahl` | ganze Zahl groß, Nachkommastellen klein; wird kleiner, nie kürzer |
| `Beleg`, `Abrisskante`, `BelegFuss` | der Beleg des Prüfschritts und der Bewegung: ohne Rahmen, die Abschnitte durch Linien getrennt |
| `Knopf` (`HAUPT`, `FLAECHE`, `BLAU`, `BLAU_GROSS`, `STILL`, `WARNUNG`) | alle Knöpfe mit Beschriftung, ganz rund. `HAUPT` ist Lampenlicht mit dunkler Schrift, `FLAECHE` nur ein heller Umriss. |
| `RunderKnopf`, `Symbolknopf`, `Zeilenknopf` | die vier Aktionen; Knöpfe ohne Wort; Zeilen, die irgendwohin führen |
| `Kopfzeile`, `Schrittpunkte` | Zurück, Titel, rechts die Schritte |
| `Wertzeile`, `Verlaufszeile` | Name und Wert; eine Bewegung in der Liste |
| `AdresseInGruppen`, `KennungInGruppen`, `Adressmarke` | Adresse vollständig in Vierergruppen; die kurze Erkennungsmarke |
| `Schaltzeile`, `Umschalter` | Einstellung mit Schalter |
| `Ziffernfeld` | das eigene Ziffernfeld für jede PIN (Regel 15); wo es einen Fingerabdruck gibt, steht er links neben der Null — nur das Symbol, blau, ohne Kreis |
| `Warnhinweis`, `Hinweisstreifen`, `Platzhalter` | rot, blau, und der stille Balken für das, was noch lädt |
| `QrKarte` | QR-Code auf weißer Karte, Ruhezone vier Module, das Y in der Mitte (Regeln 30 und 31) |
| `Eingabe` | Eingabefeld ohne eigenen Rahmen |
| `Blatt`, `Musterhimmel` | Bildschirm, der nur rollt, wenn er muss; das Linienmuster dahinter |

Die Symbole sind 30 Phosphor-Icons als `res/drawable/ic_*.xml`, erzeugt mit
`werkzeug\icons-umwandeln.ps1`.

Reine Logik der Oberfläche liegt getrennt und ist ohne Gerät getestet:
`ui/Zeiten.kt` (Heute, Gestern, „Stand 18:17"), `ui/QrCode.kt`,
`ui/Kurvenform.kt`, `ui/Einfuehrungstexte.kt`, `ui/Merkwortfelder.kt`.

## 6. Das Zeichen — `ui/Zeichen.kt`

Das Y aus drei Teilen — linkes Band, rechtes Band, Stamm — ist mehr als ein
Logo: Es zeigt Fortschritt. Beim Eintippen der PIN füllt sich mit je zwei
Ziffern ein Teil; nach dem Senden steht jedes Teil für einen Schritt
(angenommen, in einem Block, zweimal bestätigt). „Aus" ist eine helle
Kontur mit fast leerer Füllung, ein Fehler färbt alle drei rot.

## 7. Die Bildschirme

| Nr. | Bildschirm | Code | Bild |
|---|---|---|---|
| 01 | Einstieg | `ui/Einrichten.kt` | `01_einstieg` |
| 02 | PIN festlegen | `ui/Einrichten.kt` | `02_pin_setzen` |
| 03 | Sperre | `ui/Sperre.kt` | `03_sperre` |
| 04, 05 | Wallet | `ui/Wallet.kt`, `ui/Kurve.kt` | `04_wallet…`, `05_wallet…` |
| 06 | Empfangen | `ui/Wallet.kt` | `06_empfangen` |
| 07 | Verlauf | `ui/Wallet.kt` | `07_verlauf` |
| 08, 09, 16, 10, 13 | Senden: Eingabe, Prüfen, Unterschreiben, Status | `ui/SendenScreens.kt` | `08…`, `09…`, `16…`, `10…`, `13…` |
| 11 | Einstellungen, Sprache, Rückfrage, PIN-Abfragen | `ui/Einstellungen.kt` | `11_…` |
| 12 | Scanner | `ui/Scanner.kt` | `12_scanner…` |
| 14, 15 | Bewegung | `ui/Bewegung.kt` | `14_bewegung`, `15_bewegung_eingang` |
| 17–20 | Wörter zeigen, Gegenprobe, Wiederherstellen, Wörter ansehen | `ui/Einrichten.kt` | `17_…` bis `20_…` |
| 21 | Selbsttest | `ui/SelbsttestScreen.kt` | `21_selbsttest…` |
| 22 | Einführung, acht Karten | `ui/Einfuehrung.kt` | `22_einfuehrung_1…8` |

01 bis 16 haben eine Vorlage in `design-neu/html`; 17 bis 21 sind ohne
Zeichnung im selben Stil gebaut.

**Was auf den wichtigen Bildschirmen gilt:**

- **Wallet (04).** Die Zeile „Stand 18:17" ist der Knopf zum
  Aktualisieren; älter als heute trägt sie das Datum. Ohne Netz steht das
  zuletzt bekannte Guthaben da, sichtbar als alt, und „Senden" ist aus —
  gesendet wird nur mit frisch geladenem Konto. Das Auge verbirgt
  Guthaben, Diagramm, Summen und die Beträge der Liste. Der Bildschirm
  rollt als Ganzes.
- **Prüfen (09) und Unterschreiben (16).** Der Beleg zeigt Betrag, Adresse
  in Vierergruppen, Gebühr, Gesamt, „Danach verfügbar", die Notiz und die
  technischen Angaben. Auf dem Bildschirm, auf dem unterschrieben wird,
  stehen Gesamt, Betrag, Gebühr und Empfänger über dem Ziffernfeld
  (Regel 27).
- **Status (10, 13).** Überschrift und Unterzeile nennen den erreichten
  Schritt, die drei Schilder die Teile des Zeichens, fünf Zeilen die
  Schritte — mit Uhrzeiten dieses Geräts, Blocknummer und Zahl der
  Bestätigungen, wo sie bekannt sind.
- **Die vier Seiten mit den zwölf Wörtern (17–20)** tragen `FLAG_SECURE`.
  Jede ist zweigeteilt: `…Screen` setzt den Schutz und ist, was die App
  aufruft; `…Bild` ist nur das Aussehen und wird sonst allein vom
  Testrahmen gezeichnet (Regel 14).
- **Einstellungen (11).** Ein Einstellungsfenster aus Gruppen und Zeilen
  (Abschnitt 14). „Wallet von diesem Gerät entfernen" steht am Ende in
  eigener roter Fläche; die Rückfrage ist eine eigene Karte über dem
  abgedunkelten Bildschirm, der rote Knopf oben, „Abbrechen" unten.

## 8. Der Guthabenverlauf

Die Karte „Guthaben · 30 Tage" auf dem Hauptbildschirm: Wahl „7 T / 30 T /
1 J", eine Stufenlinie mit Fläche darunter, ein Punkt für heute, darunter
die Summen „Eingang" und „Ausgang".

- **Gerechnet** wird rückwärts vom heutigen Guthaben
  (`netz/Guthabenverlauf.kt`, Regel 32): Ein Ausgang kostet Betrag plus
  Gebühr, ein Eingang bringt den Betrag. Gemessen am vollständigen
  Verlauf eines echten Kontos — die Rechnung kam bei genau 0 an
  (`NETZ.md`, Abschnitt 9).
- **Geladen** wird leise, nur auf dem Hauptbildschirm, 200 Bewegungen je
  Anfrage, bis der gewählte Zeitraum abgedeckt ist
  (`netz/Verlaufslader.kt`).
- **Die Linie zeigt die Form, nicht die Größe.** Sie reicht vom kleinsten
  bis zum größten Stand des Zeitraums. Zahlen stehen in den Summen und,
  für Bildschirmleser, im Satz „von X auf Y YSR".
- **Zustände:** stiller Balken, solange nichts da ist; „belegt ab …", wenn
  ältere Seiten fehlen; waagrechte Linie ohne Bewegung; kein Diagramm,
  sondern ein Satz, wenn der Verlauf das Guthaben nicht erklärt.

## 9. Die Einführung

Acht Karten (`ui/Einfuehrung.kt`, Worte in `ui/Einfuehrungstexte.kt`):
einmal direkt nach dem Einrichten, danach unter „Einstellungen" als „Einführung
ansehen". „Überspringen" steht auf jeder Karte, nichts läuft von allein.

Sieben Karten tragen ein Foto (`res/drawable-nodpi`, zusammen 1,3 MB),
oben und unten in den Grund ausgeblendet; darüber schweben Marken und, auf
den Karten 2 und 3, drei YSR-Münzen mit Bogen — in Compose gezeichnet,
nicht ins Bild gebrannt. Karte 4 zeigt das Y in drei Stufen.

Was über den Fotos schwebt, wächst nicht mit der Systemschrift;
Überschrift und Absatz schon. Auf Karte 2 steht die eigene Adresse.

## 10. App-Symbol und Startbildschirm

**Das App-Symbol** ist das Zeichen als `adaptive-icon` in drei Ebenen
(`symbol_hintergrund.xml`, `symbol_vordergrund.xml`,
`symbol_einfarbig.xml`). Der Hintergrund ist schwarz (`#000000`, Wunsch des
Besitzers, 11.11.2026) und ist es beim Wechsel zu „Erstes Licht" geblieben;
vorher trug er den Verlauf des früheren Designs.
Das Zeichen steht darin auf 48 von 108 Einheiten (bis zum 11.11.2026 auf
60): Sein fernster Punkt liegt 31,7 von der Mitte und damit in der sicheren
Zone, einem Kreis mit Halbmesser 33 – keine Maske schneidet es an. Der
Gerätetest `Symbolbild` zeichnet das Symbol in Dateien, prüft den Grund,
misst diesen Abstand nach und prüft den Namen.

**Der Name unter dem Symbol** ist „Yskar" (`app_name`). Projekt, Ordner und
Paket heißen weiter `YskarWallet` bzw. `com.yskar.wallet`.

**Nur Hochformat.** `MainActivity` ist im Manifest auf `portrait`
festgelegt. Auf Tablets und aufgeklappten Geräten übergeht Android 16 das.

**Der Startbildschirm** (`res/values/themes.xml`, `Theme.Start`): Grund
`#040A1A` (derselbe Wert wie `Farbe.Grund`; `StartbildTest` hält beide zusammen), darauf läuft das Zeichen ab — linkes Band, rechtes Band, Stamm,
480 ms (`symbol_start.xml`). Gehalten wird, bis der Tresor gelesen ist, mit
Notbremse nach 2,5 s.

Ab Android 12 steht unten der Schriftzug „YSKAR"
(`res/values-v31/themes.xml`). Android erlaubt dort nur ein Bild; es liegt
als `drawable-xxxhdpi/marke_start.png` vor, gezeichnet in Hanken Grotesk 600 vom
Gerätetest `Schriftzugbild`. Auf älteren Geräten bleibt der Startbildschirm
ohne Schriftzug.

## 11. Wie geprüft wird

HyperOS lässt keine Eingaben über adb zu; von außen ist in der App nichts
zu bedienen. Darum steht jede Aussage über die Oberfläche auf einem von
vier Beinen:

1. **Unittests auf der JVM** — 471, darunter die reine Logik der
   Oberfläche, die Rechnung des Guthabenverlaufs am echten Mitschnitt und
   alle Texte beider Sprachen (`TexteTest`).
   `.\gradlew.bat testDebugUnitTest`
2. **Bilder vom Gerät** — `Bildschirmbilder` zeichnet jeden Bildschirm mit
   Beispieldaten, normal, mit Systemschrift 1,3 und auf Englisch, durch
   denselben Rahmen wie die App. `.\werkzeug\bilder-holen.ps1`
3. **`rollen.txt`** — zu jedem Bild, wie weit der Bildschirm sich rollen
   lässt und wie weit etwas unter den sichtbaren Bereich ragt. Nur das
   Zweite heißt, dass jemand rollen muss.
4. **Gerätetests ohne Tresor** — `SendenOberflaecheTest` (was im
   Prüfschritt steht), `SchutzTest` (die Wörter-Seiten sperren das
   Fenster), `HintergrundTest` (Vorhang, Lebenslauf und Fenster im
   Hintergrund), `StartbildTest` (das Thema des Startbildschirms löst sich
   auf), `Symbolbild` (das App-Symbol und sein Name).

`OberflaecheTest` spielt das Einrichten durch und **löscht dafür den
Tresor**. Er läuft nicht, solange eine echte Wallet auf dem Gerät liegt
(Regel 17), und ist darum seit dem Umbau nur angepasst, nicht gelaufen.

## 12. Wo die App von der Vorlage abweicht

Alle mit Absicht; die Begründung steht jeweils in `UMBAU.md`.

- **Einstellungen (11):** kein Bildschirm aus fünf Karten mehr, sondern
  ein Einstellungsfenster — siehe Abschnitt 14 (Wunsch des Besitzers,
  11.11.2026). Auf dem Hauptbildschirm heißt der Knopf dorthin
  „Einstellungen" statt „Mehr".
- **Einstieg (01):** oben rechts ein Knopf zur anderen Sprache.
- **Senden, Eingabe (08):** Der Betrag steht frei und groß in der Mitte,
  ohne Karte, darüber nichts als „Betrag", darunter „Verfügbar". Die
  Adresse hat ihre zwei Helfer als Symbole im Feld. Die Notiz ist
  eingeklappt („Notiz hinzufügen"); ihr Kreuz löscht sie (Wunsch des
  Besitzers, 11.11.2026).
- **Wiederherstellen** hat zwölf Felder, eines je Wort, statt eines großen
  Feldes — wie die zwölf Kacheln beim Anlegen. Angenommen werden genau
  zwölf Wörter (Wunsch und Entscheidung des Besitzers, 11.11.2026).
- **Fingerabdruck:** Auf der Sperre, beim Unterschreiben und vor den zwölf
  Wörtern steht er im Ziffernfeld, links neben der Null — nur das Symbol,
  blau, ohne Kreis, wie rechts der Rückschritt. Die Vorlage zeichnet einen
  breiten Knopf am Fuß (Wunsch des Besitzers, 11.11.2026).
- **Senden bei großer Systemschrift (über 1,15):** Prüfschritt und Status
  werden knapper gesetzt, damit sie ohne Rollen dastehen — die Adresse
  bleibt in zwei Zeilen, kein Wert rutscht unter seine Beschriftung, die
  Namen der Schritte bleiben einzeilig, der große Betrag und das Zeichen
  wachsen nicht mit. Gekürzt wird nichts (Feinschliff, 11.11.2026).
- **Die Wahl des Zeitraums** im Diagramm ist 36 dp hoch statt 28.
- **Große Summen** stehen untereinander statt nebeneinander.
- **Der Hauptbildschirm rollt als Ganzes**, nicht nur die Liste.
- **Einführung:** der Satz auf Karte 7; die eigene Adresse auf Karte 2;
  Kopf und Fuß stehen fest, die Karten lassen sich wischen.
- **QR-Code:** Ruhezone vier Module von der weißen Karte, nicht die 18 px
  der Zeichnung (Regel 30).
- **Rückfrage „Wirklich entfernen?"** und die Bildschirme 17 bis 21 haben
  keine Vorlage.

## 13. Was offen ist

Stand 11.11.2026. Einzelheiten in `UMBAU.md`, jeweils unter „Nicht
geprüft".

- **Kein Ablauf ist von Hand durchgespielt** — anlegen, wiederherstellen,
  senden, PIN ändern, entfernen. Die Bilder zeigen Zustände, keine Wege.
- **Das Diagramm mit echten Daten** in der laufenden App.
- **Die Einführung nach dem Einrichten** — der Weg legt einen Tresor an.
- **Der Startbildschirm** mit Schriftzug: ob HyperOS ihn zeigt.
- **Die Leseprobe des QR-Codes** mit einem zweiten Telefon. Liest es ihn
  nicht zuverlässig, kommt das Y heraus (Regel 31).
- **Die Tastatur** auf Gegenprobe und Wiederherstellen.
- **Kleinere Geräte** und andere Hersteller.
- Entscheidungen, die beim Besitzer liegen: „erledigt" neben einem Schritt
  ohne bekannte Uhrzeit; der Satz über den Push-Dienst unter „Einstellungen"; die Teilung der
  Wörter-Seiten in Screen und Bild.
- Nicht Teil dieses Umbaus: ein helles Schema, weitere Sprachen.

## 14. Das Einstellungsfenster — `ui/Einstellungen.kt`

Seit dem 11.11.2026 (Wunsch des Besitzers). Vorher hieß der Bildschirm
„Mehr" und bestand aus fünf Karten mit Knöpfen darin.

**Aufbau.** Eine Überschrift je Gruppe (klein, gesperrt, leise), darunter
eine Fläche mit Zeilen ohne Abstand, getrennt durch eine Linie, die unter
dem Text beginnt. Jede Zeile: links das Symbol im getönten Kästchen
(34 dp), daneben Name und — wo es etwas zu sagen gibt — ein Satz, rechts
genau eines:

| Zeile | rechts | Verhalten |
|---|---|---|
| `Schalterzeile` | Schalter | die GANZE Zeile schaltet; für einen Bildschirmleser ist sie ein Schalter |
| `Pfeilzeile` | Pfeil, davor wahlweise ein Wert | die ganze Zeile ist der Knopf |
| `Einstellzeile` | Wert | sagt nur etwas; nichts zu tippen |

Was eine Gruppe als Ganzes erklärt, steht als Fußnote unter ihrer Fläche.

**Gruppen.** Sicherheit (Fingerabdruck, PIN ändern, Zwölf Wörter anzeigen,
Automatisch sperren) · Allgemein (Sprache, Guthaben verbergen, Vibration)
· Benachrichtigungen (Im Hintergrund nachsehen, mit dem Satz über den
Push-Dienst) · Signaturen (Mit QR-Code anmelden, Nachricht signieren) ·
Diese Wallet (Adresse, Netz, Im Explorer ansehen, Einführung ansehen,
Selbsttest) · am Ende, in eigener rot getönter Fläche: Wallet von diesem
Gerät entfernen · darunter die Fassung.

„Automatisch sperren — Beim Verlassen" ist eine Angabe, kein Schalter:
Es lässt sich nicht abstellen.

**Die Seite „Sprache"** öffnet sich aus der Zeile: eine Zeile je Sprache
in ihrem EIGENEN Namen („Deutsch", „English"), die gewählte mit Haken. Die
Wahl gilt sofort.

## 15. Sprache — `sprache/`

Deutsch und Englisch; gewählt unter „Einstellungen" und auf dem Einstieg,
unabhängig von der Sprache des Telefons, ohne Neustart. Solange niemand
gewählt hat: Deutsch auf einem deutschen Gerät, sonst Englisch.

- **Kein Text steht im Bildschirm.** Je Bereich ein Interface
  (`SendenTexte`, `WalletTexte`, …) mit einer deutschen und einer
  englischen Fassung. Fehlt ein Text in einer Sprache, baut die App nicht.
- **Zahlen und Daten** folgen der Sprache: „1.284,57" und „11.11.2026"
  auf Deutsch, „1,284.57" und „11 Nov 2026" auf Englisch. Die Uhrzeit
  steht in beiden mit 24 Stunden.
- **Die Übersetzung sagt dasselbe**, nicht mehr und nicht weniger: Wo
  Deutsch „angenommen" steht, steht „accepted", nie „final" oder
  „completed" (Regel 20).
- **Die zwölf Wörter** sind in beiden Sprachen dieselben englischen
  BIP39-Wörter.
- **Die Meldungen aus `netz/Senden.kt`** übersetzt `Sendemeldungen` beim
  Anzeigen; die Datei selbst prüft und unterschreibt und bleibt unberührt.
- **Nicht übersetzt:** die Fotos der Einführung (sie zeigen ein Telefon
  mit deutscher Oberfläche), Abbruchtexte des Systems bei der
  Fingerabdruck-Abfrage, einzelne Werte im Selbsttest, die aus `netz/`
  und `tresor/` kommen.

## 16. Im Hintergrund — `ui/Hintergrundschutz.kt`

Seit dem 11.11.2026 (Wunsch des Besitzers; REGELN.md, Regel 35).

- **Gesperrt:** Sobald die App verlassen wird, steht sie vor der Sperre.
  Nach dem Entsperren geht es zum Hauptbildschirm — außer der Status
  einer abgeschickten Überweisung war offen; der kommt wieder.
- **Nicht zu erkennen:** Solange die App nicht vorn ist, liegt über dem
  Inhalt der Grund der App mit dem Zeichen in der Mitte (72 dp). Die
  App-Übersicht bekommt ab Android 13 kein Vorschaubild.
- Bildschirmfotos bleiben erlaubt, außer auf den Seiten mit den zwölf
  Wörtern (Regel 14).

## 17. Der Explorer — `ui/Explorer.kt`

`https://www.yskar.app/explorer`. Drei Wege führen dorthin, jeder auf
einen Tipp und nie von selbst: der Knopf „Im Explorer ansehen" unter der
Kennung einer Bewegung, das Symbol unter dem Kopieren auf dem Status nach
dem Senden, und die Zeile in den Einstellungen, die das eigene Konto
öffnet. Das Symbol ist überall dasselbe (Pfeil aus dem Kästchen).

Der Browser geht auf, die Wallet sperrt sich (Abschnitt 16). Eine Adresse
in den Explorer wird nur aus einer geprüften Kennung oder einer gültigen
Adresse gebildet; sonst fehlt der Knopf.

## 18. Anmelden und Signieren — `ui/Signieren.kt`

**Am Gerät aufgenommen am 11.11.2026 um 23:09** (`bilder/23_…`, `24_…`,
deutsch und englisch). Gesehen sind die Bilder; noch nie von Hand bedient
sind der Scanner mit einem echten Anmelde-Code, der Link vom Browser und
der Fingerabdruck beim Signieren.

Ein Bildschirm, zwei Eingänge, beide unter Einstellungen → Signaturen:

- **Anmelden** (auf einer YSKAR-Seite, etwa YSKAR Lab): scannen oder
  Link → die Anfrage ansehen → PIN oder Finger → gesendet. Die Domain
  steht groß und in Festbreite in der Mitte — „Wo meldest du dich an?"
  ist die Frage dieses Bildschirms. Darunter die Adresse mit dem
  grünen „Diese Wallet", die Ablaufzeit mit Countdown, wohin die
  Antwort geht, und die genaue Nachricht in Festbreite.
- **Signieren** (freier Text): tippen oder einfügen → ansehen, was genau
  signiert wird, mit den zwei Zeilen, die die Wallet davorsetzt → PIN
  oder Finger → Signatur zum Kopieren.

Bestätigt wird wie überall mit dem eigenen Ziffernfeld, der Finger als
Taste links neben der Null. Über dem Feld steht, WAS bestätigt wird
(„Anmelden bei lab.yskar.app"). Meldungen — wird gesendet, angemeldet,
abgelehnt — sind eine Seite mit einem Symbol im getönten Kästchen, einem
Satz und höchstens einem Knopf. Was eine fremde Seite sagt, steht als
Zitat da.

## 19. „Erstes Licht" — der Stil der Website (12.11.2026)

Wunsch des Besitzers: Die Wallet soll zur Website passen. Entschieden hat
er an einer Skizze (`entwurf/erstes-licht-vorschau.png`): warmer
Hauptknopf, Schrift der Website, und die vier runden Knöpfe auf dem
Hauptbildschirm bleiben.

**Was sich geändert hat — an den Bausteinen, nicht an den Bildschirmen:**

- **Der Grund** ist Nacht mit zwei kalten Lichtern oben (`Grund`). Das
  Linienmuster („Musterhimmel") ist weg; der Baustein hält nur noch seinen
  Platz frei.
- **Die Hauptsache steht ohne Kasten da.** Guthaben, der Betrag eines
  Belegs und seine Zeilen liegen direkt auf dem Grund, getrennt durch
  Linien. `Kartenkopf` und `Beleg` zeichnen nichts mehr außer ihrem
  Inhalt; Lichtkante, Linienmuster und das eingeprägte Zeichen entfallen.
  Guthabenkarte und Belege sitzen dafür 14 dp weiter außen, damit Text und
  Linien bei 22 dp vom Rand stehen.
- **Knöpfe sind ganz rund.** Der Hauptknopf ist Lampenlicht mit dunkler
  Schrift und einem warmen Schein darunter; alles andere von Gewicht ist
  ein heller Umriss. „Senden" unter den vier runden Knöpfen ist der eine
  warme.
- **Das Ziffernfeld** hat Umrisse statt gefüllter Kreise; die Ziffer ist
  leicht (300).
- **„YSR"** steht ohne Kästchen hinter dem Betrag, klein und gesperrt, auf
  seiner Grundlinie.
- **Felder** (Einstellungsgruppen, Eingabefelder, das Diagramm) sind
  durchscheinend mit Haarlinie. Was über anderem Inhalt liegt, bekommt die
  deckende `Tafel`.

**Was geblieben ist:** jede Größe, jeder Abstand innerhalb der Bausteine,
alle Abläufe, alle Texte. Das Zeichen zeigt weiter den Fortschritt der
PIN und den Stand einer Überweisung. Die Wallet bleibt nur dunkel.

**Was nicht passt und offen ist:**

- **Die Fotos der Einführung** zeigten auf dem abgebildeten Telefon die
  Wallet im alten Stil (blauer Knopf, Graphit). Seit dem 13.11.2026 setzt
  `werkzeug\fotos.ps1` die aktuellen Bildschirmbilder auf die Displays
  (Wunsch des Besitzers): Hauptbildschirm, Prüfschritt, Verlauf und Sperre
  im Stil „Erstes Licht" (`einfuehrung_1`, `_3`, `_5`, `_7`; am Gerät
  aufgenommen als `22_einfuehrung_…`). Das Licht der Fotos selbst bleibt
  neutral, nicht nachtblau, und `einfuehrung_2` (zwei Telefone, QR-Code)
  ist nicht angefasst.
- **Das App-Symbol bleibt schwarz** – so entschieden vom Besitzer am
  12.11.2026, nach dem Umbau gefragt. Die Website nennt als Themenfarbe
  `#030816`; das Symbol folgt ihr bewusst nicht.
- **Der Startbildschirm** ist umgestellt (Grund, Schriftzug), aber wie er
  aussieht, zeigt weiter kein Test und kein Bild.

## 20. „Einfacher" — das Nötige zuerst (12.11.2026)

Wunsch des Besitzers: Die Wallet wirkte zu technisch. Jeder soll sich
zurechtfinden, Anfänger wie Profi; die Bedienung soll einfach und modern
sein. Entschieden an einer Skizze (`entwurf/einfacher-vorschau.png`).
Regel 40 in `REGELN.md` hält den Grundsatz fest.

**Der Grundsatz: zwei Stufen.** Oben steht, was jeder wissen will. Was nur
manche brauchen, liegt einen Tipp entfernt unter „Einzelheiten"
(`Einzelheiten` in `ui/Teile.kt`) – vollständig wie vorher. Betrag, ganze
Adresse, Gebühr und Summe stehen nie darunter.

| Bildschirm | Oben | Unter „Einzelheiten" |
|---|---|---|
| Hauptbildschirm | Guthaben, vier runde Knöpfe, die letzten sieben Zahlungen | – (Adresse: „Empfangen"; Diagramm: „Verlauf") |
| Verlauf | Diagramm, darunter die Zahlungen nach Tagen, durch Linien getrennt | – |
| Prüfen | Betrag, die ganze Adresse, Netzgebühr, Gesamt, Notiz | danach verfügbar, laufende Nummer, Fassung, Größe |
| Bestätigen (PIN) | unverändert: Gesamt, Betrag, Netzgebühr, An (Regel 27) | – |
| Nach dem Senden | das Zeichen, ein Wort, ein Satz, drei Schritte, „Fertig" | die drei Schilder, die fünf Schritte mit Uhrzeiten, Kennung, Explorer, der Satz zu „endgültig", „Stand jetzt abfragen" |
| Zahlung | Betrag, wann, Stand, Netzgebühr (bei Ausgängen), die Adresse, die Notiz | vom Konto abgegangen bzw. Gebühr des Absenders, Anteile, Block, Platz im Block, Bestätigungen, Kennung, Explorer |
| Einstellungen | wie bisher | neue Gruppe „Erweitert": Explorer, Netz, Selbsttest |

**Beträge in Übersichten** (Guthaben, Verlauf, Summen am Diagramm,
„Verfügbar"): die ganze Zahl in voller Größe, **alle Nachkommastellen
klein** dahinter – „1.284" und dahinter „,57310946" (Wunsch des Besitzers,
12.11.2026; zwei Stunden lang standen die ersten beiden Stellen groß).
`betragGeteilt` (`ui/Zeiten.kt`) teilt nur; gerundet oder weggelassen
wird nichts, und `BetragGeteiltTest` setzt beide Teile wieder zusammen.
Beim Prüfen, Bestätigen und in der einzelnen Zahlung stehen alle Stellen
in einer Größe.

**Eine Zeile im Verlauf:** vorn ein Pfeil im Kreis (grün herein, leise
hinaus), dann was es war und wann, rechts der Betrag. Adressen und
Blocknummern stehen in der Zahlung, nicht in der Liste.

**Nach dem Senden – drei Schritte statt fünf, ohne etwas zu behaupten:**

| Wort | heißt | Satz darunter |
|---|---|---|
| Abgeschickt | vom Knoten angenommen | „… sind unterwegs. Das Netz bestätigt sie meist in wenigen Minuten." |
| Im Netz angekommen | in einem Block | „Die Überweisung steht in einem Block und wird gerade bestätigt." |
| Bestätigt | zwei Bestätigungen | „Das Netz hat die Überweisung zweimal bestätigt." |

Keines dieser Wörter heißt „erledigt" oder „endgültig" (Regel 20).
„Du kannst die App schließen. Die Überweisung läuft weiter." steht nur da,
solange noch etwas aussteht.

**Wörter:** „Bewegung" heißt „Zahlung", „Blockertrag" „Mining-Ertrag",
„Pool-Anteil" „Mining-Anteil (Pool)", „Gebühr" „Netzgebühr", „Bitte
prüfen" „Prüfen", „Unterschreiben und senden" „Jetzt senden",
„Unterschreiben" (der PIN-Bildschirm) „Bestätigen", „Stand 18:17"
„Aktualisiert 18:17", „Kurze Gegenprobe" „Kurze Kontrolle". Sätze mit
Knoten, Spiegel, Tresor oder Kette sind durch kürzere ersetzt; die
Fehlermeldungen aus `netz/Senden.kt` sind unberührt.

**Bewusst anders als die Skizze:**

- An der Adresse steht weiter „Zeichen für Zeichen vergleichen", nicht
  „Anfang und Ende vergleichen": Gefälschte Adressen werden so gebaut,
  dass Anfang und Ende stimmen.
- Die Summe heißt weiter „Gesamt" (wie auf dem PIN-Bildschirm), nicht
  „Zusammen".
- „Stand: Bestätigt" in der Zahlung ist nicht grün. Grün heißt in dieser
  Wallet nur „kommt herein".

**Nicht angefasst, mit Absicht:** die Fotos der Einführung (Entscheidung
des Besitzers, 12.11.2026: sie bleiben), der Selbsttest (er ist ein
Werkzeug und darf technisch sein), Anmelden und Signieren (ihr Wortlaut
stammt aus der bestehenden Wallet).

## 21. Kontakte — Namen statt Adressen (12.11.2026)

**Am Gerät aufgenommen am 12.11.2026 um 22:58** (`bilder/25_…`,
`04_wallet_kontakte`, `08_senden_eingabe_kontakt`,
`09_senden_pruefen_kontakt`, `14_bewegung_kontakt`); von Hand bedient hat
sie noch niemand. Regel 41 in `REGELN.md` hält den Grundsatz fest.

Ein Kontakt ist ein Name, den der Besitzer selbst einer Adresse gibt. Er
ist eine Lesehilfe – „An Anna" statt „An ysr1j8nn…" –, kein Beweis: Die
Adresse steht weiter überall da, wo geprüft wird.

| Wo | Was |
|---|---|
| Einstellungen → Allgemein → „Kontakte" | die Liste; rechts die Anzahl |
| Liste (`KontakteScreen`) | je Zeile ein Kreis mit dem ersten Buchstaben, der Name, darunter die Adresse in Kurzform; unten „Kontakt hinzufügen" |
| Ein Kontakt (`KontaktBearbeitenScreen`) | Name, Adresse (tippen, scannen, einfügen), „Speichern"; bei einem vorhandenen darunter „Kontakt entfernen" – zwei Tipps |
| Senden | am Adressfeld rechts der Verweis „Kontakte" (nur wenn es welche gibt); die Auswahl liegt über der Eingabe wie der Scanner. Hat die Adresse im Feld einen Namen, heißt das Feld „An Anna". |
| Prüfen | „An Anna" über der Adresse; die Adresse vollständig wie immer |
| Hauptbildschirm, Verlauf | „Von Anna" / „An Anna" statt „Empfangen" / „Gesendet" |
| Zahlung | „Von Anna" / „An Anna" über der Adresse; ohne Namen der Knopf „Als Kontakt speichern" |

**Bewusst nicht gebaut:** ein fünfter Knopf auf dem Hauptbildschirm (die
vier runden bleiben, Entscheidung vom 12.11.2026); Namen in den
Mitteilungen des Wächters; Kontakte im PIN-Bildschirm (dort steht weiter
die Adresse, Regel 27); Übernehmen von Kontakten aus einer anderen
Wallet.
