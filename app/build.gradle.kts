plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yskar.wallet"

    // Compose 1.12+ und core-ktx 1.19+ verlangen compileSdk 37.
    // targetSdk bleibt bewusst auf 36.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.yskar.wallet"
        minSdk = 26
        targetSdk = 36
        // 3.0.0: Diese Wallet loest die Fassungen 1 und 2 ab (Capacitor,
        // Kennung app.yskar.wallet), die im selben Repository erschienen sind.
        // Fuer Android ist sie trotzdem eine NEUE App - andere Kennung, anderer
        // Schluessel. versionCode zaehlt fuer diese App von vorn; 30000 steht
        // fuer 3.00.00 und laesst Platz fuer 3.0.1 = 30001, 3.1.0 = 30100.
        versionCode = 30000
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Eigene applicationId, damit Debug- und Release-Build parallel
            // auf dem Geraet liegen koennen.
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Mit built-in Kotlin erbt kotlin.compilerOptions.jvmTarget automatisch
    // den Wert von targetCompatibility - ein eigener jvmTarget-Block ist unnoetig.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Startbildschirm. Ab Android 12 kann das die Plattform selbst, aber
    // minSdk ist 26 - und diese Bibliothek macht aus beiden Faellen eine
    // einzige Beschreibung. Dazu kommt `setKeepOnScreenCondition`, das es
    // in der Plattform-API so nicht gibt.
    implementation("androidx.core:core-splashscreen:1.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    // Noetig, damit createAndroidComposeRule die Activity starten kann.
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Ed25519 fuer minSdk 26: Androids eigenes Ed25519 gibt es erst ab API 33.
    // BouncyCastles leichte API registriert keinen JCE-Provider und kann
    // darum auch keinen anderen stoeren.
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    // Biometrie. Stabil ist nur 1.1.0; die aktive Linie ist 1.4.0-beta01.
    // Fuer eine Finanz-App zuerst die stabile - und auf dem Geraet geprueft,
    // nicht nur angenommen.
    implementation("androidx.biometric:biometric:1.1.0")
    // Biometrie 1.1.0 zieht ueber appcompat 1.2.0 fragment 1.2.5 herein. Dessen
    // FragmentActivity lehnt Anfragenummern ueber 16 Bit ab ("Can only use lower
    // 16 bits for requestCode") - und genau solche vergibt activity 1.13 fuer
    // rememberLauncherForActivityResult. Folge: Absturz bei jeder Frage nach
    // Kamera oder Mitteilungen (gefunden 14.11.2026 an der Release-Fassung; die
    // Debug-Fassung hatte beides laengst erlaubt und fragte darum nie).
    // Ab fragment 1.3 ist die Pruefung weg.
    implementation("androidx.fragment:fragment:1.8.9")

    // Zustand ueber Drehungen hinweg. Ohne das verliert die Wallet beim
    // Drehen des Geraets ihren entsperrten Zustand.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // QR-Codes. Nur der reine Java-Kern, keine Android-Bindung: Die App
    // ZEICHNET den Code selbst - und LIEST ihn selbst, siehe ui/Scanner.kt.
    implementation("com.google.zxing:core:3.5.4")

    // Kamera fuer den QR-Scanner. Die stabile Linie, wie bei der Biometrie.
    //
    // WARUM NICHT ML KIT ODER DER GOOGLE-CODE-SCANNER: Beide haengen an den
    // Google Play Services. Eine Wallet, die ihre Schluessel selbst
    // verwahrt, soll zum Lesen eines Schwarzweissmusters keinen fremden
    // Dienst brauchen. CameraX liefert die Bilder, ZXing entschluesselt sie
    // auf dem Geraet - beides laeuft ohne Netz und ohne Konto.
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    // Der Waechter: regelmaessig im Hintergrund nachsehen, ob etwas
    // eingegangen ist.
    //
    // WARUM NICHT FIREBASE CLOUD MESSAGING: Echtes Push hiesse, dass ein
    // Server die Adresse dieses Geraets kennt und fuer uns beobachtet.
    // Diese Wallet redet mit genau einem Gegenueber, und niemand sonst
    // weiss, welche Adresse hier liegt. WorkManager laesst das Geraet
    // selbst nachsehen - beim selben Spiegel, den die App ohnehin fragt.
    // Dazu kaeme bei FCM wieder die Abhaengigkeit von den Play Services,
    // die wir schon beim Scanner vermieden haben.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    // Liest tests/vergleichswerte.json. Nur im Test - die App selbst
    // braucht keinen JSON-Parser aus einer Bibliothek.
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    // Ausdruecklich die aktuellen stabilen Fassungen: Der Compose-Testrahmen
    // zieht sonst Espresso 3.5.0 und runner 1.5.0 von 2022 herein. Die rufen
    // per Reflexion android.hardware.input.InputManager.getInstance() auf,
    // das es unter Android 17 nicht mehr gibt - jeder Test scheitert dann
    // schon beim Start der Activity.
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:monitor:1.8.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// `QrCodeTest` liest das Bildschirmfoto vom Geraet (bilder/06_empfangen.png).
// Ohne diese Angabe haelt Gradle den Testlauf fuer aktuell, wenn sich NUR
// das Foto geaendert hat, und zeigt das Ergebnis von davor - am 13.11.2026
// stand so noch "uebergangen" da, obwohl das neue Foto laengst passte.
// Fehlt das Foto (ohne Geraet, im oeffentlichen Stand), stoert das nicht.
tasks.withType<Test>().configureEach {
    inputs.files(rootProject.file("bilder/06_empfangen.png"))
        .withPropertyName("bildschirmfoto")
        .optional()
}
