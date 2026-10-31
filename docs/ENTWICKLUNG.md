# YSKAR Wallet – Entwicklung

Technische Hinweise zum Bauen, Prüfen und Veröffentlichen. Die Beschreibung der App steht in der [README](../README.md).

## App bauen und veröffentlichen

Die App-ID ist `app.yskar.wallet`. Sie ist endgültig: Eine App mit anderer ID wäre für Android eine andere App.

GitHub baut bei jedem Push auf `main` und bei jedem Start von Hand (**Actions → App bauen → Run workflow**) eine signierte APK. Sie liegt im Lauf ganz unten unter **Artifacts** (als ZIP-Datei) und ist zum eigenen Testen gedacht.

**Veröffentlichen** – eine neue Fassung auf GitHub:

1. In `package.json` die Version erhöhen, z. B. `"version": "1.0.1"`, und auf `main` bringen.
2. Einen Tag mit genau dieser Version setzen und hochladen:
   ```bash
   git tag v1.0.1 && git push origin v1.0.1
   ```
   Oder ohne Tag von Hand: **Actions → App bauen → Run workflow**, Zweig `main`, im Feld „veroeffentlichen“ die Version eintragen (`1.0.1`). Der Tag wird dann beim Veröffentlichen angelegt.
3. GitHub baut die APK und legt unter **Releases** ein Release an: `yskar-wallet-1.0.1.apk`, ihre Prüfsumme und den Fingerabdruck der Signatur, auf Englisch und Deutsch.

Passt der Tag nicht zur Version in `package.json`, bricht der Lauf ab. Der **versionCode** ist `1000 + Laufnummer` und steigt mit jedem Lauf; Android installiert eine neue Fassung nur über eine ältere.

Damit andere das Release herunterladen können, muss das Repo öffentlich sein.

Nur wenn die Secrets fehlen, entsteht ein **Debug-Bau** zum eigenen Testen. Er wird nie veröffentlicht. Sein Schlüssel liegt im Cache von GitHub Actions, nicht im Repo; GitHub löscht ihn, wenn ihn 7 Tage niemand liest.

## Signierschlüssel

Jede Fassung ist mit demselben Schlüssel signiert. Nur so lässt sich ein Update über die installierte App legen, und nur so erkennen Nutzer am Fingerabdruck, dass die Datei echt ist.

**Geht der Schlüssel verloren, gibt es keine Updates mehr:** Nutzer müssten die App deinstallieren und ihre Wallet mit den 12 Wörtern neu einrichten. Die `.jks`-Datei und das Passwort deshalb zusätzlich offline aufbewahren. Der Schlüssel darf nie ins Repo und nie in einen Chat.

Er liegt als Secret im Repo (**Settings → Secrets and variables → Actions**):

| Name | Inhalt |
|---|---|
| `YSKAR_KEYSTORE_BASE64` | die `.jks`-Datei als Base64-Text |
| `YSKAR_KEYSTORE_PASSWORD` | das Passwort des Keystores |
| `YSKAR_KEY_ALIAS` | der Alias des Schlüssels |
| `YSKAR_KEY_PASSWORD` | das Passwort des Schlüssels |

Den Base64-Text einer `.jks`-Datei erzeugen:

```bash
# Linux, Raspberry Pi, macOS
base64 -w0 schluessel.jks 2>/dev/null || base64 -i schluessel.jks
```

```powershell
# Windows PowerShell: legt den Text in die Zwischenablage
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path "schluessel.jks"))) | Set-Clipboard
```

## Texte und Bilder

Unter `store/` liegen Beschreibung (Deutsch und Englisch), App-Symbol, Titelgrafik und Screenshots – für die Release-Seite, die Website und spätere App-Verzeichnisse.

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

- **Benachrichtigung bei Eingang** (auf Wunsch): Die App fragt im Hintergrund etwa alle 5 Minuten den Spiegel nach neuen Eingängen. Gemeldet wird nur, was in einem Block steht.

Nicht enthalten: weitere Sprachen außer Deutsch und Englisch.

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
