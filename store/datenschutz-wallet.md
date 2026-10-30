# Entwurf: Abschnitt „YSKAR Wallet (Android-App)“ für www.yskar.app/datenschutz

> Die App verlinkt unter „Rechtliches“ auf diese Seite, sie beschreibt bisher aber nur die Website. Vorschlag: diesen Abschnitt als neuen Abschnitt 9 auf der Datenschutzseite ergänzen (Website im Hauptrepo, `website/datenschutz.html`). Erst nach Freigabe einbauen.

## 9. YSKAR Wallet (Android-App)

Die YSKAR Wallet ist eine Wallet, bei der nur Sie Ihren Schlüssel haben. Es gibt kein Benutzerkonto, keine Werbung, kein Tracking und keine Analyse- oder Absturzwerkzeuge.

**Was auf Ihrem Gerät bleibt.** Die 12 Wörter Ihrer Wallet entstehen auf dem Gerät. Sie werden mit Ihrer PIN verschlüsselt im geschützten Speicher von Android abgelegt und verlassen das Gerät nie, auch nicht in Sicherungen des Google-Kontos. Kontakte (Namen zu Adressen), Einstellungen und Zahlungsanforderungen liegen ebenfalls nur auf dem Gerät.

**Kamera.** Die Kamera wird nur für den QR-Scanner genutzt, wenn Sie ihn öffnen. Die Bilder werden auf dem Gerät ausgewertet und weder gespeichert noch übertragen.

**Fingerabdruck.** Die Entsperrung per Fingerabdruck übernimmt Android. Die App erhält keine Fingerabdruckdaten.

**Was die App an Server sendet.**
- Um Guthaben, Verlauf und Netzgebühren anzuzeigen, fragt die App den Server yskar.vercel.app (betrieben bei Vercel, siehe Abschnitt 2) mit Ihrer öffentlichen Wallet-Adresse ab. Dabei wird Ihre IP-Adresse übermittelt und in den Server-Protokollen von Vercel verarbeitet.
- Haben Sie Benachrichtigungen eingeschaltet, fragt die App dieselben Daten in Abständen auch im Hintergrund ab, um neue Eingänge zu melden.
- Wenn Sie YSR senden, wird die auf dem Gerät signierte Überweisung an den YSKAR-Knoten unter pool.yskar.app übermittelt. Sie wird dadurch Teil der öffentlichen Blockchain (siehe Abschnitt 8).
- Wenn Sie sich per QR-Code auf einer YSKAR-Seite anmelden, sendet die App die von Ihnen bestätigte signierte Nachricht mit Ihrer Adresse und Ihrem öffentlichen Schlüssel an genau diese Seite.

Wallet-Adressen und Überweisungen sind auf der Blockchain öffentlich. Rechtsgrundlage für die Abfragen ist Art. 6 Abs. 1 lit. b DSGVO (Bereitstellung der von Ihnen genutzten Funktionen).

**Löschen.** Da es kein Konto gibt, liegen bei YSKAR keine Daten zu Ihrer Person vor, die gelöscht werden könnten. Deinstallieren Sie die App, werden alle Daten auf dem Gerät gelöscht. Ohne Ihre 12 Wörter ist die Wallet danach nicht wiederherstellbar.

**Kontakt.** Fragen zum Datenschutz: [E-Mail-Adresse eintragen] oder über github.com/dabitlex/YSKAR.

---

**Hinweis (nicht Teil des Textes):** Abschnitt 1 der Seite nennt bisher niemanden als Verantwortlichen. Ob das nach DSGVO genügt, ist eine Rechtsfrage – hier keine Rechtsberatung.
