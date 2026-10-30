package app.yskar.wallet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Der Hintergrundjob: Konto laden, mit der gemerkten Hoehe vergleichen,
 * neue Eingaenge melden. Gemeldet wird nur, was in einem Block steht --
 * Wartendes nicht, damit nichts Falsches gemeldet wird.
 */
public class EingangArbeit extends Worker {

    static final String SPIEGEL = "https://yskar.vercel.app";
    static final String KANAL = "eingang";

    public EingangArbeit(@NonNull Context ctx, @NonNull WorkerParameters p) { super(ctx, p); }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(EingangPlugin.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("an", false)) return Result.success();
        String adresse = prefs.getString("adresse", "");
        if (adresse == null || adresse.isEmpty()) return Result.success();
        // Den naechsten Lauf in 5 Minuten anstossen -- Android plant periodische
        // Arbeit erst ab 15 Minuten, einzelne Auftraege mit Verzoegerung aber kuerzer.
        // Im Tiefschlaf (Doze) schiebt das System auch die hinaus; dann faengt der
        // 15-Minuten-Takt aus dem Plugin sie wieder ein.
        naechsten(ctx);
        try {
            JSONObject konto = laden(SPIEGEL + "/api/v2/account/" + adresse);
            JSONArray verlauf = konto.optJSONArray("history");
            if (verlauf == null) return Result.success();
            long gemerkt = prefs.getLong("hoehe", -1L);
            long hoechste = gemerkt;
            JSONObject texte = new JSONObject(prefs.getString("texte", "{}"));
            int zahl = 0;
            for (int i = verlauf.length() - 1; i >= 0; i--) { // aelteste zuerst melden
                JSONObject e = verlauf.getJSONObject(i);
                long h = e.optLong("height", 0);
                String art = e.optString("kind", "");
                boolean eingang = art.equals("in") || art.equals("reward") || art.equals("pool");
                if (h > hoechste) hoechste = h;
                if (gemerkt < 0 || !eingang || h <= gemerkt) continue;
                melden(ctx, e, texte, zahl++);
            }
            prefs.edit().putLong("hoehe", Math.max(hoechste, 0)).apply();
            return Result.success();
        } catch (Exception ex) {
            return Result.retry();
        }
    }

    static void naechsten(Context ctx) {
        Constraints c = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(ctx).enqueueUniqueWork("eingang-naechste", ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(EingangArbeit.class).setConstraints(c).setInitialDelay(5, TimeUnit.MINUTES).build());
    }

    private static JSONObject laden(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(20000);
        c.setRequestProperty("accept", "application/json");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder(); String z;
            while ((z = r.readLine()) != null) sb.append(z);
            return new JSONObject(sb.toString());
        } finally { c.disconnect(); }
    }

    /** "250,00" aus "25000000000": acht Nachkommastellen, mindestens zwei, hoechstens acht gezeigt. */
    static String betrag(String einheiten, Locale l) {
        BigDecimal b;
        try { b = new BigDecimal(einheiten).movePointLeft(8); } catch (Exception e) { return einheiten; }
        NumberFormat f = NumberFormat.getNumberInstance(l);
        f.setMinimumFractionDigits(2); f.setMaximumFractionDigits(8);
        return f.format(b);
    }

    static String kurz(String a) {
        if (a == null || a.length() < 14) return a == null ? "" : a;
        return a.substring(0, 8) + "…" + a.substring(a.length() - 4);
    }

    private static void melden(Context ctx, JSONObject e, JSONObject texte, int n) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(KANAL) == null) {
            NotificationChannel k = new NotificationChannel(KANAL, texte.optString("kanal", "Eingang"), NotificationManager.IMPORTANCE_HIGH);
            k.setDescription(texte.optString("kanalText", ""));
            nm.createNotificationChannel(k);
        }
        Locale l = Locale.forLanguageTag(texte.optString("sprache", "de"));
        String art = e.optString("kind", "in");
        String txid = e.optString("txid", "");
        String titel = "+" + betrag(e.optString("amount", "0"), l) + " YSR";
        String text;
        if (art.equals("reward")) text = texte.optString("belohnung", "Blockbelohnung") + " · " + texte.optString("block", "Block") + " #" + e.optLong("height", 0);
        else if (art.equals("pool")) text = texte.optString("pool", "Pool-Anteil") + " · " + texte.optString("block", "Block") + " #" + e.optLong("height", 0);
        else text = texte.optString("von", "Von") + " " + kurz(e.optString("counterparty", ""));
        String notiz = e.isNull("memo") ? "" : e.optString("memo", "");

        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction(Intent.ACTION_MAIN);
        i.putExtra("eingang_txid", txid);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, txid.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_meldung)
            .setColor(0xFF2F7BFF)
            .setContentTitle(titel)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(new NotificationCompat.Builder(ctx, KANAL).setSmallIcon(R.drawable.ic_meldung).setColor(0xFF2F7BFF)
                .setContentTitle(texte.optString("kanal", "Eingang")).setContentText(texte.optString("gesperrt", "")).build());
        if (!notiz.isEmpty()) b.setStyle(new NotificationCompat.BigTextStyle().bigText(text + "\n„" + notiz + "“"));
        try { NotificationManagerCompat.from(ctx).notify(txid.hashCode(), b.build()); } catch (SecurityException ignored) { }
    }
}
