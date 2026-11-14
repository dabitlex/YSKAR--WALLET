package com.yskar.wallet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yskar.wallet.R
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Eine Adresse von einem QR-Code ablesen.
 *
 * DER GANZE WEG BLEIBT AUF DEM GERAET. CameraX liefert die Bilder, der
 * reine Java-Kern von ZXing entschluesselt sie - kein ML Kit, kein
 * Google-Code-Scanner, keine Play Services. Eine Wallet, die ihre
 * Schluessel selbst verwahrt, soll zum Lesen eines Schwarzweissmusters
 * keinen fremden Dienst brauchen.
 *
 * WAS HIER NICHT PASSIERT: Es wird nichts gespeichert und nichts
 * weitergereicht. Die Bilder leben so lange wie ein `ImageProxy` und
 * werden danach geschlossen. Was herauskommt, ist eine Zeichenkette.
 *
 * UND DIE WIRD GEPRUEFT, BEVOR SIE GILT. Ein QR-Code ist kein Beweis -
 * jeder kann einen drucken. Was nicht `Address.isValid` besteht, wird
 * abgelehnt und nicht ins Feld geschrieben. Den Rest macht ohnehin der
 * Pruefschritt, der die Adresse im Klartext zeigt.
 */
@Composable
fun ScannerScreen(
    onGefunden: (String) -> Unit,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
    // Was aus einem gelesenen Code wird - null heisst: nicht das Gesuchte,
    // weitersuchen. Vorgabe: eine Adresse (REGELN.md, Regel 22). Fuer die
    // Anmeldung liest dieselbe Kamera einen Anmelde-Code.
    lesen: (String) -> String? = ::adresseAus,
    // Ueberschrift, Anleitung und der Satz bei einem fremden Code - null:
    // die fuer das Scannen einer Adresse.
    titel: String? = null,
    anleitung: String? = null,
    keinTreffer: String? = null,
) {
    val ctx = LocalContext.current
    val t = LocalTexte.current
    val ruetteln = kurzesRuetteln()

    var erlaubt by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var abgelehnt by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var licht by remember { mutableStateOf(false) }

    val frager = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ja ->
        erlaubt = ja
        abgelehnt = !ja
    }

    // Erst fragen, wenn der Scanner wirklich geoeffnet wird. Eine Wallet,
    // die beim ersten Start nach der Kamera fragt, hat schon verloren.
    LaunchedEffect(Unit) {
        if (!erlaubt) frager.launch(Manifest.permission.CAMERA)
    }

    // Ein falscher Code soll die Anleitung nicht fuer immer verdraengen.
    // Ohne das bleibt "Das ist keine YSKAR-Adresse." stehen, auch wenn
    // der Nutzer laengst woandershin zielt.
    LaunchedEffect(fehler) {
        if (fehler != null) {
            delay(2_500)
            fehler = null
        }
    }

    BackHandler { onAbbrechen() }

    ScannerBild(
        erlaubt = erlaubt,
        abgelehnt = abgelehnt,
        fehler = fehler,
        licht = licht,
        onLicht = { licht = !licht },
        onEinstellungen = { einstellungenOeffnen(ctx) },
        onAbbrechen = onAbbrechen,
        modifier = modifier,
        titel = titel,
        anleitung = anleitung,
    ) {
        Sucher(
            licht = licht,
            onText = { roh ->
                val gelesen = lesen(roh)
                if (gelesen != null) {
                    ruetteln()
                    onGefunden(gelesen)
                } else {
                    fehler = keinTreffer ?: t.senden.scannerKeineAdresse
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Was der Scanner zeigt - ohne Kamera, ohne Erlaubnisfrage, ohne Zustand.
 *
 * Getrennt vom Bildschirm darueber, damit sich JEDER Zustand zeichnen
 * laesst: auch der ohne Kameraerlaubnis, den man auf einem Geraet, das sie
 * schon erteilt hat, sonst nie zu sehen bekaeme. Das Kamerabild kommt als
 * [sucher] herein; `Bildschirmbilder` reicht dort nichts oder die echte
 * Kamera durch.
 *
 * Vorlage: design-neu/html/12_scanner.html.
 */
@Composable
fun ScannerBild(
    erlaubt: Boolean,
    abgelehnt: Boolean,
    fehler: String?,
    licht: Boolean,
    onLicht: () -> Unit,
    onEinstellungen: () -> Unit,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
    titel: String? = null,
    anleitung: String? = null,
    sucher: @Composable () -> Unit,
) {
    val t = LocalTexte.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // Schwarz nur hinter der Kamera: Bis ihr erstes Bild kommt,
            // soll dort kein heller Grund aufblitzen. Ohne Kamera bleibt
            // der Grund der App stehen.
            // Bis hinter Status- und Navigationsleiste: Der Grund der App ist
            // oben hell, und ueber dem Kamerabild stuende sonst ein blauer
            // Streifen (am Geraet gesehen, 12.11.2026).
            .then(
                if (erlaubt) {
                    Modifier.drawBehind {
                        val mehr = 400.dp.toPx()
                        drawRect(
                            Color.Black,
                            topLeft = Offset(0f, -mehr),
                            size = Size(size.width, size.height + 2 * mehr),
                        )
                    }
                } else Modifier,
            ),
    ) {
        // EINE Geometrie fuer beide. Wer den Rahmen zeichnet und wer den
        // Hinweis darunter setzt, rechnen aus denselben Werten.
        val kante = minOf(minOf(maxWidth, maxHeight) * 0.72f, 300.dp)
        val oben = maxHeight * 0.2f

        if (erlaubt) {
            sucher()
            Rahmen(oben = oben, kante = kante, modifier = Modifier.fillMaxSize())
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                titel ?: t.senden.scannerTitel,
                style = Schrift.Titel,
                color = Farbe.Schrift,
                modifier = Modifier.weight(1f),
            )
            Symbolknopf(R.drawable.ic_x_bold, t.allgemein.abbrechen, onAbbrechen)
        }

        // Der Hinweis gehoert zum Rahmen, nicht zum unteren Bildrand.
        // Ohne Kamera gibt es keinen Rahmen - dann steht die Absage in
        // der Mitte, wo sie niemand uebersieht.
        val stelle = if (erlaubt) {
            Modifier
                .align(Alignment.TopStart)
                .offset(y = oben + kante + 30.dp)
        } else {
            Modifier.align(Alignment.Center)
        }

        Column(
            stelle
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                !erlaubt && abgelehnt -> Hinweis(t.senden.scannerOhneKamera, rot = true)

                !erlaubt -> Hinweis(t.senden.scannerWartetAufFreigabe, rot = false)

                fehler != null -> Hinweis(fehler, rot = true)

                else -> Hinweis(anleitung ?: t.senden.scannerAnleitung, rot = false)
            }

            if (!erlaubt && abgelehnt) {
                Knopf(
                    t.senden.scannerZuDenEinstellungen, onEinstellungen,
                    art = Knopfart.FLAECHE,
                )
            } else if (erlaubt) {
                Lichtknopf(an = licht, onKlick = onLicht)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Symbol(R.drawable.ic_shield_check_fill, 16.dp, farbe = Farbe.Weich)
            Text(
                t.senden.scannerNichtsGespeichert,
                style = Schrift.Hinweis,
                color = Farbe.Weich,
            )
        }
    }
}

@Composable
private fun Hinweis(text: String, rot: Boolean) {
    Text(
        text,
        style = Schrift.Text,
        color = if (rot) Farbe.WarnSchrift else Farbe.Schrift,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Der Knopf fuer das Licht: dunkles Glas mit hellem Rand, damit er auf dem
 * Kamerabild steht, was immer dahinter ist. Nur hier gebraucht, darum hier.
 */
@Composable
private fun Lichtknopf(an: Boolean, onKlick: () -> Unit) {
    val t = LocalTexte.current
    Row(
        Modifier
            .heightIn(min = 50.dp)
            .gefasst(
                rundung = 25.dp,
                grund = SolidColor(Color(0xB8071229)),
                rand = Farbe.hauch(0.18f),
                glanz = 0f,
            )
            .clickable(role = Role.Button, onClick = onKlick)
            .padding(horizontal = 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Symbol(R.drawable.ic_flashlight_fill, 19.dp)
        Text(
            if (an) t.senden.scannerLichtAus else t.senden.scannerLichtAn,
            style = Schrift.Kopf,
            color = Farbe.Schrift,
        )
    }
}

private fun einstellungenOeffnen(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", ctx.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// --------------------------------------------------------------- Kamera

@Composable
private fun Sucher(
    licht: Boolean,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val besitzer = LocalLifecycleOwner.current
    val ausfuehrer = remember { Executors.newSingleThreadExecutor() }
    val kamera = remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose { ausfuehrer.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            val sicht = PreviewView(c).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val haupt = ContextCompat.getMainExecutor(c)
            val zukunft = ProcessCameraProvider.getInstance(c)
            zukunft.addListener({
                runCatching {
                    val anbieter = zukunft.get()
                    val vorschau = Preview.Builder().build().also {
                        it.surfaceProvider = sicht.surfaceProvider
                    }
                    val analyse = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    // Der Leser laeuft auf einem eigenen Faden; das Ergebnis
                    // muss zurueck auf den Hauptfaden, bevor es Compose
                    // anfasst.
                    analyse.setAnalyzer(ausfuehrer, QrLeser { t -> haupt.execute { onText(t) } })

                    anbieter.unbindAll()
                    kamera.value = anbieter.bindToLifecycle(
                        besitzer,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        vorschau,
                        analyse,
                    )
                    kamera.value?.cameraControl?.enableTorch(licht)
                }
            }, haupt)
            sicht
        },
    )

    LaunchedEffect(licht) {
        runCatching { kamera.value?.cameraControl?.enableTorch(licht) }
    }
}

/**
 * Der Leser.
 *
 * ZXing bekommt die Helligkeitsebene des Kamerabilds - mehr braucht es
 * nicht, ein QR-Code ist schwarzweiss. Gedreht wird nichts: Die Erkennung
 * laeuft ueber die drei Suchmuster in den Ecken und ist damit von der
 * Ausrichtung unabhaengig.
 *
 * Gemeldet wird nur, was sich geaendert hat. Sonst meldet ein Code, der
 * keine YSKAR-Adresse ist, dreissigmal in der Sekunde denselben Fehler.
 */
private class QrLeser(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val leser = qrLeser()
    private var zuletzt: String? = null

    override fun analyze(bild: ImageProxy) {
        try {
            val text = qrText(leser, bild.helligkeit(), bild.width, bild.height)
            if (text != null && text != zuletzt) {
                zuletzt = text
                onText(text)
            }
        } finally {
            bild.close()
        }
    }
}

/**
 * Die Helligkeitsebene als dichtes Feld.
 *
 * Die Kamera liefert Zeilen mit Luecken (`rowStride` ist groesser als die
 * Breite) und je nach Geraet auch Abstaende zwischen den Bildpunkten.
 * Wer das uebersieht, bekommt ein schraeg verzerrtes Bild, in dem ZXing
 * nie etwas findet.
 */
private fun ImageProxy.helligkeit(): ByteArray {
    val ebene = planes[0]
    val puffer = ebene.buffer.also { it.rewind() }
    val zeilenschritt = ebene.rowStride
    val punktschritt = ebene.pixelStride

    val ziel = ByteArray(width * height)
    val zeile = ByteArray(zeilenschritt)
    var pos = 0
    for (y in 0 until height) {
        val da = minOf(zeilenschritt, puffer.remaining())
        if (da <= 0) break
        puffer.get(zeile, 0, da)
        if (punktschritt == 1) {
            System.arraycopy(zeile, 0, ziel, pos, minOf(width, da))
        } else {
            for (x in 0 until width) {
                val q = x * punktschritt
                if (q < da) ziel[pos + x] = zeile[q]
            }
        }
        pos += width
    }
    return ziel
}


// ---------------------------------------------------------------- Rahmen

/**
 * Der Rahmen: aussen abgedunkelt, vier Ecken in der Farbe des Zeichens,
 * in der Mitte eine feine Linie.
 *
 * Kein geschlossenes Rechteck - vier Winkel sagen "hier hinein" und
 * verdecken dabei weniger vom Bild. Die Linie steht still: Sie zeigt die
 * Mitte, sie tut nicht, als suche sie.
 *
 * Lage und Groesse kommen von aussen. Sie werden eine Ebene hoeher
 * einmal gerechnet, damit der Hinweistext weiss, wo der Rahmen aufhoert.
 */
@Composable
private fun Rahmen(oben: Dp, kante: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = kante.toPx()
        val links = (size.width - k) / 2f
        val o = oben.toPx()
        val rechts = links + k
        val unten = o + k
        val rundung = 30.dp.toPx()

        val loch = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(
                RoundRect(
                    Rect(links, o, rechts, unten),
                    CornerRadius(rundung, rundung),
                ),
            )
            fillType = PathFillType.EvenOdd
        }
        drawPath(loch, Color(0xAD02050F))

        // Eine Ecke: senkrechtes Stueck, Viertelkreis, waagrechtes Stueck -
        // in der Vorlage "M3 53 V31 A28 28 0 0 1 31 3 h22". Gezeichnet wird
        // sie einmal oben links und dann dreimal um die Mitte gedreht.
        val bogen = 28.dp.toPx()
        val schenkel = 22.dp.toPx()
        val ecke = Path().apply {
            moveTo(links, o + bogen + schenkel)
            lineTo(links, o + bogen)
            arcTo(
                Rect(links, o, links + 2f * bogen, o + 2f * bogen),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(links + bogen + schenkel, o)
        }
        val strich = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val mitte = Offset((links + rechts) / 2f, (o + unten) / 2f)
        for (grad in floatArrayOf(0f, 90f, 180f, 270f)) {
            rotate(grad, pivot = mitte) {
                drawPath(ecke, YskarFarben.BlauHell, style = strich)
            }
        }

        val einzug = 22.dp.toPx()
        val linie = 2.dp.toPx()
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0f to YskarFarben.BlauHell.copy(alpha = 0f),
                0.5f to YskarFarben.BlauHell,
                1f to YskarFarben.BlauHell.copy(alpha = 0f),
                startX = links + einzug,
                endX = rechts - einzug,
            ),
            topLeft = Offset(links + einzug, o + k * 0.536f - linie / 2f),
            size = Size(k - 2f * einzug, linie),
            cornerRadius = CornerRadius(linie / 2f),
        )
    }
}
