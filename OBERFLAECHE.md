# Die Oberfläche

Jetpack Compose, Material 3, deutsch. Acht Bildschirme, ein `when` über den
Zustand im `WalletModel` — für diese Tiefe braucht es keine
Navigationsbibliothek, und der Zustand bleibt an einer Stelle statt in
Argumenten verstreut.

| Bildschirm | Zweck |
|---|---|
| Einstieg | neu anlegen oder aus zwölf Wörtern wiederherstellen |
| Wörter zeigen | die zwölf Wörter, zum Abschreiben |
| Gegenprobe | drei zufällige Stellen abtippen |
| Wiederherstellen | zwölf Wörter eingeben, mit Prüfsummenkontrolle |
| PIN festlegen | sechs Ziffern, zweimal |
| Sperre | PIN oder Fingerabdruck |
| Wallet | Guthaben, Adresse, letzte Bewegungen |
| Empfangen / Verlauf / Mehr | QR-Code, Blättern, Einstellungen, Selbsttest |

---

## 1. Fünf Entscheidungen, die Sicherheit betreffen

**Entsperrt heißt nicht: Schlüssel im Speicher.** Nach dem Entsperren prüft
die App die PIN, **überschreibt die zwölf Wörter sofort** und behält nur die
Adresse — die ist öffentlich. Zwischen zwei Zahlungen gibt es im
Arbeitsspeicher nichts zu holen. Zum Unterschreiben wird später erneut
entsperrt. Das ist eine Unbequemlichkeit mit Gegenwert.

**Eigenes Ziffernfeld, keine Systemtastatur.** Eine fremde Tastatur sieht
jede Eingabe, schlägt Wörter vor und füllt Felder automatisch aus. Für die
PIN einer Wallet ist das die falsche Stelle zum Sparen. Bei der
Wörter-Eingabe, wo eine Tastatur unvermeidlich ist, steht der Feldtyp auf
`Password` — das schaltet Vorschläge und das Lernen des Wortschatzes ab, die
Anzeige bleibt trotzdem offen.

**`FLAG_SECURE`, solange Geheimes sichtbar ist.** Auf den Wörter-Seiten sind
Bildschirmfotos, Aufnahmen und das Vorschaubild in der App-Übersicht
gesperrt. Wer das vergisst, legt die zwölf Wörter in jeden Screenshot-Ordner
und in jedes Galerie-Backup. Nur dort, nicht in der ganzen App: Eine Adresse
darf man fotografieren dürfen.

**Der Sperrbildschirm zeigt die Adresse, nicht das Guthaben.** Wer das
Telefon in die Hand nimmt, soll nicht sehen, ob sich das Zugreifen lohnt.

**Die Zwischenablage ist als sensibel markiert.** Android 13 und neuer zeigt
kopierte Adressen dann nicht in der Vorschau an.

---

## 2. Ein Fehler, den erst der Test gefunden hat

`sperren()` hat `laedtKonto` nicht zurückgesetzt. Wer sperrte, **während
gerade Kontodaten geladen wurden**, bekam ein totes Ziffernfeld: Die Tasten
hingen an derselben Markierung und blieben dauerhaft deaktiviert — die
Wallet war nicht mehr zu entsperren, bis man die App neu startete.

Ursache war, dass ein Feld zwei Dinge bedeutete: „das Netz lädt" und „der
Tresor rechnet". Jetzt sind es zwei (`laedtKonto` und `arbeitet`), und
`sperren()` räumt beide.

Von Hand wäre das kaum aufgefallen — man tippt nicht in der halben Sekunde
auf „Sperren", in der die erste Abfrage läuft. Der Test tut genau das, weil
er schneller ist als ein Mensch.

---

## 3. Warum instrumentierte Tests, und was HyperOS dagegen hatte

Die Oberfläche lässt sich auf diesem Telefon **nicht von außen bedienen**:

```
adb shell input swipe …
java.lang.SecurityException: Injecting input events requires
the caller to have the INJECT_EVENTS permission.
```

Ein instrumentierter Test läuft im Prozess der App und darf tippen. Bis er
lief, waren drei Hürden zu nehmen — alle drei in `ANDROID_DEVELOPMENT_SETUP.md`
festgehalten:

1. **`adb shell input` gesperrt** (oben). Deshalb überhaupt dieser Weg.
2. **Gradles Testinstallation gesperrt.** `connectedDebugAndroidTest`
   installiert mit „alle Berechtigungen gewähren", und dafür fehlt dem
   Shell-Nutzer die Erlaubnis
   (`INSTALL_GRANT_RUNTIME_PERMISSIONS`). Umweg: beide APKs mit
   `adb install -r -d` einspielen und
   `adb shell am instrument -w …` direkt aufrufen.
3. **Espresso 3.5.0 passt nicht zu Android 17.** Der Compose-Testrahmen
   zieht die Fassung von 2022 herein; sie ruft per Reflexion
   `android.hardware.input.InputManager.getInstance()` auf, das es nicht
   mehr gibt — jeder Test scheitert beim Start der Activity. Behoben, indem
   `androidx.test:core/runner/monitor` und `espresso-core` ausdrücklich auf
   die aktuellen stabilen Fassungen gesetzt werden.

Ergebnis: **3 Oberflächentests, 17 Sekunden.** Sie legen eine Wallet an,
lesen die zwölf Wörter vom Bildschirm ab, füllen die Gegenprobe, setzen die
PIN, sperren, entsperren, prüfen die falsche PIN und lehnen Wörter mit
falscher Prüfsumme ab.

**Der Test löscht den Tresor des Geräts, bevor er startet.** Auf einem
Telefon mit echtem Guthaben darf er nicht laufen.

Was er nicht kann: die Biometrie. Der Systemdialog gehört nicht zur App, und
einen Fingerabdruck kann kein Test auflegen. Dafür gibt es den Selbsttest
unter „Mehr" mit Knopf.

---

## 4. Sprache

Die Oberfläche ist deutsch, mit **richtigen Umlauten**. Das klingt
selbstverständlich, war es aber nicht: Der Code vermeidet Umlaute in
Kommentaren, und diese Gewohnheit war mir in die angezeigten Texte
gerutscht — „Diese Wallet gehoert dir allein" stand so auf dem Gerät.
Korrigiert wurde ausschließlich innerhalb der Textliterale; Kommentare
bleiben ASCII.

Mehrsprachigkeit fehlt. Die Vorlage hat `de` und `en`; hier stehen die Texte
fest im Code. Das ist für den nächsten Schritt kein Hindernis, aber vor einer
Veröffentlichung nachzuholen.

---

## 5. Was fehlt

- **Senden.** Bewusst zuletzt. Der Kern kann es, die Netzschicht kann es,
  die Oberfläche noch nicht — und der erste echte Versand gehört ans Ende,
  mit Kleinstbetrag.
- **QR-Code lesen.** Für das Senden nötig (Kamera, `jsQR`-Gegenstück).
- **Letzter bekannter Stand.** Ohne Netz zeigt die App „—" statt des zuletzt
  bekannten Guthabens. Die Vorlage hat dafür `wallet/letzterstand.ts`.
- **Automatisches Sperren** nach Zeit im Hintergrund.
- **Kontakte**, Notiz beim Senden, Adressbuch.
