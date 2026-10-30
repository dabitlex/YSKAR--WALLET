package app.yskar.wallet;

import android.view.WindowManager;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Sichtschutz fuer die 12 Woerter.
 *
 *   setzen({ an })  -> an: Android sperrt Bildschirmfotos und -aufnahmen und
 *                      zeigt die App in der Uebersicht schwarz.
 *
 * Die Oberflaeche schaltet das nur ein, solange die 12 Woerter zu sehen oder
 * einzugeben sind. Ueberall sonst bleiben Bildschirmfotos moeglich.
 */
@CapacitorPlugin(name = "Sichtschutz")
public class SichtschutzPlugin extends Plugin {

    @PluginMethod
    public void setzen(PluginCall call) {
        final boolean an = Boolean.TRUE.equals(call.getBoolean("an", false));
        getActivity().runOnUiThread(() -> {
            if (an) {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
            call.resolve();
        });
    }
}
