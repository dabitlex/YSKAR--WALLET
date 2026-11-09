# Phase 1 – Technische Bestandsaufnahme

Stand: 09.11.2026 · Zweig `feature/yskar-finance-v3` · Grundlage: `main` @ `2f218d0`

Dieses Dokument ist eine **Messung, keine Einschätzung**. Jede Aussage ist entweder
im Code belegt (Pfad und Zeile) oder gegen das laufende Netz geprüft. Was nicht
geprüft werden konnte, steht unter [Offene Punkte](#9-offene-punkte-ungeprüft).

---

## 1. Was bereits zuverlässig funktioniert

**Geprüft, nicht übernommen.** Alle drei Prüfungen wurden auf diesem Stand ausgeführt:

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Tests | `npm test` | **78 / 78 bestanden**, 1,2 s |
| Bau der Oberfläche | `npm run build` | **erfolgreich**, `tsc --noEmit` ohne Fehler, 2,7 s |
| Bündelprüfung | `node werkzeug/buendel-pruefen.mjs` | **in Ordnung** – 25 Dateien, nur Hauptkette, nur bekannte Gegenstellen |

Inhaltlich belastbar und ohne Änderungsbedarf:

- **Kryptografie.** Nichts ist selbst gebaut. `@noble/curves` (Ed25519),
  `@noble/hashes`, `@scure/bip39`, `@scure/base` (bech32m) – alle auf feste
  Versionen genagelt (`2.4.0`, kein `^`), was bei Krypto-Abhängigkeiten richtig ist.
- **Schlüsselableitung.** BIP39 → SLIP-0010 für Ed25519, Pfad
  `m/44'/9077'/konto'/0'/index'` (`src/kern/wallet.ts`). SLIP-0010 ist korrekt
  gewählt: BIP32 funktioniert für Ed25519 nicht, nur gehärtete Ableitung ist möglich.
- **Kern ist verifizierbar unverändert.** `src/kern/HERKUNFT.json` nagelt alle
  acht Kerndateien per SHA-256 auf `dabitlex/YSKAR@92b4893`. `tests/kern.test.ts`
  prüft gegen `tests/vergleichswerte.json`, dass dieselben 12 Wörter dieselbe
  Adresse ergeben und eine Überweisung **byteweise identisch** ist.
- **Sicherer Speicher.** `src/geraet/speicher.ts` nutzt den Android Keystore
  über `@aparajita/capacitor-secure-storage`. Darin liegt der mit der PIN
  verschlüsselte Tresor (PBKDF2-SHA256, 400 000 Runden, AES-256-GCM,
  `src/wallet/tresor.ts`).
- **Keine Schlüssel in Logs.** `grep -rn "console\." src/` → **0 Treffer** im
  gesamten Quellbaum. Das ist nicht „wahrscheinlich sauber“, das ist nachgezählt.
- **Android-Härtung** (`android/app/src/main/AndroidManifest.xml`):
  `allowBackup="false"`, `fullBackupContent="false"`,
  `usesCleartextTraffic="false"`, `dataExtractionRules` schließt *alle* Domains
  für Cloud-Backup **und** Gerätewechsel aus. Berechtigungen minimal
  (INTERNET, CAMERA, USE_BIOMETRIC, VIBRATE, POST_NOTIFICATIONS); der von der
  WorkManager-Bibliothek mitgebrachte Vordergrunddienst **und** seine
  Berechtigung sind per `tools:node="remove"` wieder entfernt.
- **Kein Nachladen von Code.** `webDir: 'dist'`, Oberfläche steckt fest in der
  APK (`capacitor.config.ts`). `CapacitorHttp: { enabled: false }` – kein
  globales `fetch`-Umleiten, Netzanfragen laufen gezielt über `src/netz/api.ts`.
- **Sichtschutz.** Eigenes Plugin (`SichtschutzPlugin.java`); bei den 12 Wörtern
  erzwungen (`src/seiten/Einstieg.tsx:54`), app-weit optional abschaltbar.
- **Eigene Tastatur.** `src/ui/tastatur.tsx` – kein Eingabefeld öffnet die
  Tastatur-App des Telefons. Das ist eine echte Maßnahme gegen Keylogger und
  gehört zu den Dingen, die man nicht nachbaut, wenn man sie einmal hat.
- **Release-Pipeline.** `.github/workflows/apk.yml` baut bereits eine signierte
  APK, prüft Tag gegen `package.json`, erzwingt `npm test` **vor** dem Bau,
  monoton steigender `versionCode` (`1000 + Laufnummer`), Release mit Prüfsumme
  und Zertifikats-Fingerabdruck.

### Der Hard Fork ist bereits abgedeckt – der wichtigste Einzelbefund

Konsensfassung 6 aktiviert bei **Block 10 000**. Gemessener Stand des Netzes
heute: **Höhe 8 410**. Das sind **~1 590 Blöcke**, bei 600 s Zielzeit also
**rund 11 Tage** – die Aktivierung fällt mitten in dieses Projekt.

Die Wallet ist darauf vorbereitet, und zwar richtig:

- `src/wallet/fassung.ts` bestimmt die Überweisungsfassung **aus der Auskunft
  des Knotens** (`markt.fassung`), mit Höhenberechnung als Rückfall.
- `src/seiten/Senden.tsx:280` gibt `version: stand.fassung` an `buildTransfer`.
- Ist der Knoten nicht erreichbar, ist die Fassung `unbekannt` und **Senden
  wird gesperrt** – statt zu raten und eine ungültige Überweisung zu bauen.
  Das ist die konservativ korrekte Entscheidung.
- Die `sendepause` der letzten drei Blöcke vor der Aktivierung ist umgesetzt
  und in beiden Sprachen erklärt (`src/i18n/de.ts:156`).
- `tests/fassung.test.ts` (109 Zeilen) deckt das ab.

**Konsequenz für dieses Projekt: `src/wallet/fassung.ts` und der Sendepfad in
`Senden.tsx` werden nicht umgeschrieben, sondern unverändert übernommen.** Ein
Redesign, das diese Logik verliert, macht die Wallet ab dem 20.11.2026 unbenutzbar.

---

## 2. Was wiederverwendet werden kann

Unverändert übernehmen (keine fachliche Änderung, nur ggf. neue Aufrufer):

| Bereich | Dateien | Begründung |
|---|---|---|
| Kern | `src/kern/*` (8 Dateien) | Per Prüfsumme an das Hauptrepo gebunden. **Nicht von Hand ändern.** |
| Tresor / PIN-Bremse | `src/wallet/tresor.ts` | Format `yskar.tresor.v1` muss bleiben, sonst sind bestehende Wallets verloren. |
| Fassungslogik | `src/wallet/fassung.ts` | Siehe oben – sicherheitskritisch. |
| Sicherer Speicher | `src/geraet/speicher.ts` | |
| Biometrie | `src/wallet/biometrie.ts` | |
| Netzschicht | `src/netz/api.ts` | Wird **erweitert**, nicht ersetzt (siehe 3). |
| Beträge | `src/wallet/betrag.ts` | `bigint`, 8 Stellen, kein Fließkomma. |
| Kontakte / Anforderungen / QR | `src/wallet/{kontakte,anforderungen,qr}.ts` | |
| Eigene Tastatur | `src/ui/tastatur.tsx` | |
| QR-Scanner | `src/ui/scanner.tsx` | |
| Android-Hülle | `android/**` | Inkl. der beiden eigenen Plugins. |
| CI/Release | `.github/workflows/apk.yml` | Wird **erweitert** (Testbericht), nicht ersetzt. |

Technologiebasis bleibt: **React 19 + TypeScript + Vite 7 + Capacitor 8.** Für
den neuen Umfang geprüft und als tragfähig bewertet – Begründung in
[Abschnitt 7](#7-bewertung-der-technologiebasis).

---

## 3. Was erweitert werden muss

- **`src/netz/api.ts`** – `Kopf` liest heute nur `{ height, mempool }`. Der
  Spiegel liefert aber bereits deutlich mehr (gemessen, siehe 6):
  `difficulty`, `hashrate`, `minerHashrate`, `activeMiners`, `miningSessions`,
  `knoten`, `nextReward`, `totalSupply`, `maxSupply`, `targetBlockTime`,
  `tipHash`, `stateRoot`. **Alle vom Auftrag geforderten YSKAR-Netzdaten sind
  da und werden nur nicht gelesen.** Hier ist nichts zu erfinden, nur abzuholen.
- **Navigation** – heute drei Ziele (`start`, `verlauf`, `einstellungen`,
  `src/App.tsx:64`). Künftig vier Bereiche: Wallet, Markets, Activity, Profile.
- **Designsystem** – `src/stil.css` (1 369 Zeilen, 1 012 Klassen) ist sauber
  über CSS-Variablen in `:root` aufgebaut. Die Umstellung auf die neue
  Markenpalette ist deshalb überwiegend eine Retokenisierung, kein Neuschreiben.
  Achtung: Das heutige Konzept „Ruhe“ (03.11.2026) **unterdrückt Blau bewusst**
  (`--blau-hell:#F3F3F4`, Hauptknöpfe hell). Die neue Richtung will das
  Gegenteil – Blau als durchgehende Akzentfarbe. Das ist eine bewusste
  Richtungsumkehr und betrifft mehr als nur die Variablenwerte.
- **Einstellungen** – es fehlen: Fiat-Währung (EUR/USD), Zahlenformat,
  Kursanzeige, Benachrichtigungen pro Kategorie, Impressum.
- **`.github/workflows/apk.yml`** – Testbericht und Lint-Schritt ergänzen.

---

## 4. Was neu entwickelt werden muss

Vollständig neu, ohne Vorlage im Bestand:

1. **Market-Data-Schicht** – austauschbarer Anbieter hinter einem Interface,
   mit Cache, Request-Coalescing und Backoff (zwingend, siehe 6).
2. **Markets-Bereich** – Marktübersicht, Coin-Liste, Filter, Suche,
   Coin-Detailseite.
3. **Charts** – interaktiv, sieben Zeiträume. Keine Bilddiagramme.
4. **Watchlist** – lokal, mehrere Listen, Reihenfolge änderbar.
5. **Portfolio-Engine** – On-Chain-Bestand (YSR) **getrennt** von manuellen
   Positionen, Einstandswerte, G/V, Verteilung, Tagesveränderung.
6. **Activity-Ausbau** – Kategorien, Filter, Zeitraum, Suche über die
   vorhandene `verlauf`-Route.
7. **Fiat-Umrechnung** – mit expliziter Quellenkennzeichnung je Wert.

---

## 5. Sicherheitsrisiken

### Bestand: keine kritischen Funde

Geprüft wurden Schlüsselhaltung, Logging, Backup-Ausschluss, Netzkonfiguration,
Berechtigungen und Code-Nachladen. **Kein kritischer Fund.** Die ehrliche
Risikobewertung des Projekts steht sogar im Code selbst
(`src/wallet/tresor.ts`): Sechs Ziffern PIN = eine Million Möglichkeiten; wer
den Tresor mit Root-Zugriff abzieht, probiert sie in Stunden durch. Die PIN
schützt das entsperrte Telefon in fremder Hand, nicht gegen forensischen
Zugriff. Der eigentliche Schutz sind die aufgeschriebenen 12 Wörter. Diese
Einordnung ist korrekt und bleibt so.

Ein Punkt zur Beobachtung, kein Fund: Die PIN-Bremse
(`src/wallet/tresor.ts`, `BREMSE`) liegt in `localStorage`, nicht im sicheren
Speicher. Sie ist damit zurücksetzbar, wenn jemand die WebView-Daten löscht.
Da die Bremse nur das Durchprobieren am entsperrten Gerät verlangsamt und die
eigentliche Sicherheit an PBKDF2 mit 400 000 Runden hängt, ist das vertretbar –
es wird aber bei der Überarbeitung mitgenommen.

### Neue Risiken, die dieses Projekt einführt

Das sind die relevanten: Jede neue Fähigkeit vergrößert die Angriffsfläche
einer Wallet, die echtes Geld hält.

| # | Risiko | Maßnahme |
|---|---|---|
| R1 | **Markt-API als neuer Netzwerkpfad.** Ein fremder Dienst kommt an eine App, die Schlüssel hält. | Strikte Trennung: Die Marktschicht erhält **keinen** Zugriff auf Tresor, Seed oder Signierfunktionen. Nur `GET`, nur JSON, Antworten schemavalidiert vor Verwendung. |
| R2 | **Adress-Korrelation.** Markt-Anfragen und Wallet-Anfragen vom selben Gerät. | Kein Wallet-Bezug in Markt-Anfragen. Keine Adresse, kein Betrag, kein Identifikator verlässt das Gerät Richtung Marktanbieter. |
| R3 | **API-Schlüssel in der APK.** Verstößt gegen die Vorgabe und ist trivial auslesbar. | Kein Schlüssel im Build. Siehe Entscheidung in 6. |
| R4 | **Ausfall der Markt-API beeinträchtigt die Wallet.** | Getrennte Fehlerpfade. Die Wallet funktioniert vollständig ohne Marktdaten; Markets degradiert sichtbar, die Wallet nie. |
| R5 | **Erfundene Werte.** Ein Chart ohne Daten, ein Fiat-Betrag ohne Kurs. | Jeder Wert trägt seine Quelle und sein Alter. Fehlt ein Kurs, steht „kein Kurs verfügbar“ – **kein Platzhalter, keine Schätzung, keine Null**. |
| R6 | **Portfolio-Doppelzählung.** Manuelle YSR-Position neben echtem On-Chain-Bestand. | Positionstyp ist Pflichtfeld (`onchain` \| `manuell`). YSR ist als manuelle Position gesperrt. |
| R7 | **Vergrößerte Abhängigkeitsfläche.** Chart-Bibliotheken ziehen gern viel nach. | Jede neue Abhängigkeit begründet, auditiert, auf feste Version genagelt. `buendel-pruefen.mjs` erweitern. |

---

## 6. Benötigte externe Schnittstellen

### YSKAR – geprüft am laufenden Netz (09.11.2026)

Quelle für die Formate ist nicht Vermutung, sondern die Implementierung in
`dabitlex/YSKAR` (`src/app/api/v2/**/route.ts`, `src/lib/node/fullnode/ReadApi.ts`),
zusätzlich live abgefragt.

| Endpunkt | Status | Gemessene Antwort (Auszug) |
|---|---|---|
| `GET /api/v2/summary` | **200** | `height 8410`, `difficulty 370 115 996`, `hashrate 41,2 GH/s`, `minerHashrate 64,1 GH/s`, `activeMiners 19`, `miningSessions 30`, `knoten 22`, `mempool 0`, `nextReward 87 500 000 000`, `targetBlockTime 600` |
| `GET /api/v2/supply` | **200** | `circulating 6 291 772,61595773`, `max 21 000 000`, `minted 7 358 750`, `unspendable 1 066 977,38` |
| `GET /api/v2/fees` | **200** | `minFee 1680`, `mindestJeByte 10`, `hoehe 8410`, `fassung 1`, `basisBytes 168`, `sendepause false`, `aktivierung 10000` |
| `GET /api/v2/account/{adresse}` | vorhanden | Guthaben, Nonce, `pending`, `history` |
| `GET /api/v2/account/{adresse}/verlauf` | vorhanden | Seitenweise, Richtung/Suche |
| `POST /api/v2/tx` (Full Node) | vorhanden | Nur fertige, signierte Bytes |

Alles, was der Auftrag unter „YSKAR-spezifische Informationen" verlangt
(Blockhöhe, Netzstatus, aktive Miner, Hashrate, Mining-Infos, Bestätigungen,
geschätzte Gebühr), ist damit **real verfügbar**. Nichts davon muss erfunden
oder geschätzt werden.

### YSR hat keinen Marktpreis – belegt, nicht vermutet

Drei unabhängige Prüfungen:

1. `GET api.coingecko.com/api/v3/search?query=yskar` → `{"coins":[], …}` – **leer**.
2. `GET api.coingecko.com/api/v3/search?query=ysr` → **leer**.
3. Suche im gesamten Kern-Repo nach `preis|kurs|price|coingecko|coinmarketcap`:
   Es existieren **nur** Mengen-Endpunkte, ausdrücklich „für Datenseiten wie
   CoinMarketCap" (`src/lib/api/umlauf.ts`). **Keine Preisquelle, nirgends.**

**Daraus folgt zwingend:**
- Ein „YSR-Kurs" kann nicht angezeigt werden. Es gibt keinen.
- „Gesamtvermögen in EUR/USD" kann den YSR-Bestand **nicht** enthalten.
- Die Wallet zeigt den YSR-Bestand in **YSR**, dazu Menge, Umlauf und
  Netzdaten – und an der Stelle, wo ein Kurs stünde, die Angabe, dass YSR
  (noch) nicht an einer Börse notiert ist.
- Das Portfolio weist einen Fiat-Gesamtwert **nur über bepreiste Positionen**
  aus und kennzeichnet den YSR-Teil getrennt.

Das ist die einzige Umsetzung, die der eigenen Vorgabe „Erfinde keine
Marktdaten" entspricht. Sobald es eine offizielle Preisquelle gibt, wird sie
als Anbieter in dieselbe Schnittstelle gesteckt – die Architektur sieht das vor.

### Marktdaten: CoinGecko, mit Einschränkung

Gemessen, nicht aus der Dokumentation übernommen:

| Kriterium | CoinGecko (öffentlich, ohne Schlüssel) | CoinMarketCap |
|---|---|---|
| Ohne API-Schlüssel nutzbar | **ja** (gemessen: HTTP 200) | **nein** – Schlüssel bei jedem Aufruf |
| Zuverlässigkeit ohne Schlüssel | **schlecht**: 12 Aufrufe sequenziell → **6× 200, 6× 429**, `retry-after: 35` | n/a |
| Datenumfang je Coin | 28 Felder, inkl. `sparkline_in_7d` (168 Punkte) | vergleichbar |
| Logos | `coin-images.coingecko.com`, in der Antwort enthalten | eigenes CDN |
| Historie | `/coins/{id}/market_chart` | kostenpflichtig eingeschränkt |
| YSR gelistet | **nein** | **nein** (keine Preisquelle vorhanden) |

**Entscheidung: CoinGecko.** Begründung: Es ist der einzige der beiden, der
überhaupt **ohne eingebetteten API-Schlüssel** funktioniert – und „kein
API-Schlüssel in der APK" ist eine harte Vorgabe. CoinMarketCap verlangt für
jeden Aufruf einen Schlüssel und erzwingt damit entweder einen Verstoß gegen
diese Vorgabe oder einen eigenen Proxy.

**Aber:** Die gemessene 50-%-Fehlerquote ohne Schlüssel ist für ein Produkt
nicht akzeptabel. Die Messung kommt von einer geteilten Cloud-IP; auf dem
Handy hinter Carrier-NAT teilen sich ebenfalls viele Nutzer eine Adresse, das
Problem ist also real und nicht nur ein Artefakt dieser Umgebung.

Die Marktschicht wird darum von Anfang an so gebaut:
- Anbieter hinter einem Interface (`MarktAnbieter`), austauschbar.
- Cache mit „stale-while-revalidate", Request-Coalescing, exponentielles
  Backoff bei 429, letzte bekannte Werte mit Alterskennzeichnung.
- Kein Schlüssel im Build. Welche Bezugsquelle produktiv gilt, ist eine
  offene Entscheidung – siehe [Offene Punkte](#9-offene-punkte-ungeprüft).

---

## 7. Bewertung der Technologiebasis

Die Frage war, ob React + TypeScript + Vite + Capacitor für den neuen Umfang
noch sinnvoll ist. **Ja**, aus vier belegbaren Gründen:

1. **Der Kern ist TypeScript.** `src/kern/*` kommt per Prüfsumme aus dem
   Hauptrepo. Ein Wechsel auf native Android-Entwicklung (Kotlin) würde
   bedeuten, Ed25519-Ableitung, bech32m und die Transaktionsserialisierung
   **neu zu implementieren** – und damit genau die Garantie zu verlieren, die
   `tests/kern.test.ts` heute gibt (byteweise identische Überweisungen).
   Das ist das größte vermeidbare Risiko im ganzen Projekt. Nicht anfassen.
2. **Die Sicherheitsmaßnahmen hängen an dieser Hülle.** Eigene Tastatur,
   Sichtschutz-Plugin, Keystore-Anbindung, kein Code-Nachladen – alles
   vorhanden und geprüft.
3. **Der Umfang ist Listen, Charts und Formulare.** Das ist die Stärke dieser
   Basis, nicht ihre Grenze. Die gebaute Oberfläche liegt bei 556 kB
   (182 kB gzip) – reichlich Luft.
4. **Die Release-Pipeline funktioniert nachweislich.**

Bewusst **nicht** eingeführt: React Native (würde die Capacitor-Plugins und
die Hülle wertlos machen), ein UI-Framework wie MUI (Designvorgabe ist
eigenständig; ein Framework-Look wäre genau die „Baukasten-Optik", die
ausgeschlossen ist), ein State-Management-Paket (`src/zustand.tsx` mit React
Context reicht für diesen Umfang).

---

## 8. Entwicklungsumgebung – gemessen

### Diese Sitzung läuft **nicht** auf dem Smartphone

Das muss vor allem anderen klargestellt werden, weil der Auftrag davon ausgeht:

```
uname -a   → Linux vm 6.18.44-fc-v310 x86_64
whoami     → root
```

Das ist ein **Linux-x86_64-Container in der Cloud** (Claude Code on the web),
kein Android. Gemessener Werkzeugstand:

| Werkzeug | Status |
|---|---|
| git | 2.43.0 ✅ |
| Node.js | v22.23.4 ✅ |
| npm | 10.9.9 ✅ |
| Java / JDK | vorhanden ✅ |
| Gradle | vorhanden ✅ |
| Python | 3.13.18 ✅ |
| Chromium + Playwright | vorhanden ✅ (für automatisierte Oberflächentests) |
| **Android SDK** | **fehlt** ❌ |
| **adb** | **fehlt** ❌ |
| Freier Speicher | 30 GB ✅ |

**Ein nativer APK-Bau ist hier nicht möglich** (kein Android SDK, kein
angeschlossenes Gerät). Das ist keine Umgehung eines Build-Problems, sondern
die Beschreibung der Umgebung.

### Der Workflow, der tatsächlich funktioniert

Der Auftrag nennt den richtigen Weg selbst – und er **existiert bereits** und
ist erprobt (`.github/workflows/apk.yml`):

```
Cloud-Sitzung                GitHub Actions              Dein Smartphone
─────────────                ──────────────              ───────────────
Code schreiben
npm test        ──────┐
npm run build         │
buendel-pruefen       │
Playwright-Test       │
git push ─────────────┼───▶  npm ci
                      │      npm test   (bricht ab, wenn rot)
                      │      npm run build
                      │      buendel-pruefen
                      │      cap sync android
                      │      gradlew assembleRelease
                      │      signieren + Prüfsumme
                      └───▶  Artefakt / Release  ───────▶ APK laden,
                                                          installieren,
                                                          testen
```

Steuerung, Entwicklung und Auslösen des Baus laufen vollständig über das
Smartphone: Die Claude-App gibt die Anweisungen, GitHub baut, das Release
liegt als APK auf dem Telefon. Was auf dem Gerät passiert – installieren,
starten, Flows durchklicken – ist der manuelle Gerätetest aus Phase 9.

**Was in dieser Sitzung automatisiert prüfbar ist:** Unit-Tests (78),
Typprüfung, Bündelprüfung, Bau der Oberfläche, und über Chromium bei
Telefon-Viewport (412 × 915) die Oberfläche selbst – Screenshots,
Interaktionen, Lifecycle, lokale Speicherung.

**Was nur auf dem Gerät prüfbar ist und darum dokumentierter manueller Test
bleibt:** Biometrie, Android-Keystore, Sichtschutz, QR-Kamera, echte
Benachrichtigungen, Installation über eine ältere Fassung.

### Konkrete Schritte auf dem Handy

Nach jedem größeren Abschnitt:

1. **Actions → App bauen → Run workflow**, Zweig `feature/yskar-finance-v3`,
   Feld „veroeffentlichen" **leer** lassen. → Es entsteht eine signierte
   Test-APK, kein Release.
2. Lauf öffnen → unten **Artifacts** → ZIP laden → entpacken → APK öffnen →
   Installation erlauben.
3. Für ein echtes Release erst nach Phase 10: Version in `package.json`
   erhöhen, auf `main` bringen, Tag setzen.

> Hinweis: Die Test-APK vom Feature-Zweig ist mit demselben Release-Schlüssel
> signiert und lässt sich damit über die installierte Fassung legen. Die
> bestehende Wallet bleibt erhalten. Der `versionCode` (`1000 + Laufnummer`)
> steigt mit jedem Lauf, Android akzeptiert das Update.

---

## 9. Offene Punkte (ungeprüft)

Ehrlich getrennt von allem oben – das hier ist **nicht** geprüft:

1. **Bezugsquelle der Marktdaten produktiv.** Die keyless-Variante liefert
   gemessen 50 % Fehler. Die Alternativen (Demo-Schlüssel zur Laufzeit vs.
   eigener Proxy neben `yskar.vercel.app`) unterscheiden sich in Aufwand und
   Betrieb erheblich. **Entscheidung notwendig, bevor Phase 5 beginnt.**
2. **Rate-Limits mit Schlüssel.** Nicht gemessen, weil kein Schlüssel vorliegt.
   Zahlen aus der Anbieterdokumentation werden hier bewusst nicht zitiert,
   solange sie nicht selbst geprüft sind.
3. **Verhalten der Wallet ab Block 10 000.** Die Logik ist vorhanden und
   getestet, aber die Aktivierung hat noch nicht stattgefunden. Der Übergang
   muss am Tag der Aktivierung am Gerät beobachtet werden.
4. **Gerätekompatibilität.** minSdk 24 (Android 7) ist konfiguriert; welche
   Geräte tatsächlich getestet wurden, ist nicht dokumentiert.
5. **`POST /api/v2/tx`** wurde **nicht** ausgelöst. Eine Überweisung in der
   produktiven Kette wird nicht zum Testen gesendet.
