package org.borneneutre.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Vérification d'une nouvelle version de l'application (installation hors magasin, par exemple depuis GitHub).
 *  - Un seul petit fichier est lu : data/version.json du dépôt public (aucune donnée personnelle envoyée).
 *  - Rien n'est téléchargé ni installé sans que l'utilisateur appuie sur « Télécharger » ; Android demande ensuite lui-même de confirmer l'installation.
 *  - Désactivé si l'application a été installée par un magasin (F-Droid, Droidify, Play, Obtainium…) : c'est le magasin qui gère les mises à jour. */
object AppUpdate {
    const val URL_VERSION = DataUpdate.BASE + "version.json"
    const val ALLOWED_PREFIX = "https://github.com/stabilo3k/bornes-smart-zoe/releases/"
    private const val EVERY_MS = 24L * 3600 * 1000
    private const val SNOOZE_MS = 3L * 24 * 3600 * 1000

    /** Version proposée à l'utilisateur (fenêtre de mise à jour) : posée par la vérification au lancement ou par le bouton des réglages. */
    val request = androidx.compose.runtime.mutableStateOf<Info?>(null)

    data class Info(val versionCode: Int, val versionName: String, val apkUrl: String, val pageUrl: String, val notes: String)

    /** Vrai si l'URL est une adresse de release du dépôt du projet (HTTPS uniquement). */
    fun allowedUrl(u: String): Boolean = u.startsWith(ALLOWED_PREFIX) && !u.contains("..") && !u.contains(' ')

    fun parse(json: String): Info? = try {
        val o = JSONObject(json)
        val apk = o.optString("apk", ""); val page = o.optString("page", ALLOWED_PREFIX + "latest")
        val code = o.optInt("versionCode", 0)
        if (code <= 0 || !allowedUrl(apk) || !allowedUrl(page)) null
        else Info(code, o.optString("versionName", code.toString()).take(20), apk, page, o.optString("notes", "").take(400))
    } catch (_: Exception) { null }

    fun isNewer(remoteCode: Int, localCode: Int): Boolean = remoteCode > localCode

    /** Installé par un magasin d'applications (qui gère lui-même les mises à jour) ? */
    fun isStoreInstall(installer: String?): Boolean {
        val i = installer?.lowercase() ?: return false
        return listOf("fdroid", "droidify", "neostore", "aurora", "vending", "accrescent", "obtainium", "galaxy.store", "amazon").any { i.contains(it) }
    }

    /** Empreinte SHA-256 de la clé de publication de l'application (APK GitHub). */
    const val PUBLISH_CERT = "ea2ab4a19e34e5816454817969f91bfb47448236c441aef08e67129f84400f46"

    /** Vrai si l'application installée est signée par une autre clé que la nôtre (ex. compilation F-Droid) : alors aucune vérification de mise à jour n'a lieu. */
    fun foreignSignature(ctx: Context): Boolean = try {
        val pm = ctx.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            if (si == null) emptyArray() else if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else @Suppress("DEPRECATION") (pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures ?: emptyArray())
        val hashes = sigs.map { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
        hashes.isNotEmpty() && PUBLISH_CERT !in hashes
    } catch (_: Exception) { false }

    fun installer(ctx: Context): String? = try {
        if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
        else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName)
    } catch (_: Exception) { null }

    private val prefs get() = "prefs"

    /** Faut-il vérifier maintenant (au lancement) ? */
    /** Variante « fdroid » : aucune vérification de mise à jour (F-Droid s'en charge) ; variante « github » : vérification possible. */
    val enabled: Boolean get() = BuildConfig.UPDATE_CHECK

    fun due(ctx: Context): Boolean {
        if (!enabled) return false
        val p = ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        if (p.getBoolean("upd_off", false) || isStoreInstall(installer(ctx)) || foreignSignature(ctx)) return false
        val now = System.currentTimeMillis()
        return now - p.getLong("upd_checked", 0L) > EVERY_MS && now > p.getLong("upd_snooze_until", 0L)
    }

    /** Lit version.json ; retourne la version proposée si elle est plus récente que celle installée, sinon null. Ne dit rien d'autre. */
    fun check(ctx: Context, force: Boolean = false): Pair<Info?, String> {
        if (!enabled) return null to "Cette version se met à jour par F-Droid : aucune vérification n'est faite par l'application."
        val p = ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        return try {
            val c = URL(URL_VERSION).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "BorneNeutre/${BuildConfig.VERSION_NAME} (Android; +https://github.com/stabilo3k/bornes-smart-zoe)")
            if (c.responseCode != 200) { DiagLog.add("Mise à jour app : HTTP ${c.responseCode}"); return null to "Vérification impossible (serveur). Réessayez plus tard." }
            val info = parse(c.inputStream.bufferedReader().readText())
            p.edit().putLong("upd_checked", System.currentTimeMillis()).apply()
            when {
                info == null -> null to "Réponse illisible, ignorée."
                isNewer(info.versionCode, BuildConfig.VERSION_CODE) -> info to "Version ${info.versionName} disponible."
                else -> null to "Vous avez la dernière version (${BuildConfig.VERSION_NAME})."
            }
        } catch (e: Exception) {
            DiagLog.add("Mise à jour app : ${e.javaClass.simpleName}")
            null to "Vérification impossible (${e.javaClass.simpleName}). Réessayez plus tard."
        }
    }

    fun snooze(ctx: Context) = ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putLong("upd_snooze_until", System.currentTimeMillis() + SNOOZE_MS).apply()
    fun disable(ctx: Context, off: Boolean) = ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putBoolean("upd_off", off).apply()
    fun isDisabled(ctx: Context) = ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE).getBoolean("upd_off", false)

    /** Télécharge l'APK avec le gestionnaire de téléchargements d'Android ; retourne l'identifiant du téléchargement, ou -1 en cas d'échec.
     *  L'ancien téléchargement de mise à jour est supprimé d'abord (pas de « -1 » en double dans Téléchargements). */
    fun download(ctx: Context, info: Info): Long = try {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val p = ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        val old = p.getLong("upd_dl_id", -1L)
        if (old >= 0) try { dm.remove(old) } catch (_: Exception) {}
        val name = "BorneNeutre-${info.versionName}.apk"
        val req = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Bornes Smart et Zoé ${info.versionName}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        val id = dm.enqueue(req)
        p.edit().putLong("upd_dl_id", id).apply()
        id
    } catch (e: Exception) { DiagLog.add("Téléchargement app : ${e.javaClass.simpleName}"); -1L }

    /** État d'un téléchargement : (DownloadManager.STATUS_*, octets reçus, taille totale ou -1). */
    fun progress(ctx: Context, id: Long): Triple<Int, Long, Long> = try {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c.moveToFirst()) Triple(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)), c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
            else Triple(DownloadManager.STATUS_FAILED, 0L, -1L)
        }
    } catch (_: Exception) { Triple(DownloadManager.STATUS_FAILED, 0L, -1L) }

    /** Ouvre l'installateur d'Android sur le fichier téléchargé (variante GitHub : permission d'installation déclarée). Android vérifie lui-même
     *  que la signature est celle de l'application installée et demande confirmation. Retourne faux si l'installateur ne s'ouvre pas. */
    fun install(ctx: Context, id: Long): Boolean = try {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(id)
        if (uri == null) false else {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
    } catch (e: Exception) { DiagLog.add("Installation app : ${e.javaClass.simpleName}"); false }
}
