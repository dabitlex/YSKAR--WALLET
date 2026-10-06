import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/*
  Die Oberflaeche wird fest in die APK gebaut (capacitor.config.ts: webDir
  "dist"). Nichts davon wird zur Laufzeit von einem Server nachgeladen.

  YSKAR_VERSION setzt der GitHub-Workflow; lokal steht dort "lokal".
*/
export default defineConfig({
  base: './',
  plugins: [react()],
  define: {
    __VERSION__: JSON.stringify(process.env.YSKAR_VERSION ?? 'lokal'),
  },
  build: {
    target: 'es2020',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
  server: { port: 5199, strictPort: true },
});
