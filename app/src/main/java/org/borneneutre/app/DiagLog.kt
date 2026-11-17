package org.borneneutre.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de diagnostic : lignes des 14 derniers jours, au plus 400 lignes (environ 40 Ko), dans un fichier du téléphone.
 * Ne contient ni position GPS ni identité. Les passages sans rien à signaler ne sont pas notés.
 */
object DiagLog {
    const val MAX_LINES = 400
    const val MAX_DAYS = 14L
    private class Entry(val ts: Long, val text: String)
    private val entries = ArrayList<Entry>()
    private var file: File? = null
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

    @Synchronized fun init(c: Context) {
        if (file != null) return
        val f = File(c.applicationContext.filesDir, "diag.log"); file = f
        try {
            if (f.exists()) f.readLines().forEach { l ->
                val i = l.indexOf('|'); val t = if (i > 0) l.substring(0, i).toLongOrNull() else null
                if (t != null) entries.add(Entry(t, l.substring(i + 1)))
            }
            // Reprise de l'ancien journal (50 lignes dans les préférences, sans année) : abandonné, il ne servait qu'au diagnostic de la 1.0.2.
            c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().remove("diag").apply()
        } catch (_: Exception) {}
        trim(System.currentTimeMillis()); rewrite()
    }

    internal fun trim(now: Long) {
        val min = now - MAX_DAYS * 86_400_000L
        entries.removeAll { it.ts < min }
        while (entries.size > MAX_LINES) entries.removeAt(0)
    }

    private fun rewrite() { try { file?.writeText(entries.joinToString("\n") { "${it.ts}|${it.text}" } + if (entries.isEmpty()) "" else "\n") } catch (_: Exception) {} }

    @Synchronized fun add(what: String) {
        val now = System.currentTimeMillis()
        val e = Entry(now, what.replace("\n", " ").take(200))
        entries.add(e)
        if (entries.size > MAX_LINES + 50) { trim(now); rewrite() }
        else try { file?.appendText("${e.ts}|${e.text}\n") } catch (_: Exception) {}
    }

    @Synchronized fun text(): String =
        "Bornes Smart et Zoé ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}\n" +
            (if (entries.isEmpty()) "(journal vide)" else entries.takeLast(MAX_LINES).joinToString("\n") { fmt.format(Date(it.ts)) + " " + it.text })

    @Synchronized fun clear() { entries.clear(); try { file?.delete() } catch (_: Exception) {} }
}
