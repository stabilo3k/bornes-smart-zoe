package org.borneneutre.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.json.JSONArray

data class FaqItem(val id: String, val q: String, val a: String, val src: String, val img: String = "", val sec: String = "")

object Faq {
    fun parse(json: String): List<FaqItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FaqItem(o.getString("id"), o.getString("q"), o.getString("a"), o.optString("src", ""), o.optString("img", ""), o.optString("sec", ""))
        }
    }

    /** Le Glossaire vient toujours en dernier, quel que soit l'ordre du fichier. */
    fun glossaryLast(l: List<FaqItem>): List<FaqItem> = l.filter { it.sec != "Glossaire" } + l.filter { it.sec == "Glossaire" }

    fun load(ctx: Context): List<FaqItem> =
        glossaryLast(parse(ctx.assets.open("faq.json").bufferedReader().readText()))
}

/** Mise en forme légère des réponses de la FAQ : paragraphes séparés par une ligne vide, puces « - » ou « • », numéros « 1) », **gras**, [texte](https://…). */
sealed class FaqBlock {
    data class Para(val text: String, val lead: Boolean = false) : FaqBlock()
    data class Item(val mark: String, val text: String) : FaqBlock()
}

private val ITEM = Regex("^(?:([•-])|(\\d+\\))) (.*)$")

fun faqBlocks(a: String): List<FaqBlock> {
    val out = mutableListOf<FaqBlock>()
    var first = true
    for (chunk in a.split("\n\n")) {
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotBlank()) {
                var t = buf.toString().trim()
                if (t.startsWith("Détail : ")) t = "**Détail :** " + t.removePrefix("Détail : ")
                out += FaqBlock.Para(t, first)
                first = false
            }
            buf.clear()
        }
        for (raw in chunk.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = ITEM.find(line)
            if (m != null) {
                flush()
                out += FaqBlock.Item(m.groupValues[2].ifEmpty { "•" }, m.groupValues[3])
                first = false
            } else {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(line)
            }
        }
        flush()
    }
    return out
}

private val INLINE = Regex("\\*\\*(.+?)\\*\\*|\\[([^\\]]+)]\\((https://[^)\\s]+)\\)")

/** Gras et liens https uniquement (jamais d'autre schéma) ; un lien s'ouvre dans le navigateur, à l'appui de la personne. */
fun faqStyled(t: String, link: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in INLINE.findAll(t)) {
        append(t.substring(i, m.range.first))
        i = m.range.last + 1
        if (m.groups[1] != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        else withLink(LinkAnnotation.Url(m.groupValues[3], TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))) { append(m.groupValues[2]) }
    }
    append(t.substring(i))
}
