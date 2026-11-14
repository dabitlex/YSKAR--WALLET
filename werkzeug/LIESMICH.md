# Werkzeug

Sechs Scripts. Alle liegen hier, weil sie sonst in einem Temp-Ordner
verschwinden und beim nächsten Mal neu getippt werden müssen — mit
anderen Schwellen und anderen Zahlen.

| Script | Wofür |
|---|---|
| `bilder-holen.ps1` | Bauen, installieren, jeden Bildschirm zeichnen, PNGs nach `bilder\` holen, Hochformat prüfen |
| `messen.ps1` | Am Bildschirmfoto nachmessen: Platz für die Systemleisten, Füllung des QR-Codes |
| `startbilder.ps1` | Den echten App-Start einfangen — mehrere Aufnahmen in der ersten Sekunde |
| `icons-umwandeln.ps1` | Die SVG aus `design-neu\icons` zu Android-Vektoren `res\drawable\ic_….xml` machen |
| `fotos.ps1` | Die aktuellen Bildschirmbilder auf die Telefone in den Fotos der Einführung setzen |
| `freigabe.ps1` | Die Release-APK bauen, signieren und prüfen; Prüfsumme und Fingerabdruck für die Release-Seite |

```powershell
.\werkzeug\bilder-holen.ps1
.\werkzeug\bilder-holen.ps1 -NurHolen      # ohne neu zu bauen und zu installieren
.\werkzeug\messen.ps1 bilder\*.png
.\werkzeug\messen.ps1 bilder\06_empfangen.png -QrFuellung
.\werkzeug\startbilder.ps1
.\werkzeug\icons-umwandeln.ps1
.\werkzeug\fotos.ps1                       # nach bilder-holen.ps1
.\werkzeug\fotos.ps1 -Vorschau             # nur ansehen, nach build\fotos
.\werkzeug\freigabe.ps1 -Schluessel D:\sicher\yskar.jks
```

Die drei Scripts für das Gerät setzen `JAVA_HOME` und `ANDROID_HOME`
selbst, wie `android.ps1` im Elternverzeichnis es tut, und prüfen
`$LASTEXITCODE` statt sich auf `$ErrorActionPreference = 'Stop'` zu
verlassen: Gradle und `adb` schreiben auf stderr, und das wäre sonst
jedes Mal ein Abbruch.

**Was `bilder-holen.ps1` nicht kann**: Das Telefon wach und entsperrt
halten und die Kameraerlaubnis erteilen. Beides verweigert HyperOS über
ADB (`INJECT_EVENTS`, `GRANT_RUNTIME_PERMISSIONS`) und muss am Gerät
selbst geschehen. Jede Installation verlangt dort ohnehin einen Tipp.

Es sieht aber nach, bevor es etwas anfasst: Ist das Display aus oder das
Telefon gesperrt, hört es mit einer klaren Meldung auf. Und es ersetzt
die Bilder in `bilder\` erst, wenn jeder Bildschirm gezeichnet und jedes
Bild angekommen ist — ein gescheiterter Lauf lässt die alten Bilder, wie
sie sind. `LIESMICH.md` daneben rührt es nie an.

Zwei Fehler, die in der ersten Fassung steckten und die man in
PowerShell leicht wieder macht:

- Eine Funktion namens `Adb`, die `adb` aufruft, ruft sich selbst auf.
  PowerShell unterscheidet keine Groß- und Kleinschreibung und zieht die
  Funktion dem Programm vor. Darum `RufeAdb` und der volle Pfad zu
  `adb.exe`.
- Eine Funktion, die ein Programm startet und danach `$LASTEXITCODE`
  zurückgibt, gibt in Wahrheit die Ausgabe des Programms UND die Zahl
  zurück. `-ne 0` ist darauf immer wahr. Darum `| Out-Host`.

**Was `messen.ps1` nicht kann**: entscheiden, ob ein Bildschirm, dessen
Inhalt bis an die untere Kante reicht, absichtlich füllt oder überläuft.
Siehe `bilder\LIESMICH.md`.

**Was `freigabe.ps1` nicht kennt**: das Passwort des Schlüssels.
`apksigner` fragt selbst danach, verdeckt, und das Script reicht es weder
durch noch hebt es etwas auf. Es weigert sich, wenn im Projekt
ungesicherte Änderungen liegen, wenn der Schlüssel im Projektordner liegt
und wenn es der Debug-Schlüssel von Android ist. Was es ablegt
(`freigabe\`), steht in `.gitignore`; die `mapping-<Version>.txt` darin
gehört zu genau diesem Release und wird aufgehoben, nicht veröffentlicht.

**Wie `fotos.ps1` arbeitet, und was es nicht kann**: Je Foto liegt unter
`fotos\` ein Grundbild (`grund_N.jpg`, das Foto mit geschwärztem Display)
und eine Maske (`maske_N.png`, wo ein Finger vor dem Display liegt).
`fotos\Einsetzen.java` verzerrt das Bildschirmbild auf die vier Ecken des
Displays, rundet die Ecken, zeichnet weich bis zur Schärfe des Fotos,
legt den Schatten des Fingers darauf und lässt den Finger davor stehen.
Die Leiste des Telefons (Uhrzeit, Empfang, Akku) zeichnet es selbst dazu
– die Bildschirmbilder zeigen nur die App.

Die Ecken der Displays sind gemessen, nicht geschätzt: an
Helligkeitsprofilen quer über jede Kante. Das Display endet einige
Bildpunkte VOR dem hellen Metallrahmen; wer nach Augenmaß den Rahmen
trifft, setzt das Bild bis zu 13 Bildpunkte zu breit. Die Zahlen stehen
im Script.

Es weigert sich, wenn die Bildschirmbilder älter sind als der Testrahmen
(`bilder\stand.txt`). Ein Grundbild macht es nur aus dem ursprünglichen
Foto, erkannt an der Prüfsumme – aus einem schon zusammengesetzten Foto
hielte die Fingersuche den warmen Hauptknopf für Haut. Und es beurteilt
nicht, ob das Ergebnis gut aussieht: ansehen, dann sichern.
