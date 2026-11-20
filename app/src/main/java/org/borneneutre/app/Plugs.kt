package org.borneneutre.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Types de prise indiqués par la source (étiquettes OpenStreetMap « socket:* »). Rien n'est supposé : étiquette absente = aucune prise affichée. */
enum class Plug(val label: String, val tag: String) {
    TYPE2("Type 2", "socket:type2"),
    TYPE2_CABLE("Type 2 (câble)", "socket:type2_cable"),
    CCS("CCS (Combo)", "socket:type2_combo"),
    CHADEMO("CHAdeMO", "socket:chademo"),
    TYPE1("Type 1", "socket:type1"),
    TESLA("Tesla", "socket:tesla_supercharger"),
    DOMESTIC("Prise domestique", "socket:schuko")
}

object Plugs {
    /** Vrai si la valeur de l'étiquette dit « présent » (vide, « no », « 0 » = absent). */
    internal fun present(v: String): Boolean { val t = v.trim().lowercase(); return t.isNotEmpty() && t != "no" && t != "0" && t != "none" }

    /** Prises listées par la source d'après les étiquettes ; liste vide si la source n'en dit rien. */
    fun of(tags: org.json.JSONObject): List<Plug> =
        Plug.values().filter { present(tags.optString(it.tag, "")) || (it == Plug.DOMESTIC && present(tags.optString("socket:typee", ""))) }

    fun of(s: Station): List<Plug> = try { of(org.json.JSONObject(s.params.ifBlank { "{}" })) } catch (_: Exception) { emptyList() }
}

/** Pictogramme schématique (dessiné ici, non officiel) : contour de la prise et ses broches. */
private fun DrawScope.plugBody(p: Plug, color: Color) {
    val w = size.width; val h = size.height
    val sw = w * 0.07f
    fun dots(pts: List<Pair<Float, Float>>, cx: Float, cy: Float, r: Float, dr: Float) = pts.forEach { (x, y) -> drawCircle(color, dr, Offset(cx + x * r, cy + y * r)) }
    fun flatTop(cx: Float, cy: Float, r: Float, cut: Float) {
        // cercle dont le haut est aplati (forme de la prise Type 2)
        val dx = Math.sqrt((1 - cut * cut).toDouble()).toFloat() * r
        val ang = Math.toDegrees(Math.atan2(-cut.toDouble(), Math.sqrt((1 - cut * cut).toDouble()))).toFloat()
        val path = Path().apply {
            moveTo(cx - dx, cy - cut * r); lineTo(cx + dx, cy - cut * r)
            arcTo(Rect(cx - r, cy - r, cx + r, cy + r), ang, 180f - 2 * ang, false)
            close()
        }
        drawPath(path, color, style = Stroke(sw))
    }
    val t2 = listOf(-0.5f to -0.2f, 0f to -0.2f, 0.5f to -0.2f, -0.62f to 0.28f, -0.2f to 0.5f, 0.2f to 0.5f, 0.62f to 0.28f)
    when (p) {
        Plug.TYPE2, Plug.TYPE2_CABLE -> { val r = w * 0.45f; flatTop(w / 2, h / 2, r, 0.55f); dots(t2, w / 2, h / 2, r, w * 0.045f) }
        Plug.CCS -> {
            val r = w * 0.34f; flatTop(w / 2, h * 0.36f, r, 0.55f); dots(t2, w / 2, h * 0.36f, r, w * 0.033f)
            drawCircle(color, w * 0.15f, Offset(w * 0.28f, h * 0.84f), style = Stroke(sw)); drawCircle(color, w * 0.15f, Offset(w * 0.72f, h * 0.84f), style = Stroke(sw))
        }
        Plug.CHADEMO -> { val r = w * 0.45f; drawCircle(color, r, Offset(w / 2, h / 2), style = Stroke(sw))
            dots(listOf(-0.5f to -0.35f, 0.5f to -0.35f, -0.5f to 0.35f, 0.5f to 0.35f, 0f to -0.62f, 0f to 0.62f), w / 2, h / 2, r, w * 0.06f) }
        Plug.TYPE1 -> { val r = w * 0.45f; drawCircle(color, r, Offset(w / 2, h / 2), style = Stroke(sw))
            dots(listOf(-0.5f to -0.25f, 0.5f to -0.25f, -0.55f to 0.3f, 0f to 0.5f, 0.55f to 0.3f), w / 2, h / 2, r, w * 0.06f) }
        Plug.TESLA -> { val r = w * 0.45f; drawCircle(color, r, Offset(w / 2, h / 2), style = Stroke(sw)); dots(listOf(-0.4f to 0f, 0.4f to 0f, 0f to 0.45f), w / 2, h / 2, r, w * 0.07f) }
        Plug.DOMESTIC -> { val r = w * 0.45f; drawCircle(color, r, Offset(w / 2, h / 2), style = Stroke(sw)); dots(listOf(-0.4f to 0f, 0.4f to 0f), w / 2, h / 2, r, w * 0.08f) }
    }
}

/** Puissance, prises et source sur une seule ligne (retour à la ligne seulement si l'écran est étroit). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlugRow(lead: String, plugs: List<Plug>, source: String?) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(lead, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        plugs.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(24.dp)) { plugBody(p, Color(0xFF1F6F43)) }
                Text(" ${p.label}", fontSize = 12.sp)
            }
        }
        if (plugs.isNotEmpty()) Text("prises d'après ${source ?: "la source ouverte"}, schéma", fontSize = 10.sp, color = Color(0xFF6B7280), modifier = Modifier.align(Alignment.CenterVertically))
    }
}
