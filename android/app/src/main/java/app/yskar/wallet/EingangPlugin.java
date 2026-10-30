package app.yskar.wallet;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.concurrent.TimeUnit;

/**
 * Benachrichtigung bei Eingang.
 *
 * Die App fragt im Hintergrund etwa alle 5 Minuten den Spiegel nach dem
 * Konto (WorkManager, auch wenn die App nicht laeuft): Jeder Lauf plant den
 * naechsten in 5 Minuten; ein 15-Minuten-Takt faengt die Kette wieder ein,
 * falls das System sie unterbricht. Steht ein neuer
 * Eingang in einem Block, meldet Android ihn als Benachrichtigung. Kein
 * Server kennt dafuer das Geraet: Die Abfrage geht vom Telefon aus, wie
 * jede andere Abfrage der Wallet auch.
 *
 *   einschalten({ adresse, texte })  -> merkt Adresse und Texte, fragt die
 *                                       Berechtigung ab und plant den Job
 *   ausschalten()                    -> plant ab, vergisst alles
 *   stand()                          -> { an, berechtigung, akkuFrei }
 *   akkuEinstellung()                -> oeffnet die Akku-Optimierung des Systems
 *   jetzt()                          -> einmal sofort pruefen (zum Testen)
 *   geoeffnetMit()                   -> { txid } wenn ueber eine Meldung geoeffnet
 */
@CapacitorPlugin(
    name = "Eingang",
    permissions = { @Permission(alias = "meldung", strings = { Manifest.permission.POST_NOTIFICATIONS }) }
)
public class EingangPlugin extends Plugin {

    static final String PREFS = "eingang";
    static final String ARBEIT = "eingang-pruefen";
    /** Von MainActivity gesetzt, wenn eine Meldung angetippt wurde. */
    static volatile String geoeffnetTxid = null;

    private SharedPreferences prefs() { return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    @PluginMethod
    public void einschalten(PluginCall call) {
        final String adresse = call.getString("adresse", "");
        if (adresse == null || adresse.isEmpty()) { call.reject("adresse fehlt"); return; }
        JSObject texte = call.getObject("texte", new JSObject());
        prefs().edit()
            .putString("adresse", adresse)
            .putString("texte", texte.toString())
            .putLong("hoehe", -1L) // -1: beim ersten Lauf nur den Stand merken, nichts melden
            .putBoolean("an", true)
            .apply();
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("meldung") != PermissionState.GRANTED) {
            requestPermissionForAlias("meldung", call, "nachBerechtigung");
            return;
        }
        planen(getContext());
        call.resolve(stand());
    }

    @PermissionCallback
    private void nachBerechtigung(PluginCall call) {
        planen(getContext());
        call.resolve(stand());
    }

    @PluginMethod
    public void ausschalten(PluginCall call) {
        WorkManager.getInstance(getContext()).cancelUniqueWork(ARBEIT);
        WorkManager.getInstance(getContext()).cancelUniqueWork("eingang-naechste");
        WorkManager.getInstance(getContext()).cancelUniqueWork("eingang-jetzt");
        prefs().edit().clear().apply();
        call.resolve(stand());
    }

    @PluginMethod
    public void stand(PluginCall call) { call.resolve(stand()); }

    @PluginMethod
    public void jetzt(PluginCall call) {
        WorkManager.getInstance(getContext()).enqueueUniqueWork("eingang-jetzt", ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(EingangArbeit.class).build());
        call.resolve();
    }

    @PluginMethod
    public void akkuEinstellung(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void geoeffnetMit(PluginCall call) {
        JSObject r = new JSObject();
        r.put("txid", geoeffnetTxid);
        geoeffnetTxid = null;
        call.resolve(r);
    }

    private JSObject stand() {
        JSObject r = new JSObject();
        r.put("an", prefs().getBoolean("an", false));
        String b;
        if (Build.VERSION.SDK_INT < 33) b = "granted";
        else {
            PermissionState s = getPermissionState("meldung");
            b = s == PermissionState.GRANTED ? "granted" : s == PermissionState.DENIED ? "denied" : "prompt";
        }
        r.put("berechtigung", b);
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        r.put("akkuFrei", pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName()));
        return r;
    }

    static void planen(Context ctx) {
        Constraints c = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(EingangArbeit.class, 15, TimeUnit.MINUTES)
            .setConstraints(c).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(ARBEIT, ExistingPeriodicWorkPolicy.UPDATE, w);
        // Einmal gleich: den aktuellen Stand merken, damit nur Neues gemeldet wird.
        WorkManager.getInstance(ctx).enqueueUniqueWork("eingang-jetzt", ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(EingangArbeit.class).setConstraints(c).build());
    }
}
