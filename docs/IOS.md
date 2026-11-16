# YSKAR Wallet auf dem iPhone

Stand: 16.11.2026 · Zweig `feature/yskar-finance-v3`

Die App ist ein Capacitor-Projekt. Oberfläche, Krypto-Kern, Markets,
Portfolio und Watchlist laufen auf iOS **unverändert** – es ist derselbe
Code. Was sich unterscheidet, ist die Hülle. Dieses Dokument sagt, was
davon betroffen ist, und verschweigt nichts.

---

## 1. Was ohne Änderung läuft

| | |
|---|---|
| Oberfläche, Navigation, Designsystem | React + CSS, identisch |
| Schlüssel, Adressen, Überweisungen | `src/kern/*` ist reines TypeScript |
| Markets, Charts, Portfolio, Watchlist | identisch |
| Sicherer Speicher | Keychain statt Keystore – **aber siehe 2.1** |
| Biometrie | Face ID / Touch ID statt Fingerabdruck |
| Teilen, Zwischenablage, Vibrieren, Browser | Capacitor-Plugins, beide Plattformen |

Alle sieben verwendeten Capacitor-Plugins unterstützen iOS. Geprüft an den
Paketen selbst, nicht aus dem Gedächtnis.

---

## 2. Was sich unterscheidet

### 2.1 Der Tresor darf das Gerät nicht verlassen — musste erzwungen werden

Auf Android regelt das die Hülle: `allowBackup="false"` und
`dataExtractionRules` schließen alles aus.

**Auf iOS gilt das nicht von allein.** Die Vorgabe des Keychains ist
`whenUnlocked`, und deren eigene Beschreibung lautet:

> *„Items with this attribute migrate to a new device when using encrypted
> backups."*

Dazu kommt die iCloud-Schlüsselbundsynchronisierung. Ohne Gegenmaßnahme
wäre die Zusage aus der README – *„die Wörter wandern nicht in
Cloud-Sicherungen"* – auf iPhones **schlicht falsch gewesen**.

Umgesetzt in `src/geraet/speicher.ts`, einmal beim Start, vor jedem Zugriff:

```
setSynchronize(false)                        kein iCloud-Schlüsselbund
setDefaultKeychainAccess(whenUnlockedThisDeviceOnly)   keine Sicherung
```

Schlägt das fehl, wirft der Speicher – es passiert nicht stillschweigend.

### 2.2 Bildschirmfotos lassen sich auf iOS **nicht** sperren

Das ist der größte Unterschied, und er lässt sich nicht wegprogrammieren.

| | Android | iOS |
|---|---|---|
| Bildschirmfoto verhindern | **ja** (`FLAG_SECURE`) | **nein** |
| Bildschirmaufnahme verhindern | **ja** | **nein** |
| Übersicht/App-Umschalter verdecken | ja | **ja** |
| Gemachtes Foto melden | nein | **ja** |

iOS hat keine öffentliche Schnittstelle, mit der eine App ein
Bildschirmfoto verhindern kann. Es kursieren Tricks über die verdeckte
Ebene eines sicheren Textfeldes; sie stützen sich auf undokumentiertes
Verhalten, brechen mit iOS-Fassungen und sind im App Store ein Risiko.
**In einer Wallet wird so etwas nicht eingebaut.**

`SichtschutzPlugin.swift` tut deshalb das, was iOS erlaubt:

1. **Übersicht verdecken** – geht die App in den Hintergrund, legt sie eine
   deckende Fläche in `#080D18` über den Inhalt. Im App-Umschalter ist
   nichts zu lesen. Das deckt den häufigsten Fall ab: jemand bekommt das
   Telefon kurz in die Hand.
2. **Bildschirmfoto melden** – verhindern kann iOS es nicht, aber die App
   erfährt davon und kann warnen, gerade wenn die zwölf Wörter zu sehen
   waren.

Die Oberfläche **fragt die Fähigkeit ab statt sie anzunehmen**
(`sichtschutzKann()` in `src/geraet/geraet.ts`). Auf einem iPhone darf
nirgends stehen, Bildschirmfotos seien gesperrt.

> **Noch offen:** Die Texte in `src/i18n/` und die Store-Beschreibung
> sagen heute „Keine Bildschirmfotos, solange die 12 Wörter zu sehen
> sind." Für iOS muss das umformuliert werden, bevor die App dort
> erscheint. Siehe Abschnitt 6.

### 2.3 Benachrichtigung bei Eingang — vorerst nicht auf iOS

Auf Android fragt eine `WorkManager`-Arbeit etwa alle fünf Minuten nach
neuen Eingängen (`EingangPlugin.java`, `EingangArbeit.java`).

iOS kennt dafür `BGAppRefreshTask`. Das System entscheidet aber selbst,
ob und wann es eine App weckt – nach Nutzungsgewohnheit, Akkustand und
Ladezustand. Ein verlässliches Intervall gibt es nicht; „etwa alle fünf
Minuten" wäre auf iOS ein Versprechen, das die Plattform nicht hält.

**Entscheidung:** Die erste iOS-Fassung kommt ohne diese Benachrichtigung.
Der Schalter wird dort nicht angeboten, statt eine Funktion zu zeigen, die
unzuverlässig arbeitet. Wer sie später will, braucht entweder
`BGAppRefreshTask` mit ehrlicher Beschreibung („gelegentlich") oder
echte Push-Benachrichtigungen über einen Server – und der müsste wissen,
welche Adresse zu welchem Gerät gehört. Das ist eine Datenschutzfrage und
keine rein technische.

### 2.4 Eigene Plugins müssen auf iOS von Hand angemeldet werden

Das wäre beinahe ein stiller Fehler geworden.

Capacitor registriert auf iOS die Plugins aus `packageClassList` in
`capacitor.config.json`. Diese Liste erzeugt die Kommandozeile beim
Synchronisieren – und sie durchsucht dafür **ausschließlich die
npm-Pakete** der Plugins, nie den Quellcode der App selbst
(`@capacitor/cli`, `util/iosplugin.js`, Funktion `getPluginFiles`).

Unser `SichtschutzPlugin` liegt in der App. Es hätte also nie in der Liste
gestanden, wäre nie registriert worden, und der Aufruf aus der Oberfläche
wäre ins Leere gelaufen – **ohne Fehlermeldung beim Bauen**. Aufgefallen
wäre es erst auf einem iPhone.

Gelöst wie auf Android, wo `MainActivity.java` dasselbe tut: Ein eigener
`ViewController` meldet das Plugin an.

```swift
class ViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(SichtschutzPlugin())
    }
}
```

Dazu zeigt `Main.storyboard` auf diese Klasse statt auf
`CAPBridgeViewController`.

**Und:** Das Projekt ist ein klassisches Xcode-Projekt (`objectVersion 60`)
ohne synchronisierte Ordner. Jede Swift-Datei muss an **vier** Stellen in
`project.pbxproj` stehen – `PBXBuildFile`, `PBXFileReference`, in der
Gruppe und in der Sources-Phase. Fehlt eine, wird die Datei stillschweigend
nicht übersetzt. Beide Dateien sind eingetragen und gegen `AppDelegate.swift`
als bekannt gute Referenz geprüft.

`npx cap sync ios` fasst `project.pbxproj` und `Main.storyboard` nicht an –
nachgemessen, nicht angenommen.

### 2.5 Kleinere Angleichungen

| Punkt | Android | iOS |
|---|---|---|
| Nur Hochformat | `screenOrientation="portrait"` | `UISupportedInterfaceOrientations` |
| Nur HTTPS | `usesCleartextTraffic="false"` | `NSAppTransportSecurity` |
| `yskar:`-Links | `intent-filter` | `CFBundleURLTypes` |
| Kamera | `uses-permission` | `NSCameraUsageDescription` |
| Biometrie | `USE_BIOMETRIC` | `NSFaceIDUsageDescription` |
| Mindestfassung | Android 7 (SDK 24) | iOS 15 |

Die eigene Tastatur der App funktioniert auf iOS genauso – sie ist reines
HTML und öffnet nie die Systemtastatur.

---

## 3. Was geprüft ist und was nicht

| | |
|---|---|
| iOS-Projekt erzeugt | **ja**, auf Linux (Capacitor 8 nutzt Swift Package Manager, kein CocoaPods) |
| Alle 7 Plugins für iOS erkannt | **ja** |
| `Info.plist` gültig und vollständig | **ja**, mit `plistlib` geprüft |
| TypeScript-Änderungen | **ja**, 183/183 Tests grün |
| Plugin-Anmeldung über `ViewController` | **ja**, Mechanismus im Capacitor-Quelltext nachgelesen |
| Beide Swift-Dateien im Xcode-Projekt | **ja**, alle vier Stellen gegen Referenz geprüft |
| `cap sync` zerstört die Änderungen nicht | **ja**, Prüfsummen verglichen |
| **Swift-Code übersetzt** | **NEIN – hier nicht prüfbar** |
| Verhalten auf einem echten iPhone | **NEIN** |

`SichtschutzPlugin.swift` ist geschrieben, aber auf einem Linux-Rechner
**nicht übersetzbar**. Swift braucht Xcode und macOS. Der erste echte
Übersetzungslauf passiert in `.github/workflows/ios.yml` auf einem
macOS-Läufer oder auf deinem iMac. Rechne damit, dass dabei noch etwas
anzupassen ist – das ist normal und kein Rückschritt.

---

## 4. Auf dem iMac loslegen

```bash
git clone https://github.com/dabitlex/YSKAR--WALLET.git
```

```bash
cd YSKAR--WALLET && git checkout feature/yskar-finance-v3 && npm ci
```

```bash
npm run build && npx cap sync ios
```

```bash
npx cap open ios
```

Der letzte Befehl öffnet Xcode. Dort einmal:

1. **App → Signing & Capabilities → Team** auswählen (auch eine kostenlose
   Apple-ID genügt für den Anfang).
2. Oben das Ziel auf einen Simulator stellen und **Run** drücken.

Für den Simulator braucht es **kein** Entwicklerkonto und keine Bezahlung.

---

## 5. Auf ein echtes iPhone

| Weg | Voraussetzung | Haltbarkeit |
|---|---|---|
| Xcode mit kostenloser Apple-ID | iPhone per USB am Mac | **7 Tage**, dann neu laden |
| Apple Developer Program | 99 USD im Jahr | 1 Jahr |
| TestFlight | Developer Program + Review | 90 Tage je Build |
| App Store | Developer Program + Review | dauerhaft |

Für regelmäßige Tests auf dem Gerät führt praktisch kein Weg am Developer
Program vorbei – die Sieben-Tage-Frist der kostenlosen Variante wird sonst
schnell lästig.

**Zum App Store:** Apple behandelt Krypto-Wallets gesondert. Eine
nicht-verwahrende Wallet ist grundsätzlich zulässig, aber die
Prüfrichtlinien sind eigen und ändern sich. Das ist vor einer Einreichung
zu klären und nicht aus dem Gedächtnis zu beantworten.

Ebenfalls zu klären: die **Ausfuhrerklärung zur Verschlüsselung**
(`ITSAppUsesNonExemptEncryption`). Die App verschlüsselt den Tresor mit
AES-GCM. Ob die Ausnahme greift, ist eine rechtliche Einschätzung – der
Eintrag ist deshalb **bewusst nicht gesetzt**, statt eine Zusicherung zu
geben, die niemand geprüft hat.

---

## 6. Offen, bevor iOS zu Nutzern geht

| # | Punkt |
|---|---|
| 1 | Swift-Code erstmals übersetzen (CI oder Xcode) |
| 2 | Texte zum Sichtschutz für iOS umformulieren – dort sind Bildschirmfotos **nicht** gesperrt |
| 3 | Schalter „Benachrichtigung bei Eingang" auf iOS ausblenden |
| 4 | App-Symbol und Startbildschirm für iOS setzen (`Assets.xcassets` trägt noch die Capacitor-Vorgaben) |
| 5 | Gerätetest nach `docs/GERAETETEST.md`, iOS-Abschnitte sinngemäß |
| 6 | App-Store-Richtlinien für Wallets prüfen |
| 7 | Ausfuhrerklärung zur Verschlüsselung klären |
