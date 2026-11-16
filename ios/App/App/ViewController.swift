import UIKit
import Capacitor

/**
 Eigener Bildschirm-Controller -- nur, um das Sichtschutz-Plugin anzumelden.

 WARUM DAS NOETIG IST: Capacitor registriert auf iOS die Plugins, die in
 `capacitor.config.json` unter `packageClassList` stehen. Diese Liste
 erzeugt die Kommandozeile beim Synchronisieren, und sie durchsucht dafuer
 ausschliesslich die npm-Pakete der Plugins -- nie den Quellcode der App
 selbst (siehe @capacitor/cli, util/iosplugin.js: getPluginFiles).

 Ein Plugin, das wie unseres in der App liegt, stuende dort also nie, und
 der Aufruf aus der Oberflaeche liefe ins Leere. Auf Android ist das
 dieselbe Lage; dort meldet MainActivity.java die beiden eigenen Plugins
 vor super.onCreate() an.

 `registerPluginInstance` ist der dafuer vorgesehene Weg: Im Gegensatz zu
 `registerPluginType` greift es auch, wenn Capacitor die uebrigen Plugins
 selbst registriert.
 */
class ViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(SichtschutzPlugin())
    }
}
