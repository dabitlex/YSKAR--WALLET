# Phase 2 – Produktarchitektur

Stand: 09.11.2026 · Zweig `feature/yskar-finance-v3`
Grundlage: [`BESTANDSAUFNAHME.md`](BESTANDSAUFNAHME.md)

---

## 1. Leitsätze

Fünf Regeln, aus denen sich der Rest ergibt:

1. **Die Wallet ist unantastbar.** Kein neues Modul darf den Sendepfad, den
   Tresor oder den Kern berühren. Fällt die Markt-API aus, merkt die Wallet
   das nicht.
2. **Schlüssel bleiben im Wallet-Teil.** Die Marktschicht hat keinen
   Importpfad zu Tresor, Seed oder Signierfunktionen – und das wird getestet,
   nicht nur vereinbart.
3. **Jeder Wert trägt seine Herkunft.** Ein Preis ohne Quelle und ohne Alter
   wird nicht angezeigt. Fehlt ein Wert, steht das da.
4. **YSKAR-Netz und Marktanbieter sind unabhängig.** Getrennte Caches,
   getrennte Fehlertypen, getrennte Wiederholungsregeln.
5. **Austauschbarkeit vor Bequemlichkeit.** Der Marktanbieter steckt hinter
   einem Interface. Ein Wechsel kostet eine Datei.

---

## 2. Modulkarte

```
src/
├── kern/            UNVERÄNDERT · Krypto, Adressen, Transaktionen
│                    Prüfsummengebunden an dabitlex/YSKAR. Nicht anfassen.
├── wallet/          BESTAND · Tresor, Biometrie, Kontakte, Beträge, Fassung
│                    fassung.ts + Sendepfad bleiben unverändert (Hard Fork).
├── netz/            ERWEITERT · YSKAR-Node
│   ├── api.ts         bestehend: Konto, Verlauf, Gebühren, Senden
│   └── netzdaten.ts   NEU: summary + supply (Höhe, Hashrate, Miner, Belohnung)
├── markt/           NEU · externe Marktdaten
│   ├── typen.ts       Coin, Kurs, Chartreihe, Marktüberblick
│   ├── anbieter.ts    Interface MarktAnbieter
│   ├── coingecko.ts   Adapter
│   ├── cache.ts       stale-while-revalidate, Coalescing, Backoff
│   └── schluessel.ts  Demo-Schlüssel im sicheren Speicher
├── portfolio/       NEU
│   ├── modell.ts      Position: quelle 'onchain' | 'manuell'
│   ├── rechnen.ts     Bewertung, G/V, Verteilung, Tagesveränderung
│   └── lager.ts       lokale Ablage
├── watchlist/       NEU · mehrere Listen, Reihenfolge, lokal
├── diagramm/        NEU · eigene interaktive SVG-Charts
├── geraet/          BESTAND · Speicher, Teilen, Vibrieren, Sichtschutz
├── seiten/          ERWEITERT · + Markets, CoinDetail, Portfolio, Watchlist
├── ui/              ERWEITERT · Designsystem-Komponenten
└── i18n/            ERWEITERT · de / en
```

### Erlaubte Abhängigkeitsrichtung

```
seiten/  ──▶  ui/  ──▶  diagramm/
   │
   ├──▶ wallet/ ──▶ kern/          (Schlüssel, Signieren)
   ├──▶ netz/                      (YSKAR-Node)
   ├──▶ markt/                     (Marktdaten)
   └──▶ portfolio/ ──▶ markt/ + netz/
```

**Verboten, und per Test geprüft:**

| Verbot | Warum |
|---|---|
| `markt/*` importiert `wallet/tresor`, `kern/wallet`, `wallet/biometrie` | Marktschicht darf nie an Schlüsselmaterial |
| `markt/*` importiert `netz/api` | Markt und Kette bleiben unabhängig |
| `netz/*` importiert `markt/*` | dito, andere Richtung |
| `kern/*` importiert irgendetwas aus `src/` außer `kern/*` | Prüfsummenbindung |

Umgesetzt als `tests/grenzen.test.ts`: liest die Importzeilen und schlägt
fehl, wenn eine Grenze verletzt wird. Eine Vereinbarung, die niemand prüft,
hält genau bis zum ersten eiligen Commit.

---

## 3. Navigation

Vier Bereiche, Bottom-Navigation. Wallet ist der Einstieg und bleibt der
Schwerpunkt.

```
┌─────────┬─────────┬──────────┬─────────┐
│ Wallet  │ Markets │ Activity │ Profile │
└─────────┴─────────┴──────────┴─────────┘
```

| Bereich | Einstieg | Tiefere Seiten |
|---|---|---|
| **Wallet** | Gesamtübersicht, YSR-Bestand, Netzstatus, letzte Zahlungen | Senden, Empfangen, Kontakte, Signieren, Transaktionsdetail |
| **Markets** | Marktüberblick, Coin-Liste, Suche, Filter | Coin-Detail (Chart, Kennzahlen, Beschreibung), Watchlist, Portfolio |
| **Activity** | Verlauf mit Kategorien, Filter, Zeitraum, Suche | Transaktionsdetail |
| **Profile** | Sicherheit, Darstellung, Netz, App-Info | PIN ändern, Wiederherstellungsphrase, Adressen, Datenschutz |

Die bestehende Navigation (`src/App.tsx`, `useNav`) kennt Reiter plus Tiefe
und Zurück-Taste. Sie wird um einen Reiter erweitert, nicht ersetzt.

---

## 4. Die Wallet-Seite – was wirklich angezeigt werden kann

Belegt aus der Live-Messung (`BESTANDSAUFNAHME.md` §6). Nichts davon ist
geschätzt.

| Anzeige | Quelle | Feld |
|---|---|---|
| YSR-Bestand | `/api/v2/account/{adr}` | `balance` |
| Blockhöhe | `/api/v2/summary` | `height` |
| Netz-Hashrate | `/api/v2/summary` | `hashrate` |
| Aktive Miner | `/api/v2/summary` | `activeMiners` |
| Mining-Sessions | `/api/v2/summary` | `miningSessions` |
| Knoten im Netz | `/api/v2/summary` | `knoten` |
| Nächste Blockbelohnung | `/api/v2/summary` | `nextReward` |
| Umlaufmenge / Höchstmenge | `/api/v2/supply` | `circulating` / `max` |
| Geschätzte Netzgebühr | `/api/v2/fees` | `stufen`, `mindestJeByte` |
| Bestätigungen | berechnet | `spitze − hoehe + 1` |
| Wartende Zahlungen | `/api/v2/account/{adr}` | `pending` |

**Der YSR-Kurs wird nicht angezeigt, weil es keinen gibt.** An seiner Stelle
steht die Angabe, dass YSR noch nicht an einer Börse notiert ist, zusammen mit
den Mengen- und Netzdaten, die es tatsächlich gibt.

### Entscheidung: Getrennter Ausweis

Gewählt, um keine Zahl zu erfinden:

```
┌────────────────────────────────────┐
│  YSR-Bestand                       │
│  1 234,567 890 12 YSR              │  ← groß, die Hauptzahl
│  noch nicht an einer Börse notiert │  ← an der Kursstelle, ehrlich
├────────────────────────────────────┤
│  Netz    #8 410 · 41,2 GH/s · 19 M │
├────────────────────────────────────┤
│  Weitere Positionen      1 842,10 €│  ← nur bepreiste Positionen
│  (Portfolio, 7 Positionen)         │
└────────────────────────────────────┘
```

Es gibt **bewusst keine** einzige Gesamtsumme über beides. Eine Summe aus
„1 234 YSR + 1 842 €" wäre entweder falsch oder erfunden.

---

## 5. Marktschicht

### Interface

```ts
export interface MarktAnbieter {
  readonly name: string;
  ueberblick(): Promise<Marktueberblick>;
  liste(o: ListeOptionen): Promise<CoinKurz[]>;
  detail(id: string): Promise<CoinDetail>;
  verlauf(id: string, zeitraum: Zeitraum): Promise<Chartreihe>;
  suche(text: string): Promise<CoinTreffer[]>;
}
```

Jeder gelieferte Wert ist umhüllt:

```ts
export interface MitHerkunft<T> {
  wert: T;
  quelle: 'coingecko' | 'yskar-node' | 'eigene-eingabe';
  standVon: number;      // Unix-Sekunden
  veraltet: boolean;     // über der Frische-Schwelle
}
```

Damit kann die Oberfläche nie versehentlich einen alten Kurs als aktuell
zeigen – die Information steckt im Typ, nicht in einer Konvention.

### Zuverlässigkeit – die Antwort auf die gemessenen 429er

Vier Maßnahmen in `markt/cache.ts`, in dieser Reihenfolge wirksam:

1. **Request-Coalescing.** Gleiche Anfrage zur gleichen Zeit → ein Netzaufruf.
   Scrollen in einer Liste erzeugt damit keine Lawine.
2. **Stale-while-revalidate.** Es wird sofort der letzte bekannte Stand
   angezeigt (mit Altersangabe) und im Hintergrund aktualisiert. Die
   Oberfläche wartet nie auf das Netz.
3. **Exponentielles Backoff bei 429**, `retry-after` wird respektiert.
   Nach einem 429 wird der betroffene Endpunkt kurz nicht erneut befragt.
4. **Persistenter Cache.** Der letzte Stand überlebt den App-Neustart, damit
   beim Öffnen sofort etwas da ist.

Frische-Schwellen (gewählt nach Datenart, nicht geraten):

| Daten | Frisch bis | Begründung |
|---|---|---|
| Kurse / Liste | 60 s | Kursanzeige soll leben, aber nicht flackern |
| Marktüberblick | 5 min | globale Kennzahlen bewegen sich langsam |
| Coin-Detail (Stammdaten) | 24 h | Beschreibung und Links ändern sich kaum |
| Chartreihe | nach Zeitraum: 1 h → 2 min, 1 J → 6 h | längere Reihen brauchen keine Sekundenfrische |

### Schlüssel – Entscheidung: Demo-Schlüssel im sicheren Speicher

- **Kein Schlüssel im Build.** `markt/schluessel.ts` liest ihn aus dem
  Android Keystore (`src/geraet/speicher.ts`), Ort `yskar.markt.schluessel`.
- Ohne Schlüssel läuft die App **trotzdem** – gegen die öffentliche API, mit
  sichtbarem Hinweis auf die eingeschränkte Zuverlässigkeit.
- Eingabe in Profile → Marktdaten. Der Schlüssel wird nie geloggt, nie
  angezeigt (nur die letzten vier Zeichen), nie an YSKAR gesendet.
- Der Schlüssel geht ausschließlich an `api.coingecko.com`, als Header
  `x-cg-demo-api-key`.

---

## 6. Portfolio – Doppelzählung strukturell ausgeschlossen

Die Trennung steckt im Typ, nicht in einer Prüfung:

```ts
export type Position =
  | { quelle: 'onchain';  kette: 'yskar'; adresse: string;  symbol: 'YSR' }
  | { quelle: 'manuell';  id: string; coinId: string; symbol: string;
      menge: string;            // Dezimaltext, nie number
      einstandJeStueck: string | null;
      waehrung: 'EUR' | 'USD';
      angelegt: number };
```

- Eine `onchain`-Position hat **keine** Mengenangabe – die kommt immer vom
  Knoten. Sie ist damit nicht von Hand verfälschbar.
- **YSR ist als manuelle Position gesperrt.** Beim Anlegen abgewiesen, mit
  Begründung. Sonst stünde der echte Bestand neben einem erfundenen.
- Mengen sind **Dezimaltext**, Berechnung in `bigint`-Einheiten. Kein
  `number` an Geldwerten – derselbe Grundsatz wie in `kern/params.ts`.

Berechnet wird (`portfolio/rechnen.ts`):
Fiat-Gesamtwert nur über bepreiste Positionen · Verteilung · Gewichtung ·
Einstand und G/V je Position und gesamt · Tagesveränderung.

**Was nicht berechnet wird:** eine Gesamtsumme, die YSR enthält, und eine
historische Portfolio-Entwicklung, die über den Zeitpunkt der ersten
Positionsanlage zurückreicht. Beides wäre rekonstruiert, nicht gemessen.

---

## 7. Charts – eigene Umsetzung, bewusst keine Bibliothek

Geprüft wurden Chart-Bibliotheken (Recharts, lightweight-charts). **Entscheidung:
eigene SVG-Umsetzung in `src/diagramm/`.** Vier Gründe:

1. **Designvorgabe.** Gefordert ist eine eigenständige Identität ohne
   Baukasten-Optik. Jede Bibliothek bringt ihre eigene visuelle Sprache mit,
   gegen die man dann anstylt.
2. **Bündelgröße.** Recharts zieht D3-Module nach und liegt im dreistelligen
   kB-Bereich. Die App liegt heute bei 182 kB gzip; das wäre ein erheblicher
   Aufschlag für Linien und Flächen.
3. **Abhängigkeitsfläche.** In einer Wallet ist jede Abhängigkeit ein
   Lieferkettenrisiko. `werkzeug/buendel-pruefen.mjs` existiert genau deshalb.
4. **Der Bedarf ist überschaubar.** Linie mit Fläche, Mini-Chart, Touch-Scrub
   mit Fadenkreuz, sieben Zeiträume. Das ist eigener Code in überschaubarem
   Umfang, dafür vollständig unter Kontrolle und ohne fremdes Markup.

Umfang: `linie.tsx` (interaktiv, Touch-Scrub), `mini.tsx` (Sparkline für
Listenzeilen), `achsen.ts` (Skalierung, Ticks), `pfad.ts` (Pfadberechnung,
Glättung).

---

## 8. Fehlerverhalten – strikt getrennt

| Ausfall | Wallet | Markets | Portfolio |
|---|---|---|---|
| YSKAR-Node weg | letzter bekannter Stand + Hinweis; **Senden gesperrt** (Fassung unbekannt) | unberührt | YSR-Position mit letztem Stand |
| Markt-API weg | **unberührt** | letzter Stand + Altersangabe | Fiat-Werte als „nicht verfügbar" |
| Beide weg | letzter Stand, Senden gesperrt | letzter Stand | letzter Stand |
| Kein Schlüssel gesetzt | unberührt | öffentliche API + Hinweis | wie Markets |

Die Sperre beim Senden ohne Node-Auskunft ist **bestehendes Verhalten** und
bleibt. Sie ist richtig: Ohne die Fassungsauskunft könnte die Wallet eine
Überweisung bauen, die der nächste Block nicht annimmt.

---

## 9. Lokale Ablage

| Inhalt | Ort | Grund |
|---|---|---|
| Tresor (verschlüsselte 12 Wörter) | Keystore, `yskar.tresor.v1` | **unverändert** – Kompatibilität |
| Markt-Schlüssel | Keystore, `yskar.markt.schluessel` | Geheimnis |
| Kontakte, Anforderungen | wie bisher | |
| Watchlists | `localStorage`, `yskar.watchlist.v1` | kein Geheimnis, muss Neustart überleben |
| Portfolio-Positionen | `localStorage`, `yskar.portfolio.v1` | dito |
| Markt-Cache | `localStorage`, `yskar.markt.cache.v1` | verworfen bei Formatwechsel |
| Einstellungen | wie bisher, erweitert | |

Alle neuen Schlüssel sind versioniert. Bei unbekannter Version wird verworfen,
nicht geraten – ein halb gelesener Datensatz ist schlimmer als keiner.

---

## 10. Reihenfolge der Umsetzung

| Phase | Inhalt | Prüfung |
|---|---|---|
| 3 | Designsystem: Palette, Typografie, Komponenten | Chromium-Screenshots, Kontrastprüfung |
| 4 | Wallet im neuen Design + Netzdaten anbinden | Tests grün, Live-Daten sichtbar |
| 5 | Markets: Anbieter, Cache, Liste, Suche, Detail, Charts | Cache-/Backoff-Tests, Chromium |
| 6 | Portfolio + Watchlist | Rechentests, Persistenz nach Neustart |
| 7 | Activity + Settings | Filtertests |
| 8 | Integration | Grenzen-Test, voller Durchlauf |
| 9 | Tests automatisiert + Gerätetestplan | 78 Alttests müssen grün bleiben |
| 10 | Release: APK, Doku, Übergabe | signierter Bau über Actions |

Nach jeder Phase: `npm test`, `npm run build`, `buendel-pruefen`, Commit,
`PROJECT_STATUS.md` aktualisieren.
