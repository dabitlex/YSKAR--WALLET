package net.yskar.wallet.test;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Eigene Plugins vor super.onCreate registrieren, sonst kennt die
        // Bruecke sie nicht.
        registerPlugin(SichtschutzPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
