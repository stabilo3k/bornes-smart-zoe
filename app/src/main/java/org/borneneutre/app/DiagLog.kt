package org.borneneutre.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Journal de diagnostic (50 dernières lignes, conservé sur le téléphone). Ne contient ni position GPS ni identité. */
object DiagLog {
    private const val MAX = 50
    private val lines = ArrayList<String>()
    private var ctx: Context? = null
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

    @Synchronized fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        val s = ctx!!.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("diag", "") ?: ""
        if (s.isNotBlank()) lines.addAll(s.split("\n").takeLast(MAX))
    }

    @Synchronized fun add(what: String) {
        lines.add(fmt.format(Date()) + " " + what.replace("\n", " ").take(200))
        while (lines.size > MAX) lines.removeAt(0)
        ctx?.getSharedPreferences("prefs", Context.MODE_PRIVATE)?.edit()?.putString("diag", lines.joinToString("\n"))?.apply()
    }

    @Synchronized fun text(): String =
        "Bornes Smart et Zoé ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}\n" +
            (if (lines.isEmpty()) "(journal vide)" else lines.joinToString("\n"))

    @Synchronized fun clear() { lines.clear(); ctx?.getSharedPreferences("prefs", Context.MODE_PRIVATE)?.edit()?.remove("diag")?.apply() }
}
