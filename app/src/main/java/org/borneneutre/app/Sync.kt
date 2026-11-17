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
                    .also { if (r.remote) it.put("remote", true); if (r.kw != null) it.put("kw", r.kw) }
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
            rl.add(Releve(cid, sid, o.optString("ts", ""), Result.valueOf(o.getString("result")), null, "", false, car, o.optBoolean("remote", false),
                if (o.has("kw") && !o.isNull("kw")) o.optDouble("kw").takeIf { !it.isNaN() } else null))
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
        if (rows.length() > 0 || waiting > 0) DiagLog.add("synchro : $waiting en attente / ${rows.length()} à envoyer")
        status = when {
            waiting > 0 -> "envoi en attente : $waiting relevé(s) ($lastCode)"
            rows.length() > 0 -> "${rows.length()} relevé(s) envoyé(s)" + (if (priv > 0) ", $priv privé(s) non envoyé(s)" else "")
            priv > 0 -> "$priv relevé(s) sur borne privée : non envoyés"
            else -> "envoi : rien à envoyer"
        }
        val all = JSONArray()
        var offset = 0
        // Colonnes facultatives (remote, kw) : elles n'existent qu'après la mise à jour SQL ; repli sur moins de colonnes si le serveur répond 400.
        val ladder = listOf(",remote,kw", ",remote", "")
        var level = 0
        while (offset < 20000) {
            val base = "releves?select=client_id,station_id,station_name,lat,lon,current,result,car,ts"
            var r = call(base + ladder[level] + "&order=client_id.asc&limit=1000&offset=$offset", "GET", null)
            while (r.body == null && r.code == 400 && level < ladder.size - 1) {
                level++; DiagLog.add("lecture relevés : colonnes absentes (HTTP 400), repli niveau $level")
                r = call(base + ladder[level] + "&order=client_id.asc&limit=1000&offset=$offset", "GET", null)
            }
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

    // ---- États de borne (table « etats », SQL 1.0.3) : panne, occupée, file, libre, déclaré par un tiers. ----

    fun etatRows(etats: List<Etat>, stations: Map<String, Station>, device: String, alreadySent: Set<String>): JSONArray {
        val out = JSONArray()
        for (e in etats) {
            if (e.id in alreadySent) continue
            val s = stations[e.stationId] ?: continue
            if (s.kind == "privee") continue
            out.put(
                JSONObject().put("client_id", e.id).put("device", device).put("station_id", s.id)
                    .put("station_name", s.name.take(120)).put("lat", s.lat).put("lon", s.lon)
                    .put("kind", e.kind.code).put("level", if (e.declared) "declare" else "test")
                    .put("car", canonicalCar(e.car) ?: "").put("source", e.source.take(40))
            )
        }
        return out
    }

    fun etatsFromRows(json: String): List<Etat> {
        val a = JSONArray(json)
        val out = ArrayList<Etat>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val k = EtatKind.of(o.optString("kind", "")) ?: continue
            val ts = try { java.time.OffsetDateTime.parse(o.optString("created_at", "")).toInstant().toEpochMilli() } catch (_: Exception) { continue }
            out.add(Etat(o.getString("client_id"), o.getString("station_id"), k, o.optString("level", "test") == "declare", o.optString("car", ""), o.optString("source", ""), ts))
        }
        return out
    }

    /** Envoie les états de l'utilisateur puis télécharge ceux des 14 derniers jours. Ne lève jamais d'exception ; null si la table n'existe pas encore ou si le réseau échoue. */
    fun syncEtats(ctx: Context, mine: List<Etat>, stations: Map<String, Station>): String? = try {
        val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val sent = p.getStringSet("synced_etats", emptySet()) ?: emptySet()
        val rows = etatRows(mine, stations, deviceId(ctx), sent)
        if (rows.length() > 0) {
            val done = upload(rows) { body ->
                try { val r = call("etats?on_conflict=client_id", "POST", body, PREFER); DiagLog.add("envoi états : HTTP ${r.code} ${(r.err ?: "").take(90)}"); classify(r.code, r.err) }
                catch (e: Exception) { DiagLog.add("envoi états : ${e.javaClass.simpleName}"); -1 }
            }
            p.edit().putStringSet("synced_etats", sent + done).apply()
        }
        val since = java.time.Instant.now().minusSeconds(14L * 24 * 3600).toString()
        val r = call("etats?select=client_id,station_id,kind,level,car,source,created_at&created_at=gte.$since&order=created_at.desc&limit=1000", "GET", null)
        if (r.body == null) { DiagLog.add("lecture états : HTTP ${r.code} ${(r.err ?: "").take(90)}"); null } else r.body
    } catch (e: Exception) {
        DiagLog.add("états : ${e.javaClass.simpleName} ${(e.message ?: "").take(60)}")
        null
    }

    // ---- Fiches de borne (table « fiches », SQL 1.0.4) : nombre de bornes, prises par borne, puissance déclarée. ----

    fun ficheRows(fiches: List<Fiche>, stations: Map<String, Station>, device: String, alreadySent: Set<String>): JSONArray {
        val out = JSONArray()
        for (f in fiches) {
            if (f.id in alreadySent) continue
            val s = stations[f.stationId] ?: continue
            if (s.kind == "privee") continue
            out.put(JSONObject().put("client_id", f.id).put("device", device).put("station_id", s.id)
                .put("station_name", s.name.take(120)).put("lat", s.lat).put("lon", s.lon)
                .also { o -> f.points?.let { o.put("points", it) }; f.plugs?.let { o.put("plugs", it) }; f.kw?.let { o.put("kw", it) }; f.contact?.let { o.put("contact", it) } })
        }
        return out
    }

    fun fichesFromRows(json: String): List<Fiche> {
        val a = JSONArray(json)
        val out = ArrayList<Fiche>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val ts = try { java.time.OffsetDateTime.parse(o.optString("created_at", "")).toInstant().toEpochMilli() } catch (_: Exception) { continue }
            out.add(Fiche(o.getString("client_id"), o.getString("station_id"), o.optString("station_name", ""), o.optDouble("lat", 0.0), o.optDouble("lon", 0.0),
                if (o.has("points") && !o.isNull("points")) o.optInt("points") else null, if (o.has("plugs") && !o.isNull("plugs")) o.optInt("plugs") else null,
                if (o.has("kw") && !o.isNull("kw")) o.optDouble("kw").takeIf { v -> !v.isNaN() } else null, ts,
                o.optString("contact", "").ifBlank { null }))
        }
        return out
    }

    /** Envoie les fiches de l'utilisateur puis télécharge celles de la communauté. Ne lève jamais d'exception ; null si la table n'existe pas ou si le réseau échoue. */
    fun syncFiches(ctx: Context, mine: List<Fiche>, stations: Map<String, Station>): String? = try {
        val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val sent = p.getStringSet("synced_fiches", emptySet()) ?: emptySet()
        val rows = ficheRows(mine, stations, deviceId(ctx), sent)
        if (rows.length() > 0) {
            val done = upload(rows) { body ->
                try { val r = call("fiches?on_conflict=client_id", "POST", body, PREFER); DiagLog.add("envoi fiches : HTTP ${r.code} ${(r.err ?: "").take(90)}"); classify(r.code, r.err) }
                catch (e: Exception) { DiagLog.add("envoi fiches : ${e.javaClass.simpleName}"); -1 }
            }
            p.edit().putStringSet("synced_fiches", sent + done).apply()
        }
        val cols = "client_id,station_id,station_name,lat,lon,points,plugs,kw"
        var r = call("fiches?select=$cols,contact,created_at&order=created_at.desc&limit=2000", "GET", null)
        if (r.body == null && r.code == 400) r = call("fiches?select=$cols,created_at&order=created_at.desc&limit=2000", "GET", null) // colonne contact absente avant la mise à jour SQL
        if (r.body == null) { DiagLog.add("lecture fiches : HTTP ${r.code} ${(r.err ?: "").take(90)}"); null } else r.body
    } catch (e: Exception) {
        DiagLog.add("fiches : ${e.javaClass.simpleName} ${(e.message ?: "").take(60)}")
        null
    }
}
