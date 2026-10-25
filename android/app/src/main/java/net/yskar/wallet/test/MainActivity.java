package net.yskar.wallet.test;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Eigene Plugins vor super.onCreate registrieren, sonst kennt die
        // Bruecke sie nicht.
        registerPlugin(SichtschutzPlugin.class);
        registerPlugin(EingangPlugin.class);
        super.onCreate(savedInstanceState);
        merken(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        merken(intent);
    }

    /** Ueber eine Eingangs-Meldung geoeffnet: die TXID fuer die Oberflaeche aufheben. */
    private void merken(Intent intent) {
        if (intent == null) return;
        String txid = intent.getStringExtra("eingang_txid");
        if (txid != null && !txid.isEmpty()) {
            EingangPlugin.geoeffnetTxid = txid;
            intent.removeExtra("eingang_txid");
        }
    }
}
