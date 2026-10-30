import type { CapacitorConfig } from '@capacitor/cli';

/**
 * YSKAR Wallet -- die Android-Huelle.
 *
 * Die Oberflaeche steckt FEST in der APK (webDir "dist"). Die App laedt
 * keinen Programmcode von einem Server nach: Was getestet wurde, ist das,
 * was laeuft, und niemand kann es nachtraeglich austauschen.
 *
 * Die Kennung app.yskar.wallet (umgedrehte Domain yskar.app) ist
 * endgueltig: Mit einer anderen Kennung waere es fuer Android eine andere App.
 */
const config: CapacitorConfig = {
  appId: 'app.yskar.wallet',
  appName: 'YSKAR Wallet',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
    backgroundColor: '#03060F',
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
