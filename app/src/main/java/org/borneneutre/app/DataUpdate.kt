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
    const val EMBEDDED_DATE = "2026-11-20"
    const val OSM_FILE = "bornes-be-osm.json"
    const val BXL_FILE = "bornes-bruxelles.json"
    const val WAL_FILE = "bornes-wallonie.json"
    private const val CHECK_EVERY_MS = 24L * 3600 * 1000 // seul meta.json est lu chaque jour ; les listes ne sont téléchargées que si la date change

    /** Fichier téléchargé s'il existe et reste lisible, sinon null (l'appelant retombe sur l'asset). */
    fun localFile(ctx: Context, name: String): File? =
        File(ctx.filesDir, name).takeIf { it.isFile && it.length() > 100 && prefDate(ctx) >= EMBEDDED_DATE } // une liste téléchargée plus ancienne que celle embarquée (après mise à jour de l'app) est ignorée

    /** Écriture atomique : fichier temporaire puis renommage, pour qu'une coupure ne laisse jamais un fichier tronqué. */
    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) { tmp.delete(); throw java.io.IOException("renommage impossible") } }
    }

    private fun prefDate(ctx: Context): String =
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("data_date", null) ?: EMBEDDED_DATE

    /** Date des listes réellement utilisées : la plus récente entre la dernière mise à jour reçue et la liste embarquée. */
    fun currentDate(ctx: Context): String = maxOf(prefDate(ctx), EMBEDDED_DATE)

    fun due(ctx: Context): Boolean =
        System.currentTimeMillis() - ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getLong("data_checked", 0L) > CHECK_EVERY_MS

    /** Âge des listes en jours (date des données), ou null si la date est illisible. */
    fun ageDays(ctx: Context): Long? = try { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(currentDate(ctx).take(10)), java.time.LocalDate.now()) } catch (_: Exception) { null }
    fun ageLabel(ctx: Context): String = ageDays(ctx)?.let { if (it <= 0) " (à jour)" else if (it == 1L) " (il y a 1 jour)" else " (il y a $it jours)" } ?: ""

    fun sha256(text: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Garde-fou relatif : refuse un fichier dont le nombre d'éléments tombe de plus de 10 % ou monte de plus de 25 % par rapport au précédent. Sans précédent : accepté. */
    fun plausible(prev: Int, now: Int): Boolean = prev <= 0 || (now >= prev * 0.90 && now <= prev * 1.25)

    /** Empreinte déclarée dans meta.json pour un fichier (« files » → nom → « sha256 »), ou null si meta.json ne la donne pas. */
    fun declaredSha(meta: JSONObject, file: String): String? = meta.optJSONObject("files")?.optJSONObject(file)?.optString("sha256", "")?.takeIf { it.isNotBlank() }

    private fun prevCount(ctx: Context, key: String, embedded: () -> Int): Int {
        val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return if (p.contains(key) && prefDate(ctx) >= EMBEDDED_DATE) // base périmée si l'application embarque une liste plus récente
            p.getInt(key, 0) else runCatching(embedded).getOrDefault(0)
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "BorneNeutre/1.3 (Android; +https://github.com/stabilo3k/bornes-smart-zoe)")
        return if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else { DiagLog.add("Mise à jour listes : HTTP ${c.responseCode} ${url.substringAfterLast('/')}"); null }
    }

    /** Vérifie et télécharge si une liste plus récente existe. Retourne un message pour l'utilisateur ; updated = true si les listes ont changé. */
    fun update(ctx: Context): Pair<Boolean, String> {
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return try {
            val meta = JSONObject(get(BASE + "meta.json") ?: return false to "Serveur injoignable ou liste non publiée. Réessayez plus tard.")
            prefs.edit().putLong("data_checked", System.currentTimeMillis()).apply()
            val remote = meta.optString("date", "")
            // même date mais contenu différent (plusieurs passages le même jour) : l'empreinte annoncée par meta.json le détecte
            val shaChanged = declaredSha(meta, OSM_FILE)?.let { !it.equals(prefs.getString("data_sha_osm", null), ignoreCase = true) } ?: false
            if (remote.isBlank() || remote < currentDate(ctx) || (remote == currentDate(ctx) && !shaChanged)) return false to "Listes déjà à jour (${currentDate(ctx)})."
            if (remote + declaredSha(meta, OSM_FILE) == prefs.getString("data_refused", null)) return false to "Liste du $remote déjà refusée (contrôle de plausibilité) : ancienne liste conservée."
            val osm = get(BASE + OSM_FILE) ?: return false to "Téléchargement impossible. Réessayez plus tard."
            val bxl = get(BASE + BXL_FILE) ?: return false to "Téléchargement impossible. Réessayez plus tard."
            // contrôle de plausibilité avant de remplacer quoi que ce soit : empreintes (si meta.json les donne), taille minimale, variation relative −10 % / +25 %
            for ((name, txt) in listOf(OSM_FILE to osm, BXL_FILE to bxl)) declaredSha(meta, name)?.let { if (!it.equals(sha256(txt), ignoreCase = true)) { DiagLog.add("Mise à jour listes refusée : empreinte de $name différente"); prefs.edit().putString("data_refused", remote + declaredSha(meta, OSM_FILE)).apply(); return false to "Fichiers reçus non conformes à l'empreinte annoncée, ignorés." } }
            val nOsm = Overpass.parse(osm).size; val nBxl = Brussels.parse(bxl).size
            if (nOsm < 1000 || nBxl < 100) { DiagLog.add("Mise à jour listes refusée : OSM $nOsm, Bruxelles $nBxl"); prefs.edit().putString("data_refused", remote + declaredSha(meta, OSM_FILE)).apply(); return false to "Fichiers reçus incomplets, ignorés." }
            val pOsm = prevCount(ctx, "n_osm") { Overpass.parse(ctx.assets.open(OSM_FILE).bufferedReader().readText()).size }
            val pBxl = prevCount(ctx, "n_bxl") { Brussels.parse(ctx.assets.open(BXL_FILE).bufferedReader().readText()).size }
            if (!plausible(pOsm, nOsm) || !plausible(pBxl, nBxl)) { DiagLog.add("Mise à jour listes refusée (variation) : OSM $pOsm→$nOsm, Bruxelles $pBxl→$nBxl"); prefs.edit().putString("data_refused", remote + declaredSha(meta, OSM_FILE)).apply(); return false to "Variation trop forte du nombre de bornes (OSM $pOsm → $nOsm, Bruxelles $pBxl → $nBxl) : ancienne liste conservée." }
            writeAtomic(File(ctx.filesDir, OSM_FILE), osm); writeAtomic(File(ctx.filesDir, BXL_FILE), bxl)
            // Wallonie (SPW) : facultatif, une absence ou un fichier suspect laisse la liste précédente en place
            var nWal = -1
            if (meta.optString("wal", "").isNotBlank()) {
                val wal = runCatching { get(BASE + WAL_FILE) }.getOrNull()
                val n = wal?.let { runCatching { Wallonie.parse(it).size }.getOrDefault(0) } ?: 0
                val pWal = prevCount(ctx, "n_wal") { Wallonie.parse(ctx.assets.open(WAL_FILE).bufferedReader().readText()).size }
                val shaOk = declaredSha(meta, WAL_FILE)?.let { it.equals(sha256(wal ?: ""), ignoreCase = true) } ?: true
                if (wal != null && n >= 1000 && shaOk && plausible(pWal, n)) { writeAtomic(File(ctx.filesDir, WAL_FILE), wal); nWal = n; prefs.edit().putInt("n_wal", n).apply() }
                else DiagLog.add("Mise à jour listes : Wallonie ignorée ($n emplacements)")
            }
            prefs.edit().putString("data_date", remote).putString("data_sha_osm", sha256(osm)).putInt("n_osm", nOsm).putInt("n_bxl", nBxl).apply()
            DiagLog.add("Mise à jour listes : $remote, OSM $nOsm, Bruxelles $nBxl" + (if (nWal >= 0) ", Wallonie $nWal" else ""))
            true to "Listes mises à jour ($remote) : $nOsm bornes OpenStreetMap, $nBxl à Bruxelles" + (if (nWal >= 0) ", $nWal en Wallonie." else ".")
        } catch (e: Exception) {
            DiagLog.add("Mise à jour listes : ${e.javaClass.simpleName}")
            false to "Mise à jour impossible (${e.javaClass.simpleName}). Les listes embarquées restent utilisées."
        }
    }
}
