package org.borneneutre.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Mise à jour mensuelle des listes de bornes (OpenStreetMap Belgique, Bruxelles) hébergées dans le dépôt public.
 *  Les fichiers téléchargés remplacent ceux embarqués ; en cas d'échec, les fichiers embarqués restent utilisés. */
object DataUpdate {
    const val BASE = "https://raw.githubusercontent.com/stabilo3k/bornes-smart-zoe/main/data/"
    const val EMBEDDED_DATE = "2026-11-17"
    const val OSM_FILE = "bornes-be-osm.json"
    const val BXL_FILE = "bornes-bruxelles.json"
    private const val CHECK_EVERY_MS = 30L * 24 * 3600 * 1000

    /** Fichier téléchargé s'il existe et reste lisible, sinon null (l'appelant retombe sur l'asset). */
    fun localFile(ctx: Context, name: String): File? = File(ctx.filesDir, name).takeIf { it.isFile && it.length() > 100 }

    fun currentDate(ctx: Context): String =
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("data_date", null) ?: EMBEDDED_DATE

    fun due(ctx: Context): Boolean =
        System.currentTimeMillis() - ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getLong("data_checked", 0L) > CHECK_EVERY_MS

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "BorneNeutre/1.0 (Android)")
        return if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else { DiagLog.add("Mise à jour listes : HTTP ${c.responseCode} ${url.substringAfterLast('/')}"); null }
    }

    /** Vérifie et télécharge si une liste plus récente existe. Retourne un message pour l'utilisateur ; updated = true si les listes ont changé. */
    fun update(ctx: Context): Pair<Boolean, String> {
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return try {
            val meta = JSONObject(get(BASE + "meta.json") ?: return false to "Serveur injoignable ou liste non publiée. Réessayez plus tard.")
            prefs.edit().putLong("data_checked", System.currentTimeMillis()).apply()
            val remote = meta.optString("date", "")
            if (remote.isBlank() || remote <= currentDate(ctx)) return false to "Listes déjà à jour (${currentDate(ctx)})."
            val osm = get(BASE + OSM_FILE) ?: return false to "Téléchargement impossible. Réessayez plus tard."
            val bxl = get(BASE + BXL_FILE) ?: return false to "Téléchargement impossible. Réessayez plus tard."
            // contrôle de plausibilité avant de remplacer quoi que ce soit
            val nOsm = Overpass.parse(osm).size; val nBxl = Brussels.parse(bxl).size
            if (nOsm < 1000 || nBxl < 100) { DiagLog.add("Mise à jour listes refusée : OSM $nOsm, Bruxelles $nBxl"); return false to "Fichiers reçus incomplets, ignorés." }
            File(ctx.filesDir, OSM_FILE).writeText(osm); File(ctx.filesDir, BXL_FILE).writeText(bxl)
            prefs.edit().putString("data_date", remote).apply()
            DiagLog.add("Mise à jour listes : $remote, OSM $nOsm, Bruxelles $nBxl")
            true to "Listes mises à jour ($remote) : $nOsm bornes OpenStreetMap, $nBxl à Bruxelles."
        } catch (e: Exception) {
            DiagLog.add("Mise à jour listes : ${e.javaClass.simpleName}")
            false to "Mise à jour impossible (${e.javaClass.simpleName}). Les listes embarquées restent utilisées."
        }
    }
}
