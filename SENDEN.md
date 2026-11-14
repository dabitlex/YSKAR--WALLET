# Senden

Der Teil, bei dem ein Fehler Geld kostet. Entsprechend gebaut: erst prüfen,
dann zeigen, dann erst den Schlüssel anfassen.

```
1 Eingabe      Empfänger, Betrag, Notiz
2 Prüfschritt  alles im Klartext, nichts abgekürzt, nicht zu überspringen
3 PIN/Finger   erst hier wird der Tresor geöffnet
4 Ergebnis     ehrlich: „angenommen" ist nicht „erledigt"
```

**Auf Schritt 3 stehen die Zahlen noch einmal.** Betrag, Gebühr, Gesamt und
der Empfänger — obwohl der Prüfschritt sie gerade gezeigt hat. Das ist
keine Dopplung aus Versehen: Wer die PIN eintippt, unterschreibt. Ein
Ziffernfeld ohne Zahlen daneben ist eine Blankounterschrift.

---

## 1. Was geprüft wird, bevor irgendetwas unterschrieben wird

`Senden.pruefe()` gibt im Zweifel **nicht** frei. Eine Überweisung, die der
Knoten ablehnt, kostet nur Zeit; eine, die er annimmt und die falsch ist,
kostet Geld.

| Prüfung | Abgelehnt, wenn |
|---|---|
| Kontostand bekannt | nein → sonst stimmt die laufende Nummer nicht |
| Fassung bekannt | weder Gebührenauskunft noch Höhe da |
| Sendepause | die drei Blöcke vor dem Fassungswechsel |
| Adresse | Prüfsumme oder Präfix falsch |
| Adressversion | `ysr1p…` vor Konsensfassung 6 |
| Eigene Adresse | an sich selbst — kostet nur Gebühr |
| Betrag | fehlt, unlesbar, null oder negativ |
| Staubgrenze | unter 100 Einheiten ab Höhe 4.000 |
| Notiz | über 32 **Byte** (Umlaut zwei, Emoji vier) |
| Deckung | Betrag + Gebühr über dem Guthaben — mit Fehlbetrag im Text |
| Wartende Zahlung | eine liegt noch im Mempool → Nonce-Kollision |

Die Gebühr wird aus der Auskunft des Knotens für **genau diese Größe**
berechnet, nie pauschal.

---

## 2. „Was du siehst, wird unterschrieben"

Das ist das Versprechen des Prüfschritts, und es ist belegt, nicht behauptet.

`SendenTest.dieEingereichtenBytesSindGenauDasVersprochene` nimmt die Bytes,
die eingereicht würden, wieder auseinander und vergleicht **jedes Feld**:
Absender, Empfänger, Betrag, Gebühr, Nonce, Fassung, Notiz, öffentlicher
Schlüssel — und prüft die Signatur.

Der Transport ist dabei eine Attrappe. **Kein Unittest spricht mit dem echten
Netz.**

Dazu kommt `SendenOberflaecheTest.derPruefschrittZeigtAllesUngerundet`: Der
Bildschirm wird einzeln gerendert und auf die letzte Nachkommastelle
geprüft — `1,50`, `0,0000173`, `1,5000173`, und was danach übrig bleibt.

---

## 3. Der Schlüssel lebt nur für einen Augenblick

```
PIN oder Finger  →  Tresor öffnen  →  Schlüssel ableiten  →  unterschreiben
                                                          →  Schlüssel überschreiben
                                                          →  Wörter überschreiben
```

**Der Finger ist kein schwächerer Weg, sondern derselbe.** `bioOeffnen`
holt den Tresorschlüssel aus der Hülle, die beim Einschalten mit der PIN
gefüllt wurde; der Finger ersetzt die sechs Ziffern, nicht den Schlüssel.
Wer die Biometrie eingeschaltet hat, hat diese Entscheidung schon fürs
Entsperren getroffen — sie beim Unterschreiben noch einmal zu verweigern
wäre Theater, kein Schutz. Beide Wege laufen durch dieselbe Funktion
(`WalletModel.unterschreiben`), damit sie nicht auseinanderlaufen können.

Das passiert in `finally`-Blöcken: Auch wenn unterwegs etwas schiefgeht,
bleibt nichts liegen. Zwischen zwei Zahlungen gibt es im Arbeitsspeicher
nichts zu holen — „entsperrt" heißt in dieser Wallet nur, dass Guthaben und
Verlauf sichtbar sind.

**Drei Gegenproben vor dem Absenden:**

1. Die abgeleitete Adresse muss der angezeigten entsprechen. Täte sie es
   nicht, ginge die Überweisung von einem Konto aus, das der Nutzer nie
   gesehen hat.
2. Die eigene Signatur wird nachgerechnet.
3. Die echte Größe darf die geschätzte nicht übersteigen — sonst wäre die
   Gebühr je Byte zu niedrig und die Zahlung bliebe liegen. Dann wird
   abgebrochen statt eingereicht.

---

## 4. „Angenommen" ist Schritt 2 von fünf

Aus `sendestatus.ts` der Vorlage, wörtlich übernommen, weil es stimmt:

> Eine erfolgreiche Antwort auf das Einreichen ist Schritt 2, nicht mehr.
> „Endgültig" gibt es nicht: Das Netz hat keine Checkpoints und keine Grenze
> für Reorganisationen.

```
1 auf dem Gerät unterschrieben
2 vom Knoten angenommen
3 in der Warteliste des Knotens
4 in einem Block
5 mindestens zwei Bestätigungen   (der Block selbst zählt als erste)
```

Der Ergebnisbildschirm zeigt alle fünf mit ihrem Stand. Wer dort „erledigt"
schreibt, sobald der Knoten genickt hat, lügt den Nutzer an.

Ablehnungsgründe werden übersetzt (`bad_nonce` → „Die laufende Nummer passt
nicht…"), der Code bleibt daneben stehen — wer nachfragen will, braucht ihn.

Bei einem **Verbindungsabbruch** steht ausdrücklich dort, dass der Kontostand
zu prüfen ist, bevor man es erneut versucht: Falls die Überweisung doch
ankam, würde ein zweiter Versuch mit derselben Nummer abgelehnt.

---

## 5. Die Adresse per QR-Code

Es gibt drei Wege, eine Empfängeradresse in das Feld zu bekommen: tippen,
einfügen, scannen. Der dritte ist der bequemste und darum der, bei dem am
genauesten hingesehen werden muss.

**Ein QR-Code ist kein Beweis.** Jeder kann einen drucken und an eine Wand
kleben. Darum wird nichts übernommen, was nicht `Address.isValid` besteht —
bech32m hat eine Prüfsumme, ein verfälschtes Zeichen fällt durch
(`AdresslesenTest`).

**Ein Betrag im Code wird verworfen, nicht ausgewertet.** Manche Programme
schreiben `yskar:ysr1…?amount=999`. Das Schema wird abgeschnitten, der
Parameter weggeworfen. Wie viel gesendet wird, entscheidet der Besitzer am
Gerät — nicht ein gedrucktes Muster. Und was danach im Prüfschritt steht,
ist dasselbe wie immer: die Adresse im Klartext, in Blöcken zu vier
Zeichen, zum Vergleichen.

**Nichts verlässt das Gerät.** CameraX liefert die Bilder, der reine
Java-Kern von ZXing entziffert sie — kein ML Kit, kein
Google-Code-Scanner, keine Play Services. Die Bilder leben so lange wie
ein `ImageProxy` und werden danach geschlossen; gespeichert wird nichts.

Die Kameraerlaubnis wird erst gefragt, wenn der Scanner geöffnet wird.
Wird sie verweigert, sagt der Bildschirm das und verweist auf die beiden
anderen Wege — er lässt den Nutzer nicht in einer Sackgasse stehen.

Der Weg vom Bild zum Text ist ohne Kamera geprüft: `AdresslesenTest`
zeichnet einen Code, so wie die App ihn auf dem Empfangen-Bildschirm
zeichnet, und liest ihn mit den Einstellungen des Scanners wieder.

Der Bildschirm selbst wurde am 10.11.2026 mit laufender Kamera
aufgenommen (`Bildschirmbilder.scanner`). Was dabei auffiel, steht in
[DESIGN.md](DESIGN.md) Abschnitt 9.

**Am selben Tag hat der Besitzer einen echten Code gescannt, und es ging.**
Das ist der Teil, den kein Test ersetzt: Dass die Kamera ein Muster auf
einem Bildschirm oder Papier wirklich trifft, hängt an Licht, Abstand und
Winkel, und davon weiß die JVM nichts. Unter welchen Bedingungen es
*nicht* mehr geht, ist damit allerdings auch nicht beantwortet — ein
gelungener Versuch ist ein Beleg, keine Grenze.

---

## 6. Was fehlt

- **Gebührenstufen.** Die Oberfläche nimmt immer `NORMAL`. Der Knoten meldet
  derzeit für alle drei Stufen denselben Satz, es gibt also nichts zu wählen
  — sobald Andrang herrscht, ändert sich das.
- **Adressbuch / Kontakte.**

---

## 7. Der erste echte Versand — durchgeführt im November 2026

Ein Kleinstbetrag von einer eigenen Adresse an eine zweite eigene, mit der
Mindestgebühr. Kennung, Adressen, Betrag und Block stehen hier mit
Absicht nicht: Sie gehören zu echten Konten, und die Kette ist öffentlich.

**Die Werte wurden vor dem Senden vorausgesagt und danach gemessen:** das
Guthaben des Absenders danach und das des Empfängers danach — beide auf
die letzte Stelle wie vorausgesagt.

Empfänger, Betrag, Gebühr und laufende Nummer im Mempool des Knotens waren
Zeile für Zeile das, was der Prüfschritt angezeigt hatte.

**Eine Beobachtung fürs Protokoll:** Kurz nach dem Absenden meldete der
Spiegel die Überweisung weder im Mempool noch in einem Block — eine Lücke von
rund einer Minute, in der sie scheinbar verschwunden war. Sie war es nicht;
sie kam im nächsten Block. Genau davor warnt `zahlungen.ts` der Vorlage mit
`nichtGelistet()`: nicht vor zwei Minuten als „nicht gelistet" werten. Unser
`Sendestatus` fängt es über die Markierung `gesehen` ab — ein Schritt, der
einmal erledigt war, springt nicht zurück.

---

## 8. Anleitung für weitere Testversände

Es gibt kein Testnetz, auf das wir ausweichen könnten. Deshalb:

1. Wallet in der App aus den zwölf Wörtern wiederherstellen.
2. „Aktualisieren" — das Guthaben muss stimmen, und erst damit ist die
   laufende Nummer bekannt.
3. **Kleinstbetrag an eine zweite eigene Adresse.** Nicht an eine fremde.
4. Im Prüfschritt die Adresse **Zeichen für Zeichen** vergleichen.
5. Nach dem Senden „Stand aktualisieren", bis Schritt 4 (in einem Block)
   erledigt ist. Das dauert bis zu zehn Minuten — so lang ist die Zielzeit
   eines Blocks.

Danach, und erst danach, ist die Wallet für echte Beträge brauchbar.
