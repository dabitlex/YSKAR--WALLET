# YSKAR Wallet

Die Wallet für Android: senden, empfangen, Verlauf, Kontakte. Kein Mining.

Dieses Repo ist privat. Die App läuft auf der Hauptkette (`yskar-main-1`).

## APK bekommen

GitHub baut bei jedem Push auf `main` eine APK.

1. Im Repo auf **Actions** gehen und den obersten Lauf „APK bauen“ öffnen.
2. Ganz unten unter **Artifacts** die Datei herunterladen. Es ist eine ZIP-Datei, darin liegt die APK.
3. Die APK an die Tester geben, zum Beispiel über Telegram.

Ein Tag `v0.1.0` erzeugt zusätzlich ein Release, an dem die APK direkt hängt.

Der Test-Schlüssel ist als Secret hinterlegt: Jeder Lauf erzeugt eine signierte APK (`yskar-wallet-test-<version>.apk`), die sich über die vorige installieren lässt.

Nur wenn die Secrets fehlen, entsteht ein **Debug-Bau** zum eigenen Testen. Sein Schlüssel wird einmal auf dem Runner erzeugt und liegt danach im Cache von GitHub Actions. Jede neue Debug-Fassung ist damit gleich signiert und lässt sich über die vorige installieren, die Wallet auf dem Gerät bleibt erhalten.

- Der Debug-Schlüssel steht nicht im Repo.
- GitHub löscht Cache-Einträge, die 7 Tage lang niemand gelesen hat. Liegt zwischen zwei Debug-Bauten mehr Zeit, legt der nächste Bau einen neuen Schlüssel an und meldet das mit einer Warnung. Dann muss die App einmal deinstalliert werden.
- Ob zwei Fassungen zusammenpassen, zeigt der Fingerabdruck der Signatur in der Zusammenfassung des Laufs: Er muss derselbe sein.
- Zum Verteilen an Tester ist der Debug-Bau nicht gedacht. Dafür den Test-Schlüssel hinterlegen (nächster Abschnitt). Der erste signierte Bau lässt sich nicht über einen Debug-Bau installieren.

## Test-Schlüssel zum Signieren anlegen

Einmalig. Der Schlüssel darf nie ins Repo und nie in einen Chat. Es sollte **nicht** der Schlüssel der bisherigen App sein.

Auf einem Rechner mit Java (`keytool` gehört zu jedem JDK und zu Android Studio):

```bash
keytool -genkeypair -v -keystore yskar-wallet-test.jks -alias yskar-wallet-test \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=YSKAR Wallet Test"
```

`keytool` fragt nach einem Passwort. Dasselbe Passwort gilt für Keystore und Schlüssel.

Dann die Datei als Text ausgeben:

```bash
# Linux, Raspberry Pi, macOS
base64 -w0 yskar-wallet-test.jks 2>/dev/null || base64 -i yskar-wallet-test.jks
```

```powershell
# Windows PowerShell: legt den Text in die Zwischenablage
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path "yskar-wallet-test.jks"))) | Set-Clipboard
```

Im Repo unter **Settings → Secrets and variables → Actions → New repository secret** vier Secrets anlegen:

| Name | Inhalt |
|---|---|
| `YSKAR_KEYSTORE_BASE64` | der lange Text aus dem Befehl oben |
| `YSKAR_KEYSTORE_PASSWORD` | das Passwort |
| `YSKAR_KEY_ALIAS` | `yskar-wallet-test` |
| `YSKAR_KEY_PASSWORD` | dasselbe Passwort |

Danach unter **Actions → APK bauen → Run workflow** einen Lauf starten. Die APK heißt dann ohne `-debug`.

**Die Datei `yskar-wallet-test.jks` und das Passwort sicher aufbewahren.** Geht der Schlüssel verloren, lässt sich keine neue Fassung mehr über eine installierte legen. Die Tester müssten deinstallieren und ihre Wallet mit den 12 Wörtern neu einrichten.

## Was die App tut und was nicht

- **Schlüssel:** Die 12 Wörter entstehen auf dem Gerät und verlassen es nie. Sie liegen mit der PIN verschlüsselt im geschützten Speicher von Android (Keystore).
- **Senden:** Jede Überweisung wird auf dem Gerät gebaut und signiert. An den Full Node gehen nur fertige Bytes.
- **Lesen:** Guthaben, Verlauf und Gebührenlage kommen vom Spiegel `yskar.vercel.app`.
- **Bestätigt** ist eine Zahlung ab dem ersten Block.
- **Kontakte und Anforderungen** liegen nur auf dem Gerät. Die 12 Wörter stellen sie nicht wieder her.
- **Anfordern** erzeugt einen Code `yskar:<adresse>?amount=…&memo=…` als QR und als Text zum Teilen. Wallets schicken sich untereinander nichts.
- **Kein Nachladen:** Die Oberfläche steckt fest in der APK.
- **Sichtschutz:** Solange die 12 Wörter zu sehen oder einzugeben sind, sperrt Android Bildschirmfotos.
- **Eigene Tastatur:** Kein Eingabefeld öffnet die Tastatur des Telefons. Betrag, Adresse, die 12 Wörter, Notiz, Name und Suche werden über die Tastatur der App getippt (`src/ui/tastatur.tsx`). Eine fremde Tastatur-App bekommt davon nichts mit. Grenzen: Geschrieben wird am Ende des Feldes, gelöscht von hinten. Emojis, Wischen und andere Schriften als die lateinische gibt es nicht.

Nicht enthalten: Mitteilungen bei Zahlungseingang, weitere Sprachen außer Deutsch und Englisch.

## Aufbau

| Ordner | Inhalt |
|---|---|
| `src/kern/` | Schlüssel, Adressen, Überweisungen. **Unverändert** aus `dabitlex/YSKAR` (`src/lib/core`), siehe `HERKUNFT.json`. Nicht von Hand ändern. |
| `src/wallet/` | Tresor, Biometrie, Kontakte, Anforderungen, Beträge, Notizen, QR-Codes |
| `src/netz/` | Verbindung zu Spiegel und Full Node |
| `src/geraet/` | Android-Funktionen: Speicher, Teilen, Vibrieren, Sichtschutz |
| `src/seiten/`, `src/ui/` | die Bildschirme und ihre Bausteine |
| `src/i18n/` | Deutsch und Englisch |
| `android/` | die Android-Hülle (Capacitor) |
| `tests/` | Tests, laufen vor jedem Bau |

## Prüfen und bauen

```bash
npm ci
npm test                             # Kern, Tresor, Beträge, Anforderungen
npm run build                        # Typprüfung und Oberfläche nach dist/
node werkzeug/buendel-pruefen.mjs    # nur Hauptkette, nur bekannte Gegenstellen
npm run dev                          # Oberfläche im Browser, zum Entwickeln
```

Die Tests in `tests/kern.test.ts` stellen sicher, dass dieselben 12 Wörter dieselbe Adresse ergeben wie in der bisherigen App und dass eine Überweisung Byte für Byte dieselbe ist. Schlagen sie fehl, baut GitHub keine APK.

## Den Kern aktualisieren

Ändert sich `src/lib/core` im Hauptrepo, die sechs Dateien neu kopieren, `HERKUNFT.json` (Commit und Prüfsummen) und `tests/vergleichswerte.json` mit dem Code des Hauptrepos neu erzeugen. Die Vergleichswerte nie mit dem Code dieses Repos erzeugen: Dann prüft der Test nichts mehr.
