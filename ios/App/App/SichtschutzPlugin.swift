import Foundation
import UIKit
import Capacitor

/**
 Sichtschutz auf iOS -- und was iOS davon NICHT kann.

 Auf Android setzt SichtschutzPlugin.java das Fensterflag FLAG_SECURE.
 Android sperrt daraufhin Bildschirmfotos, Bildschirmaufnahmen und zeigt
 die App in der Uebersicht schwarz. Das ist eine echte Sperre.

 IOS HAT DAFUER KEINE ENTSPRECHUNG. Es gibt keine oeffentliche
 Schnittstelle, mit der eine App ein Bildschirmfoto verhindern kann. Was
 kursiert, sind Tricks ueber die verdeckte Ebene eines sicheren
 Textfeldes; sie stuetzen sich auf undokumentiertes Verhalten, brechen
 mit iOS-Fassungen und sind im App Store ein Risiko. In einer Wallet
 wird so etwas nicht eingebaut.

 Was diese Umsetzung deshalb WIRKLICH tut -- beides ist dokumentiert und
 zulaessig:

   1. UEBERSICHT VERDECKEN. Wechselt die App in den Hintergrund, legt
      sie eine deckende Flaeche ueber den Inhalt. Im App-Umschalter und
      in der Aufgabenuebersicht ist dann nichts zu lesen. Das deckt den
      haeufigsten Fall ab: jemand bekommt das Telefon kurz in die Hand.

   2. BILDSCHIRMFOTO MELDEN. iOS sagt einer App hinterher, dass eines
      gemacht wurde. Verhindern laesst es sich nicht, aber die
      Oberflaeche kann es dem Nutzer sagen -- gerade wenn die zwoelf
      Woerter zu sehen waren.

 Die Oberflaeche muss diesen Unterschied benennen und darf auf iOS nicht
 behaupten, Bildschirmfotos seien gesperrt. Siehe docs/IOS.md.
 */
@objc(SichtschutzPlugin)
public class SichtschutzPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "SichtschutzPlugin"
    public let jsName = "Sichtschutz"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "setzen", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "faehigkeiten", returnType: CAPPluginReturnPromise)
    ]

    private var an = false
    private var deckel: UIView?

    override public func load() {
        let z = NotificationCenter.default
        z.addObserver(self, selector: #selector(gehtWeg),
                      name: UIApplication.willResignActiveNotification, object: nil)
        z.addObserver(self, selector: #selector(kommtZurueck),
                      name: UIApplication.didBecomeActiveNotification, object: nil)
        z.addObserver(self, selector: #selector(fotoGemacht),
                      name: UIApplication.userDidTakeScreenshotNotification, object: nil)
    }

    deinit { NotificationCenter.default.removeObserver(self) }

    /// an = true: Uebersicht verdecken und Bildschirmfotos melden.
    @objc func setzen(_ call: CAPPluginCall) {
        an = call.getBool("an") ?? false
        if !an {
            DispatchQueue.main.async { [weak self] in self?.deckelWeg() }
        }
        call.resolve()
    }

    /**
     Was diese Plattform kann. Die Oberflaeche fragt das ab, statt es zu
     raten: `sperrt` ist auf Android true und auf iOS false.
     */
    @objc func faehigkeiten(_ call: CAPPluginCall) {
        call.resolve([
            "sperrt": false,          // Bildschirmfotos verhindern: nein
            "verdecktUebersicht": true,
            "meldetFoto": true
        ])
    }

    // MARK: - Uebersicht verdecken

    @objc private func gehtWeg() {
        guard an else { return }
        deckelDrauf()
    }

    @objc private func kommtZurueck() {
        deckelWeg()
    }

    private func fenster() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
    }

    private func deckelDrauf() {
        guard deckel == nil, let w = fenster() else { return }
        // Deckende Flaeche in der Grundfarbe der App (#080D18), damit der
        // Uebergang nicht blitzt. Kein Weichzeichner: Der ist auf aelteren
        // Geraeten traege, und verdeckt werden soll vollstaendig.
        let v = UIView(frame: w.bounds)
        v.backgroundColor = UIColor(red: 8.0/255.0, green: 13.0/255.0, blue: 24.0/255.0, alpha: 1.0)
        v.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        v.tag = 0x59534B52  // "YSKR"
        w.addSubview(v)
        deckel = v
    }

    private func deckelWeg() {
        deckel?.removeFromSuperview()
        deckel = nil
    }

    // MARK: - Bildschirmfoto

    @objc private func fotoGemacht() {
        guard an else { return }
        // Verhindern kann iOS es nicht. Die Oberflaeche erfaehrt es und
        // kann warnen -- besonders, wenn die zwoelf Woerter zu sehen waren.
        notifyListeners("bildschirmfoto", data: [:])
    }
}
