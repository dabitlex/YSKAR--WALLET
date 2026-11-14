import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;

/**
 * Ein Bildschirmbild der Wallet auf das Display eines Telefons in einem
 * Foto setzen.
 *
 * Drei Schritte, jeder ein eigener Aufruf:
 *
 *   probe  Foto aufgehellt und vergroessert ausgeben, mit dem Umriss des
 *          Displays, wie die vier Ecken ihn beschreiben. Zum Einmessen.
 *   grund  Aus dem Foto das GRUNDBILD machen: Das alte Display wird
 *          geschwaerzt, Finger davor bleiben stehen. Dazu eine Maske, die
 *          sagt, wo ein Finger vor dem Display liegt.
 *   setze  Ein Bildschirmbild auf das Grundbild legen: perspektivisch
 *          verzerrt, mit runden Ecken, in Schaerfe und Helligkeit ans Foto
 *          angepasst, hinter den Fingern.
 *
 * Angaben als name=wert. Die Ecken des Displays im Foto, in Bildpunkten:
 * ecken=x1,y1,x2,y2,x3,y3,x4,y4 - oben links, oben rechts, unten rechts,
 * unten links, gesehen vom aufrecht gehaltenen Telefon.
 */
public class Einsetzen {

    // ------------------------------------------------------------ Abbildung
    static double[] abbildung(double[] s, double[] d) {
        double[][] a = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double u = s[2 * i], v = s[2 * i + 1], x = d[2 * i], y = d[2 * i + 1];
            a[2 * i] = new double[]{u, v, 1, 0, 0, 0, -u * x, -v * x, x};
            a[2 * i + 1] = new double[]{0, 0, 0, u, v, 1, -u * y, -v * y, y};
        }
        for (int c = 0; c < 8; c++) {
            int p = c;
            for (int r = c + 1; r < 8; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
            double[] t = a[c]; a[c] = a[p]; a[p] = t;
            for (int r = 0; r < 8; r++) {
                if (r == c) continue;
                double f = a[r][c] / a[c][c];
                for (int k = c; k < 9; k++) a[r][k] -= f * a[c][k];
            }
        }
        double[] h = new double[9];
        for (int i = 0; i < 8; i++) h[i] = a[i][8] / a[i][i];
        h[8] = 1;
        return h;
    }

    static double[] kehre(double[] m) {
        double a = m[0], b = m[1], c = m[2], d = m[3], e = m[4], f = m[5], g = m[6], h = m[7], i = m[8];
        double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        return new double[]{(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det};
    }

    static double mx, my;
    static void bilde(double[] m, double u, double v) {
        double w = m[6] * u + m[7] * v + m[8];
        mx = (m[0] * u + m[1] * v + m[2]) / w;
        my = (m[3] * u + m[4] * v + m[5]) / w;
    }

    /** Liegt (u,v) im Display? Rechteck mit runden Ecken, um e erweitert. */
    static boolean drin(double u, double v, double w, double h, double r, double e) {
        double x0 = -e, y0 = -e, x1 = w + e, y1 = h + e, rr = r + e;
        if (u < x0 || u > x1 || v < y0 || v > y1) return false;
        double cx = Math.max(x0 + rr, Math.min(x1 - rr, u)), cy = Math.max(y0 + rr, Math.min(y1 - rr, v));
        double dx = u - cx, dy = v - cy;
        return dx * dx + dy * dy <= rr * rr;
    }

    // ---------------------------------------------------------------- Helfer
    static double kl(double x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }

    /**
     * Auch Haut im Schatten und glaenzende Naegel als Finger zaehlen
     * (schattenhaut=1). Die Unterseite eines Daumens ist im Foto fast
     * schwarz mit roetlichem Stich, ein Nagel hell und kaum gefaerbt -
     * beides faellt durch die gewoehnliche Pruefung. Was beide vom Display
     * trennt: Sie sind roetlich, das Display blaeulich oder neutral.
     */
    static boolean schattenhaut = false;

    /** Wie sehr ein Bildpunkt nach Haut aussieht, 0..1. */
    static double haut(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        double hell = r < 60 ? 0 : kl((r - b - 20) / 35.0) * kl((r - g - 8) / 14.0) * kl((g - b + 6) / 10.0);
        if (!schattenhaut || r < 26) return hell;
        return Math.max(hell, kl((r - b - 6) / 10.0) * kl((r - g - 3) / 8.0));
    }

    static float[] weich(float[] a, int w, int h, double sigma) {
        if (sigma <= 0.01) return a;
        int n = (int) Math.ceil(sigma * 3);
        float[] k = new float[2 * n + 1]; float s = 0;
        for (int i = -n; i <= n; i++) { k[i + n] = (float) Math.exp(-i * i / (2 * sigma * sigma)); s += k[i + n]; }
        for (int i = 0; i < k.length; i++) k[i] /= s;
        float[] t = new float[a.length], o = new float[a.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = 0;
            for (int i = -n; i <= n; i++) { int xx = Math.max(0, Math.min(w - 1, x + i)); v += a[y * w + xx] * k[i + n]; }
            t[y * w + x] = v;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = 0;
            for (int i = -n; i <= n; i++) { int yy = Math.max(0, Math.min(h - 1, y + i)); v += t[yy * w + x] * k[i + n]; }
            o[y * w + x] = v;
        }
        return o;
    }

    /** Scheibe als Formelement: weiten (max) oder engen (min). */
    static boolean[] forme(boolean[] a, int w, int h, int r, boolean weiten) {
        if (r <= 0) return a;
        boolean[] o = new boolean[a.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean v = !weiten;
            aussen:
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                int xx = x + dx, yy = y + dy;
                boolean q = xx >= 0 && yy >= 0 && xx < w && yy < h && a[yy * w + xx];
                if (weiten && q) { v = true; break aussen; }
                if (!weiten && !q) { v = false; break aussen; }
            }
            o[y * w + x] = v;
        }
        return o;
    }

    static void schreibeJpg(BufferedImage b, File f, float guete) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(guete);
        f.delete();
        try (ImageOutputStream o = ImageIO.createImageOutputStream(f)) {
            w.setOutput(o);
            w.write(null, new IIOImage(b, null, null), p);
        }
        w.dispose();
    }

    static BufferedImage lies(String pfad) throws IOException {
        BufferedImage q = ImageIO.read(new File(pfad));
        if (q == null) throw new IOException("nicht lesbar: " + pfad);
        BufferedImage b = new BufferedImage(q.getWidth(), q.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics(); g.drawImage(q, 0, 0, null); g.dispose();
        return b;
    }

    // ----------------------------------------------------------------- Lauf
    static Map<String, String> an = new HashMap<>();
    static String wert(String n, String vorgabe) { return an.getOrDefault(n, vorgabe); }
    static double zahl(String n, double vorgabe) { return an.containsKey(n) ? Double.parseDouble(an.get(n)) : vorgabe; }

    // Quellmasse (ein Bildschirmbild) und Ecken
    static double sw = 1200, sh = 2608, radius;
    static double[] hin, her;
    static int x0, y0, bw, bh;      // Ausschnitt im Foto, in dem gerechnet wird

    static void richteEin(int fw, int fh) {
        String[] t = wert("ecken", "").split(",");
        if (t.length != 8) throw new IllegalArgumentException("ecken= braucht acht Zahlen");
        double[] d = new double[8];
        for (int i = 0; i < 8; i++) d[i] = Double.parseDouble(t[i].trim());
        sw = zahl("quellbreite", 1200); sh = zahl("quellhoehe", 2608);
        radius = zahl("rundung", 0.105) * sw;
        hin = abbildung(new double[]{0, 0, sw, 0, sw, sh, 0, sh}, d);
        her = kehre(hin);
        double xa = 1e9, ya = 1e9, xb = -1e9, yb = -1e9;
        for (int i = 0; i < 4; i++) { xa = Math.min(xa, d[2 * i]); xb = Math.max(xb, d[2 * i]); ya = Math.min(ya, d[2 * i + 1]); yb = Math.max(yb, d[2 * i + 1]); }
        int rand = 16;
        x0 = Math.max(0, (int) xa - rand); y0 = Math.max(0, (int) ya - rand);
        bw = Math.min(fw, (int) xb + rand + 1) - x0; bh = Math.min(fh, (int) yb + rand + 1) - y0;
    }

    /** Anteil eines Bildpunkts, der im (um e erweiterten) Display liegt. */
    static float[] deckung(double e) {
        int n = 4; float[] c = new float[bw * bh];
        for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
            int z = 0;
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                bilde(her, x0 + x + (i + 0.5) / n, y0 + y + (j + 0.5) / n);
                if (drin(mx, my, sw, sh, radius, e)) z++;
            }
            c[y * bw + x] = z / (float) (n * n);
        }
        return c;
    }

    /** Wo liegt ein Finger vor dem Display? 0..1 je Bildpunkt des Ausschnitts. */
    static float[] fingermaske(BufferedImage foto, float[] display) {
        float[] m = new float[bw * bh];
        if (zahl("finger", 0) < 0.5) return m;
        schattenhaut = zahl("schattenhaut", 0) > 0.5;
        double[] hw = new double[bw * bh]; boolean[] bin = new boolean[bw * bh];
        for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
            hw[y * bw + x] = haut(foto.getRGB(x0 + x, y0 + y));
            bin[y * bw + x] = hw[y * bw + x] > 0.5;
        }
        // Duenne Dinge (Schrift) fallen beim Engen und Wiederweiten heraus.
        bin = forme(forme(bin, bw, bh, 2, false), bw, bh, 2, true);
        // Zusammenhaengende Flaechen: nur grosse, die ueber das Display hinausragen.
        int[] nr = new int[bw * bh]; boolean[] halte = new boolean[bw * bh];
        int naechste = 0; int[] stapel = new int[bw * bh];
        for (int start = 0; start < bw * bh; start++) {
            if (!bin[start] || nr[start] != 0) continue;
            naechste++; int oben = 0; stapel[oben++] = start; nr[start] = naechste;
            ArrayList<Integer> glieder = new ArrayList<>(); boolean draussen = false;
            while (oben > 0) {
                int p = stapel[--oben]; glieder.add(p);
                if (display[p] < 0.5f) draussen = true;
                int px = p % bw, py = p / bw;
                int[] nb = {p - 1, p + 1, p - bw, p + bw};
                boolean[] ok = {px > 0, px < bw - 1, py > 0, py < bh - 1};
                for (int k = 0; k < 4; k++) if (ok[k] && bin[nb[k]] && nr[nb[k]] == 0) { nr[nb[k]] = naechste; stapel[oben++] = nb[k]; }
            }
            if (glieder.size() >= zahl("fingerflaeche", 300) && draussen) for (int p : glieder) halte[p] = true;
        }
        // Loecher schliessen: Ein Fingernagel glaenzt und sieht stellenweise
        // nicht nach Haut aus. Was vom Rand des Ausschnitts her nicht zu
        // erreichen ist, ohne durch den Finger zu gehen, gehoert zum Finger.
        boolean[] erreicht = new boolean[bw * bh]; int ob = 0;
        for (int x = 0; x < bw; x++) for (int y : new int[]{0, bh - 1}) { int p = y * bw + x; if (!halte[p] && !erreicht[p]) { erreicht[p] = true; stapel[ob++] = p; } }
        for (int y = 0; y < bh; y++) for (int x : new int[]{0, bw - 1}) { int p = y * bw + x; if (!halte[p] && !erreicht[p]) { erreicht[p] = true; stapel[ob++] = p; } }
        while (ob > 0) {
            int p = stapel[--ob]; int px = p % bw, py = p / bw;
            int[] nb = {p - 1, p + 1, p - bw, p + bw};
            boolean[] ok = {px > 0, px < bw - 1, py > 0, py < bh - 1};
            for (int k = 0; k < 4; k++) if (ok[k] && !halte[nb[k]] && !erreicht[nb[k]]) { erreicht[nb[k]] = true; stapel[ob++] = nb[k]; }
        }
        for (int i = 0; i < halte.length; i++) if (!erreicht[i]) halte[i] = true;
        // dazu=x1,y1,x2,y2,...: ein Vieleck in Bildpunkten des Fotos, das
        // zum Finger gezaehlt wird, gleich wie es aussieht. Fuer Stellen im
        // tiefen Schatten, die fast schwarz sind und sich an der Farbe nicht
        // mehr vom Display unterscheiden lassen. Die Kontur ist am
        // aufgehellten Original abgelesen.
        String dazu = wert("dazu", "");
        if (!dazu.isEmpty()) {
            String[] z = dazu.split(",");
            Path2D eck = new Path2D.Double();
            for (int i = 0; i + 1 < z.length; i += 2) {
                double px = Double.parseDouble(z[i].trim()) - x0, py = Double.parseDouble(z[i + 1].trim()) - y0;
                if (i == 0) eck.moveTo(px, py); else eck.lineTo(px, py);
            }
            eck.closePath();
            for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) if (eck.contains(x + 0.5, y + 0.5)) halte[y * bw + x] = true;
        }
        // Buchten schliessen (schliessen=<Radius>): erst weiten, dann engen.
        int zu = (int) zahl("schliessen", 0);
        if (zu > 0) halte = forme(forme(halte, bw, bh, zu, true), bw, bh, zu, false);
        // flaechig=1: Die Maske ist die Flaeche des Fingers, weich berandet -
        // nicht die Hautfarbe Punkt fuer Punkt. Fuer einen Finger, der
        // stellenweise nicht nach Haut aussieht.
        if (zahl("flaechig", 0) > 0.5) {
            boolean[] f = forme(halte, bw, bh, (int) zahl("einzug", 0), false);
            for (int i = 0; i < m.length; i++) m[i] = f[i] ? 1 : 0;
            return weich(m, bw, bh, zahl("maskenweich", 0.7));
        }
        boolean[] innen = forme(halte, bw, bh, (int) zahl("innenweite", 2), false), nah = forme(halte, bw, bh, (int) zahl("nahweite", 4), true);
        for (int i = 0; i < m.length; i++) m[i] = (float) Math.max(innen[i] ? 1 : 0, nah[i] ? hw[i] : 0);
        return weich(m, bw, bh, zahl("maskenweich", 0.7));
    }

    public static void main(String[] args) throws Exception {
        String was = args[0];
        for (int i = 1; i < args.length; i++) { int p = args[i].indexOf('='); an.put(args[i].substring(0, p), args[i].substring(p + 1)); }
        BufferedImage foto = lies(wert("foto", ""));
        int fw = foto.getWidth(), fh = foto.getHeight();
        richteEin(fw, fh);

        if (was.equals("probe")) {
            int f = (int) zahl("mal", 3); double gamma = zahl("gamma", 0.45);
            BufferedImage o = new BufferedImage(bw * f, bh * f, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < bh * f; y++) for (int x = 0; x < bw * f; x++) {
                int rgb = foto.getRGB(x0 + x / f, y0 + y / f);
                int r = (int) (255 * Math.pow(((rgb >> 16) & 255) / 255.0, gamma)), g = (int) (255 * Math.pow(((rgb >> 8) & 255) / 255.0, gamma)), b = (int) (255 * Math.pow((rgb & 255) / 255.0, gamma));
                o.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
            Graphics2D g = o.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Path2D umriss = new Path2D.Double();
            RoundRectangle2D rr = new RoundRectangle2D.Double(0, 0, sw, sh, 2 * radius, 2 * radius);
            PathIterator it = rr.getPathIterator(null, 2.0); double[] c = new double[6]; boolean erst = true;
            while (!it.isDone()) {
                int art = it.currentSegment(c);
                if (art != PathIterator.SEG_CLOSE) { bilde(hin, c[0], c[1]); double px = (mx - x0) * f, py = (my - y0) * f; if (erst) { umriss.moveTo(px, py); erst = false; } else umriss.lineTo(px, py); }
                else umriss.closePath();
                it.next();
            }
            g.setColor(new Color(255, 0, 255, 220)); g.setStroke(new BasicStroke(1f)); g.draw(umriss);
            // Hilfslinien bei 1/4, 1/2, 3/4 der Hoehe und der Breite
            g.setColor(new Color(0, 255, 255, 150));
            for (double q : new double[]{0.25, 0.5, 0.75}) {
                bilde(hin, 0, sh * q); double ax = (mx - x0) * f, ay = (my - y0) * f; bilde(hin, sw, sh * q);
                g.draw(new Line2D.Double(ax, ay, (mx - x0) * f, (my - y0) * f));
                bilde(hin, sw * q, 0); ax = (mx - x0) * f; ay = (my - y0) * f; bilde(hin, sw * q, sh);
                g.draw(new Line2D.Double(ax, ay, (mx - x0) * f, (my - y0) * f));
            }
            g.dispose();
            ImageIO.write(o, "png", new File(wert("aus", "probe.png")));
            return;
        }

        if (was.equals("kanten")) {
            // Die vier Kanten des Displays suchen: Von innen nach aussen
            // faellt die Helligkeit vom leuchtenden Display auf den
            // schwarzen Rand. Je Kante viele solche Stellen, durch sie eine
            // Gerade, und die Ecken sind die Schnittpunkte. Ausgangspunkt
            // sind die geschaetzten Ecken; die runden Ecken und alles, wo
            // ein Finger liegt, bleiben aussen vor.
            String[] t = wert("ecken", "").split(",");
            double[] d = new double[8]; for (int i = 0; i < 8; i++) d[i] = Double.parseDouble(t[i].trim());
            double cx = (d[0] + d[2] + d[4] + d[6]) / 4, cy = (d[1] + d[3] + d[5] + d[7]) / 4;
            double weite = zahl("weite", 12), von = zahl("von", 0.15), bis = zahl("bis", 0.85);
            double[][] gerade = new double[4][];   // Punkt (px,py) und Richtung (rx,ry)
            for (int s = 0; s < 4; s++) {
                double ax = d[2 * s], ay = d[2 * s + 1], bx = d[2 * ((s + 1) % 4)], by = d[2 * ((s + 1) % 4) + 1];
                double len = Math.hypot(bx - ax, by - ay), rx = (bx - ax) / len, ry = (by - ay) / len;
                double nx = -ry, ny = rx;
                if (nx * ((ax + bx) / 2 - cx) + ny * ((ay + by) / 2 - cy) < 0) { nx = -nx; ny = -ny; }
                ArrayList<double[]> pk = new ArrayList<>();
                for (double q = von; q <= bis; q += 1.0 / len) {
                    double px = ax + q * (bx - ax), py = ay + q * (by - ay);
                    int n = (int) (weite * 4) + 1; double[] hell = new double[n]; boolean[] hautHier = new boolean[n]; boolean finger = false;
                    for (int i = 0; i < n; i++) {
                        double o = -weite + i * 0.5, sx = px + o * nx, sy = py + o * ny;
                        int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
                        if (ix < 0 || iy < 0 || ix >= fw - 1 || iy >= fh - 1) { finger = true; break; }
                        double fx = sx - ix, fy = sy - iy; double v = 0;
                        for (int k = 0; k < 4; k++) {
                            int rgb = foto.getRGB(ix + (k & 1), iy + (k >> 1));
                            if (haut(rgb) > 0.35) hautHier[i] = true;
                            double l = 0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255);
                            v += l * ((k & 1) == 1 ? fx : 1 - fx) * ((k >> 1) == 1 ? fy : 1 - fy);
                        }
                        hell[i] = v;
                    }
                    if (finger) continue;
                    double[] innen = Arrays.copyOfRange(hell, 0, n / 4); Arrays.sort(innen);
                    double bezug = innen[innen.length / 2];
                    // Der dunkle Rand: das Minimum, bevor es (zum Rahmen hin) wieder hell wird.
                    double dunkel = 1e9; for (int i = n / 4; i < n; i++) dunkel = Math.min(dunkel, hell[i]);
                    if (bezug - dunkel < zahl("kontrast", 5)) continue;
                    double schwelle = dunkel + 0.5 * (bezug - dunkel);
                    for (int i = n / 4; i < n; i++) {
                        if (hell[i] < schwelle) {
                            double o = -weite + i * 0.5;
                            if (i > 0 && hell[i - 1] > hell[i]) o -= 0.5 * (schwelle - hell[i]) / (hell[i - 1] - hell[i]);
                            boolean davor = false; for (int j = 0; j <= Math.min(n - 1, i + 4); j++) davor |= hautHier[j];
                            if (!davor) pk.add(new double[]{px + o * nx, py + o * ny});
                            break;
                        }
                    }
                }
                // Gerade durch die Punkte. Erst die Zweipunktgerade mit den
                // meisten Anhaengern suchen (Stellen, die an etwas anderem
                // haengengeblieben sind, stimmen nicht mit), dann die Gerade
                // durch ihre Anhaenger legen.
                double mxp = 0, myp = 0, vx = rx, vy = ry;
                if (pk.size() >= 2) {
                    int beste = -1; double[] bp = null; java.util.Random zufall = new java.util.Random(7);
                    for (int versuch = 0; versuch < 600; versuch++) {
                        double[] p1 = pk.get(zufall.nextInt(pk.size())), p2 = pk.get(zufall.nextInt(pk.size()));
                        double l2 = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]); if (l2 < len * 0.15) continue;
                        double ux = (p2[0] - p1[0]) / l2, uy = (p2[1] - p1[1]) / l2; int z = 0;
                        for (double[] p : pk) if (Math.abs((p[0] - p1[0]) * -uy + (p[1] - p1[1]) * ux) < 0.7) z++;
                        if (z > beste) { beste = z; bp = new double[]{p1[0], p1[1], ux, uy}; }
                    }
                    if (bp != null) { final double[] f = bp; pk.removeIf(p -> Math.abs((p[0] - f[0]) * -f[3] + (p[1] - f[1]) * f[2]) >= 0.7); }
                    for (double[] p : pk) { mxp += p[0]; myp += p[1]; } mxp /= pk.size(); myp /= pk.size();
                    double sxx = 0, sxy = 0, syy = 0;
                    for (double[] p : pk) { double ux = p[0] - mxp, uy = p[1] - myp; sxx += ux * ux; sxy += ux * uy; syy += uy * uy; }
                    double w = 0.5 * Math.atan2(2 * sxy, sxx - syy); vx = Math.cos(w); vy = Math.sin(w);
                }
                double rest = 0; for (double[] p : pk) rest += Math.abs((p[0] - mxp) * -vy + (p[1] - myp) * vx);
                System.out.printf(Locale.ROOT, "Kante %d: %d Stellen, mittlere Abweichung %.2f%n", s, pk.size(), pk.isEmpty() ? 0 : rest / pk.size());
                gerade[s] = new double[]{mxp, myp, vx, vy};
            }
            StringBuilder aus = new StringBuilder();
            for (int e = 0; e < 4; e++) {
                double[] g1 = gerade[(e + 3) % 4], g2 = gerade[e];
                double det = g1[2] * -g2[3] + g2[2] * g1[3];
                double tt = ((g2[0] - g1[0]) * -g2[3] + (g2[1] - g1[1]) * g2[2]) / det;
                aus.append(String.format(Locale.ROOT, "%s%.1f,%.1f", e == 0 ? "" : ",", g1[0] + tt * g1[2], g1[1] + tt * g1[3]));
            }
            System.out.println("ecken=" + aus);
            return;
        }

        if (was.equals("grund")) {
            float[] display = deckung(0), flaeche = deckung(zahl("ueberstand", 14));
            float[] m = fingermaske(foto, display);
            int sr = (int) zahl("schwarz_r", 9), sg = (int) zahl("schwarz_g", 9), sb = (int) zahl("schwarz_b", 11);
            BufferedImage maske = new BufferedImage(fw, fh, BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
                int i = y * bw + x; float cb = flaeche[i]; if (cb <= 0) continue;
                int rgb = foto.getRGB(x0 + x, y0 + y); float mm = m[i];
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                // Unter der Maske bleibt das Foto stehen - also auch, was vom
                // alten Bildschirm am Rand des Fingers durchscheint: helle,
                // farblose Schrift. Haut ist nie farblos. Solche Punkte werden
                // zu Schatten (schriftweg=1).
                if (mm > 0 && zahl("schriftweg", 0) > 0.5) {
                    int hoch = Math.max(r, Math.max(g, b)), tief = Math.min(r, Math.min(g, b));
                    if (hoch - tief < 26 && hoch > 48) { r = 26; g = 19; b = 17; }
                }
                float nr = r * mm + sr * (1 - mm), ng = g * mm + sg * (1 - mm), nb = b * mm + sb * (1 - mm);
                r = Math.round(r * (1 - cb) + nr * cb); g = Math.round(g * (1 - cb) + ng * cb); b = Math.round(b * (1 - cb) + nb * cb);
                foto.setRGB(x0 + x, y0 + y, (r << 16) | (g << 8) | b);
                int grau = Math.round(255 * mm * cb);
                maske.getRaster().setSample(x0 + x, y0 + y, 0, grau);
            }
            schreibeJpg(foto, new File(wert("aus", "grund.jpg")), (float) zahl("guete", 0.96));
            ImageIO.write(maske, "png", new File(wert("maske", "maske.png")));
            return;
        }

        if (was.equals("setze")) {
            BufferedImage schirm = lies(wert("schirm", ""));
            if (schirm.getWidth() != (int) sw || schirm.getHeight() != (int) sh)
                throw new IllegalArgumentException("Bildschirmbild ist " + schirm.getWidth() + "x" + schirm.getHeight() + ", erwartet " + (int) sw + "x" + (int) sh);
            if (zahl("leiste", 1) > 0.5) statusleiste(schirm);
            BufferedImage mb = ImageIO.read(new File(wert("maske", "")));
            float[] display = deckung(0);

            // Vorverkleinern: Das Display ist im Foto viel kleiner als das
            // Bildschirmbild. Ohne das flimmert feine Schrift.
            bilde(hin, 0, 0); double ax = mx, ay = my; bilde(hin, sw, 0);
            double breiteImFoto = Math.hypot(mx - ax, my - ay);
            double k = Math.min(1.0, zahl("vorfaktor", 2.0) * breiteImFoto / sw);
            int kw = (int) Math.round(sw * k), kh = (int) Math.round(sh * k);
            float[][] klein = verkleinere(schirm, kw, kh);

            int n = 4; float[][] farbe = new float[3][bw * bh];
            for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
                int i = y * bw + x; if (display[i] <= 0) continue;
                float r = 0, g = 0, b = 0; int z = 0;
                for (int j = 0; j < n; j++) for (int ii = 0; ii < n; ii++) {
                    bilde(her, x0 + x + (ii + 0.5) / n, y0 + y + (j + 0.5) / n);
                    if (!drin(mx, my, sw, sh, radius, 0)) continue;
                    double u = mx * k - 0.5, v = my * k - 0.5;
                    int u0 = (int) Math.floor(u), v0 = (int) Math.floor(v); double fu = u - u0, fv = v - v0;
                    int ua = Math.max(0, Math.min(kw - 1, u0)), ub = Math.max(0, Math.min(kw - 1, u0 + 1));
                    int va = Math.max(0, Math.min(kh - 1, v0)), vb = Math.max(0, Math.min(kh - 1, v0 + 1));
                    for (int c = 0; c < 3; c++) {
                        double w = klein[c][va * kw + ua] * (1 - fu) * (1 - fv) + klein[c][va * kw + ub] * fu * (1 - fv)
                            + klein[c][vb * kw + ua] * (1 - fu) * fv + klein[c][vb * kw + ub] * fu * fv;
                        if (c == 0) r += w; else if (c == 1) g += w; else b += w;
                    }
                    z++;
                }
                if (z > 0) { farbe[0][i] = r / z * display[i]; farbe[1][i] = g / z * display[i]; farbe[2][i] = b / z * display[i]; }
            }
            // Schaerfe ans Foto angleichen: weichzeichnen, mit der Deckung gewichtet.
            double sigma = zahl("weich", 0.6);
            float[] dw = weich(display, bw, bh, sigma);
            for (int c = 0; c < 3; c++) farbe[c] = weich(farbe[c], bw, bh, sigma);

            // Schatten des Fingers auf dem Display.
            float[] m = new float[bw * bh], hart = new float[bw * bh];
            for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
                m[y * bw + x] = mb.getRaster().getSample(x0 + x, y0 + y, 0) / 255f;
                hart[y * bw + x] = m[y * bw + x] > 0.5f ? 1 : 0;
            }
            float[] schatten = weich(hart, bw, bh, zahl("schattenweite", 4));
            double tiefe = zahl("schatten", 0.5), heller = zahl("heller", 0.92), sockel = zahl("sockel", 5);

            for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
                int i = y * bw + x; float a = display[i]; if (a <= 0) continue;
                int rgb = foto.getRGB(x0 + x, y0 + y);
                float[] alt = {(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255};
                float mm = m[i]; double s = 1 - tiefe * schatten[i];
                int[] neu = new int[3];
                for (int c = 0; c < 3; c++) {
                    double f = dw[i] > 1e-4 ? farbe[c][i] / dw[i] : 0;
                    f = (sockel + heller * f) * s;
                    double mitFinger = alt[c] * mm + f * (1 - mm);
                    neu[c] = (int) Math.max(0, Math.min(255, Math.round(alt[c] * (1 - a) + mitFinger * a)));
                }
                foto.setRGB(x0 + x, y0 + y, (neu[0] << 16) | (neu[1] << 8) | neu[2]);
            }
            String aus = wert("aus", "aus.jpg");
            if (aus.endsWith(".png")) ImageIO.write(foto, "png", new File(aus));
            else schreibeJpg(foto, new File(aus), (float) zahl("guete", 0.90));
            return;
        }
        throw new IllegalArgumentException("unbekannt: " + was);
    }

    /** Flaechentreu verkleinern: jeder Zielpunkt ist das Mittel seiner Quellpunkte. */
    static float[][] verkleinere(BufferedImage b, int kw, int kh) {
        int w = b.getWidth(), h = b.getHeight();
        float[][] o = new float[3][kw * kh]; float[] z = new float[kw * kh];
        double fx = kw / (double) w, fy = kh / (double) h;
        for (int y = 0; y < h; y++) {
            double ya = y * fy, yb = (y + 1) * fy;
            for (int ky = (int) ya; ky <= Math.min(kh - 1, (int) yb); ky++) {
                double wy = Math.min(yb, ky + 1) - Math.max(ya, ky); if (wy <= 0) continue;
                for (int x = 0; x < w; x++) {
                    double xa = x * fx, xb = (x + 1) * fx; int rgb = b.getRGB(x, y);
                    for (int kx = (int) xa; kx <= Math.min(kw - 1, (int) xb); kx++) {
                        double wx = Math.min(xb, kx + 1) - Math.max(xa, kx); if (wx <= 0) continue;
                        float g = (float) (wx * wy); int i = ky * kw + kx;
                        o[0][i] += g * ((rgb >> 16) & 255); o[1][i] += g * ((rgb >> 8) & 255); o[2][i] += g * (rgb & 255); z[i] += g;
                    }
                }
            }
        }
        for (int i = 0; i < kw * kh; i++) if (z[i] > 0) { o[0][i] /= z[i]; o[1][i] /= z[i]; o[2][i] /= z[i]; }
        return o;
    }

    /**
     * Die Leiste des Telefons: Uhrzeit, Empfang, Akku. Die Bildschirmbilder
     * zeigen nur die App; ohne die Leiste saehe das Display im Foto leer aus.
     * 18:17 - dieselbe Zeit, die der Hauptbildschirm als "Aktualisiert" nennt.
     */
    static void statusleiste(BufferedImage b) throws Exception {
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double w = b.getWidth();
        Font f;
        String datei = wert("schrift", "");
        if (!datei.isEmpty()) f = Font.createFont(Font.TRUETYPE_FONT, new File(datei)).deriveFont(Font.BOLD, (float) (w * 0.037));
        else f = new Font("SansSerif", Font.BOLD, (int) (w * 0.037));
        g.setFont(f); g.setColor(new Color(238, 244, 255, 235));
        float grund = (float) (w * 0.083);
        g.drawString(wert("uhrzeit", "18:17"), (float) (w * 0.085), grund);
        // Empfang: vier Balken
        double bx = w * 0.775, bb = w * 0.0105, lu = w * 0.006;
        for (int i = 0; i < 4; i++) {
            double hh = w * (0.011 + 0.0065 * i);
            g.fill(new RoundRectangle2D.Double(bx + i * (bb + lu), grund - hh, bb, hh, bb * 0.5, bb * 0.5));
        }
        // Akku
        double ax = w * 0.862, aw = w * 0.058, ah = w * 0.028, ay = grund - ah;
        g.setStroke(new BasicStroke((float) (w * 0.0028)));
        g.setColor(new Color(238, 244, 255, 150));
        g.draw(new RoundRectangle2D.Double(ax, ay, aw, ah, ah * 0.55, ah * 0.55));
        g.fill(new RoundRectangle2D.Double(ax + aw + w * 0.003, ay + ah * 0.32, w * 0.0045, ah * 0.36, 3, 3));
        g.setColor(new Color(238, 244, 255, 235));
        double ein = w * 0.0048;
        g.fill(new RoundRectangle2D.Double(ax + ein, ay + ein, (aw - 2 * ein) * 0.82, ah - 2 * ein, ah * 0.3, ah * 0.3));
        g.dispose();
    }
}
