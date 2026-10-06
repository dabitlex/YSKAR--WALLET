import type { CapacitorConfig } from '@capacitor/cli';

/**
 * YSKAR Wallet -- die Android-Huelle.
 *
 * Die Oberflaeche steckt FEST in der APK (webDir "dist"). Die App laedt
 * keinen Programmcode von einem Server nach: Was getestet wurde, ist das,
 * was laeuft, und niemand kann es nachtraeglich austauschen.
 *
 * Kennung und Name tragen "test", solange die App nur an Tester geht. So
 * laesst sie sich neben der bisherigen App (net.yskar.wallet) installieren.
 */
const config: CapacitorConfig = {
  appId: 'net.yskar.wallet.test',
  appName: 'YSKAR Wallet Test',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
    backgroundColor: '#303338',
  },
  plugins: {
    // Dunkle App: helle Symbole in Statusleiste und Gestenbalken. Android
    // zeichnet bis unter beide; Capacitor traegt deren Hoehe als
    // --safe-area-inset-* ein, stil.css nutzt das.
    SystemBars: { style: 'DARK', initialViewportFitValueHint: 'cover' },
    // Anfragen laufen gezielt ueber CapacitorHttp (src/netz/api.ts); das
    // allgemeine Umleiten von fetch bleibt aus.
    CapacitorHttp: { enabled: false },
  },
};

export default config;
