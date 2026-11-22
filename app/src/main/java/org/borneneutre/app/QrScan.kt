package org.borneneutre.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import java.util.concurrent.Executors

/** Lecteur de QR : la caméra n'est utilisée que pour lire le texte du code ; aucune image n'est enregistrée ni envoyée. */
@Composable
fun QrScanDialog(onTexts: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    // Retour des réglages Android : relire la permission sans exiger un second toucher.
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    val exec = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) { onDispose { runCatching { provider?.unbindAll() }; exec.shutdown() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { QrIcon(22.dp); Text("  Lire le QR de la borne") } },
        text = {
            Column {
                if (!granted) Column {
                    Text("La caméra sert uniquement à lire le code : aucune photo n'est enregistrée ni envoyée. Autorisez la caméra pour continuer.", fontSize = 13.sp)
                    TextButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
                    // Après un refus définitif, Android n'affiche plus la demande : on ouvre les réglages de l'application.
                    TextButton(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))) } }) { Text("Ouvrir les réglages de l'application") }
                }
                else if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) Text("Ce téléphone n'a pas de caméra utilisable : la lecture de QR n'est pas disponible.", fontSize = 13.sp)
                else {
                    Text("Cadrez le QR collé sur la borne. Seul le texte du code est lu sur ce téléphone ; aucune photo n'est enregistrée ni envoyée.", fontSize = 12.sp, color = Color(0xFF4B5563))
                    var done = false
                    Box(Modifier.fillMaxWidth().height(260.dp).clipToBounds()) { AndroidView(modifier = Modifier.matchParentSize(), factory = { c ->
                        val pv = PreviewView(c)
                        pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        val fut = ProcessCameraProvider.getInstance(c)
                        fut.addListener({
                            try {
                                val p = fut.get(); provider = p
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.EAN_13), DecodeHintType.TRY_HARDER to true)
                                val reader = GenericMultipleBarcodeReader(MultiFormatReader().also { it.setHints(hints) })
                                analysis.setAnalyzer(exec) { img ->
                                    try {
                                        if (!done) {
                                            val plane = img.planes[0]; val buf = plane.buffer
                                            val data = ByteArray(buf.remaining()); buf.get(data)
                                            val src = PlanarYUVLuminanceSource(data, plane.rowStride, img.height, 0, 0, img.width, img.height, false)
                                            val texts = try { reader.decodeMultiple(BinaryBitmap(HybridBinarizer(src)), hints).map { it.text }.filter { !it.isNullOrBlank() }.distinct() } catch (_: Exception) { emptyList() }
                                            if (texts.isNotEmpty()) { done = true; ContextCompat.getMainExecutor(c).execute { onTexts(texts) } }
                                        }
                                    } finally { img.close() }
                                }
                                p.unbindAll()
                                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            } catch (e: Exception) { DiagLog.add("QR : ${e.javaClass.simpleName}") }
                        }, ContextCompat.getMainExecutor(c))
                        pv
                    }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

/** Pictogramme QR dessiné (trois repères d'angle et quelques modules) : remplace l'emoji appareil photo. */
@Composable
fun QrIcon(size: androidx.compose.ui.unit.Dp, color: Color = Color(0xFF1F2937)) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 9f
        fun finder(x: Float, y: Float) {
            drawRect(color, androidx.compose.ui.geometry.Offset(x * u, y * u), androidx.compose.ui.geometry.Size(3f * u, 3f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 0.8f))
            drawRect(color, androidx.compose.ui.geometry.Offset((x + 1f) * u, (y + 1f) * u), androidx.compose.ui.geometry.Size(u, u))
        }
        finder(0f, 0f); finder(6f, 0f); finder(0f, 6f)
        listOf(4f to 4f, 6f to 4f, 5f to 5f, 7f to 5f, 4f to 6f, 6f to 6f, 5f to 7f, 7f to 7f, 4f to 0f, 4f to 2f, 0f to 4f, 2f to 4f).forEach { (x, y) ->
            drawRect(color, androidx.compose.ui.geometry.Offset(x * u, y * u), androidx.compose.ui.geometry.Size(u, u))
        }
    }
}

/** Pastille ronde coupée en deux (moitié verte, moitié bleue) : icône du bouton « Sticker sur la borne ». */
@Composable
fun StickerIcon(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val d = this.size.minDimension
        drawArc(Color(0xFF16A34A), 90f, 180f, true, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(d, d))
        drawArc(Color(0xFF1D4ED8), 270f, 180f, true, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(d, d))
        drawCircle(Color.White, d * 0.30f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = d * 0.07f))
    }
}
