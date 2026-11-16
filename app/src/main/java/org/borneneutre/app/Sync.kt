package org.borneneutre.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Relevés partagés (Supabase). Seuls partent : la borne (nom, position de la borne), le résultat, la date, le modèle de voiture
 * et un identifiant aléatoire de téléphone (anti-abus). Jamais la position GPS de l'utilisateur ni son identité.
 */
object Sync {
    const val URL = "https://evvoufjrzejumdpjgoek.supabase.co"
    const val KEY = "sb_publishable_phgSpnbZ0xQSz93hzalZ9A_mWcgPrmx" // clé publique par conception ; l'accès est limité par les règles de la base

    fun deviceId(ctx: Context): String {
        val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return p.getString("device", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    fun loadSynced(ctx: Context): Set<String> {
        val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        // Version 1.0 : les anciennes versions marquaient « envoyé » des relevés refusés (base absente) ; on renvoie tout une fois (doublons ignorés par le serveur).
        if (!p.getBoolean("sync_reset_v10", false)) p.edit().remove("synced").putBoolean("sync_reset_v10", true).apply()
        return p.getStringSet("synced", emptySet()) ?: emptySet()
    }

    fun saveSynced(ctx: Context, ids: Set<String>) {
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putStringSet("synced", ids).apply()
    }

    /** Lignes à envoyer pour les relevés de l'utilisateur qui n'ont pas encore été envoyés. */
    fun toRows(releves: List<Releve>, stations: Map<String, Station>, device: String, alreadySent: Set<String>): JSONArray {
        val out = JSONArray()
        for (r in releves) {
            if (r.id in alreadySent) continue
            val s = stations[r.stationId] ?: continue
            if (s.kind == "privee") continue
            val car = canonicalCar(r.car) ?: continue
            out.put(
                JSONObject().put("client_id", r.id).put("device", device).put("station_id", s.id)
                    .put("station_name", s.name.take(120)).put("lat", s.lat).put("lon", s.lon)
                    .put("current", if (s.current in listOf("AC", "DC")) s.current else "?")
                    .put("result", r.result.name).put("car", car).put("ts", r.ts)
                    .also { if (r.remote) it.put("remote", true) }
            )
        }
        return out
    }

    /** Relevés et bornes reçus du serveur ; les relevés déjà connus localement (mêmes identifiants) sont ignorés. */
    fun fromRows(json: String, knownReleveIds: Set<String>, knownStationIds: Set<String>): Pair<List<Station>, List<Releve>> {
        val a = JSONArray(json)
        val st = LinkedHashMap<String, Station>()
        val rl = ArrayList<Releve>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val car = canonicalCar(o.optString("car", "")) ?: continue
            val cid = o.getString("client_id")
            if (cid in knownReleveIds) continue
            val sid = o.getString("station_id")
            if (sid !in knownStationIds && sid !in st)
                st[sid] = Station(sid, o.optString("station_name", "Borne"), "", o.getDouble("lat"), o.getDouble("lon"), o.optString("current", "?"), "", "commu")
            rl.add(Releve(cid, sid, o.optString("ts", ""), Result.valueOf(o.getString("result")), null, "", false, car, o.optBoolean("remote", false)))
        }
        return Pair(st.values.toList(), rl)
    }

    private class Resp(val code: Int, val body: String?, val err: String? = null)

    /** Code renvoyé à upload() : 2xx = envoyé ; [REJECTED] = ligne refusée définitivement (violation de contrainte, SQLSTATE 23xxx) ; tout autre = à réessayer plus tard. */
    internal const val REJECTED = 400
    internal fun classify(code: Int, err: String?): Int = when {
        code in 200..299 -> code
        code in 400..499 && err != null && Regex("\"code\"\\s*:\\s*\"23").containsMatchIn(err) -> REJECTED
        else -> -1
    }

    private fun call(path: String, method: String, body: String?, prefer: String? = null): Resp {
        val c = URL("$URL/rest/v1/$path").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("apikey", KEY); c.setRequestProperty("Authorization", "Bearer $KEY")
        if (prefer != null) c.setRequestProperty("Prefer", prefer)
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        return if (code in 200..299) Resp(code, c.inputStream.bufferedReader().readText())
        else Resp(code, null, try { c.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null })
    }

    /** État du dernier envoi, affiché à l'écran pour le diagnostic. */
    @Volatile var status: String = ""
    @Volatile private var lastCode: String = ""

    private const val POST = "releves?on_conflict=client_id"
    private const val PREFER = "resolution=ignore-duplicates,return=minimal"

    /**
     * Envoi par petits lots. Si un lot échoue, ses lignes sont renvoyées une à une : une ligne qui viole une contrainte
     * de la base (code REJECTED) est écartée pour ne pas bloquer les autres ; toute autre erreur (réseau, limite anti-abus, 404, 429, 5xx)
     * arrête l'envoi sans rien marquer : reprise au prochain essai.
     */
    internal fun upload(rows: JSONArray, post: (String) -> Int): Set<String> {
        val done = HashSet<String>()
        fun ids(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it).getString("client_id") }
        var i = 0
        while (i < rows.length()) {
            val chunk = JSONArray(); for (k in i until minOf(i + 10, rows.length())) chunk.put(rows.get(k))
            i += 10
            val c = post(chunk.toString())
            if (c in 200..299) { done += ids(chunk); continue }
            for (k in 0 until chunk.length()) {
                val one = JSONArray().put(chunk.get(k))
                val c1 = post(one.toString())
                if (c1 in 200..299 || c1 == REJECTED) done += ids(one) else return done
            }
        }
        return done
    }

    /** Envoie les nouveaux relevés puis télécharge tous les relevés partagés (par pages de 1 000). Null si le réseau ne répond pas. */
    fun sync(ctx: Context, userReleves: List<Releve>, stations: Map<String, Station>): Pair<String, Set<String>>? = try {
        val sent = loadSynced(ctx)
        val rows = toRows(userReleves, stations, deviceId(ctx), sent)
        var newSent = sent
        val priv = userReleves.count { stations[it.stationId]?.kind == "privee" }
        lastCode = ""
        if (rows.length() > 0) {
            newSent = sent + upload(rows) { body ->
                try { val r = call(POST, "POST", body, PREFER); lastCode = "HTTP ${r.code} ${(r.err ?: "").take(90)}"; DiagLog.add("envoi relevés : $lastCode"); classify(r.code, r.err) }
                catch (e: Exception) { lastCode = e.javaClass.simpleName; DiagLog.add("envoi relevés : $lastCode ${(e.message ?: "").take(60)}"); -1 }
            }
            saveSynced(ctx, newSent)
        }
        val waiting = rows.length() - (newSent - sent).size
        DiagLog.add("synchro : $waiting en attente / ${rows.length()} à envoyer")
        status = when {
            waiting > 0 -> "envoi en attente : $waiting relevé(s) ($lastCode)"
            rows.length() > 0 -> "${rows.length()} relevé(s) envoyé(s)" + (if (priv > 0) ", $priv privé(s) non envoyé(s)" else "")
            priv > 0 -> "$priv relevé(s) sur borne privée : non envoyés"
            else -> "envoi : rien à envoyer"
        }
        val all = JSONArray()
        var offset = 0
        var withRemote = true
        while (offset < 20000) {
            val base = "releves?select=client_id,station_id,station_name,lat,lon,current,result,car,ts"
            // La colonne « remote » n'existe qu'après la mise à jour SQL : repli sans elle si le serveur la refuse.
            var r = call(base + (if (withRemote) ",remote" else "") + "&order=client_id.asc&limit=1000&offset=$offset", "GET", null)
            if (r.body == null && withRemote && r.code == 400) { withRemote = false; DiagLog.add("lecture relevés : colonne remote absente (HTTP 400), repli"); r = call(base + "&order=client_id.asc&limit=1000&offset=$offset", "GET", null) }
            if (r.body == null) DiagLog.add("lecture relevés : HTTP ${r.code} ${(r.err ?: "").take(90)}")
            val page = JSONArray(r.body ?: return null)
            for (k in 0 until page.length()) all.put(page.get(k))
            if (page.length() < 1000) break
            offset += 1000
        }
        Pair(all.toString(), newSent)
    } catch (e: Exception) {
        status = "réseau indisponible (${e.javaClass.simpleName})"
        DiagLog.add("synchro : ${e.javaClass.simpleName} ${(e.message ?: "").take(60)}")
        null
    }
}
