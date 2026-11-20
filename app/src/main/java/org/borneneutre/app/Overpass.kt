package org.borneneutre.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Bornes de recharge OpenStreetMap autour de la position GPS (données © contributeurs OpenStreetMap, ODbL). */
object Overpass {
    private val ENDPOINTS = listOf(
        "https://lz4.overpass-api.de/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    /** Dernière cause d'échec (code HTTP ou type d'erreur), affichée à l'utilisateur pour le diagnostic. */
    @Volatile var lastError: String = ""

    fun query(lat: Double, lon: Double, radiusM: Int): String =
        "[out:json][timeout:12];nwr[\"amenity\"=\"charging_station\"](around:$radiusM,$lat,$lon);out center tags;"

    /** Extrait les bornes d'une réponse Overpass ; les éléments sans coordonnées sont ignorés. */
    fun parse(json: String): List<Station> {
        val arr = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Station>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val c = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.getDouble("lat") else c?.optDouble("lat", Double.NaN) ?: Double.NaN
            val lon = if (e.has("lon")) e.getDouble("lon") else c?.optDouble("lon", Double.NaN) ?: Double.NaN
            if (lat.isNaN() || lon.isNaN()) continue
            val t = e.optJSONObject("tags") ?: JSONObject()
            val operator = t.optString("operator", t.optString("brand", ""))
            val name = t.optString("name", "").ifBlank {
                listOf(operator, t.optString("addr:street", ""), t.optString("addr:housenumber", "")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Borne OpenStreetMap" }
            }
            val cls = classify(t, operator)
            out.add(Station("osm-" + e.getString("type") + "-" + e.getLong("id"), name, operator, lat, lon, cls, "", "osm", t.toString()))
        }
        return out
    }

    /**
     * Classement courant continu (DC) / alternatif (AC) d'après les étiquettes OSM (test13).
     * DC si : prise combo ou CHAdeMO sans Type 2 ; ou Type 2 déclaré à plus de 43 kW (un Type 2 en alternatif ne dépasse pas 43 kW) ;
     * ou exploitant de bornes rapides connu (Electra, Fastned, Ionity) ; ou Tesla Supercharger. Sinon AC si Type 2, sinon « ? ».
     */
    fun isFastOperator(op: String): Boolean = Regex("electra|fastned|ionity").containsMatchIn(op.lowercase())

    fun classify(t: JSONObject, operator: String): String {
        val hasT2 = t.has("socket:type2")
        if ((t.has("socket:type2_combo") || t.has("socket:chademo")) && !hasT2) return "DC"
        val op = (operator + " " + t.optString("brand", "") + " " + t.optString("network", "")).lowercase()
        if (Regex("electra|fastned|ionity").containsMatchIn(op)) return "DC"
        if (op.contains("tesla") && (op.contains("supercharger") || t.has("socket:tesla_supercharger"))) return "DC"
        if (t.has("socket:tesla_supercharger") && !hasT2) return "DC"
        if (hasT2) {
            val kw = StationInfo.powerKw(Station("", "", "", 0.0, 0.0, "?", "", "osm", t.toString()))
            if (kw != null && kw > 43.0) return "DC"
            return "AC"
        }
        return "?"
    }

    /**
     * Appel réseau (à lancer hors du fil principal) ; renvoie null si aucun serveur ne répond.
     * Requêtes « décalées » : le premier serveur part tout de suite, le suivant part après 3 s sans réponse
     * (ou dès que le précédent a échoué) ; la première réponse valable gagne et les autres sont coupées.
     * Budget total : 26 s. Journal du 18/11 : les quatre serveurs publics répondaient 502 ou dépassaient 9 s.
     */
    fun fetch(lat: Double, lon: Double, radiusM: Int): List<Station>? {
        val body = "data=" + URLEncoder.encode(query(lat, lon, radiusM), "UTF-8")
        val errs = java.util.Collections.synchronizedList(ArrayList<String>())
        val result = java.util.concurrent.atomic.AtomicReference<List<Station>?>(null)
        val failures = java.util.concurrent.atomic.AtomicInteger(0)
        val answered = java.util.Collections.synchronizedSet(HashSet<String>())
        val latch = java.util.concurrent.CountDownLatch(1)
        val conns = java.util.Collections.synchronizedList(ArrayList<HttpURLConnection>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(ENDPOINTS.size)
        val start = System.currentTimeMillis()
        ENDPOINTS.forEachIndexed { i, ep ->
            pool.execute {
                val host = ep.substringAfter("//").substringBefore("/")
                try { while (latch.count > 0L && System.currentTimeMillis() - start < i * STAGGER_MS && failures.get() < i) Thread.sleep(100) }
                catch (_: InterruptedException) { return@execute }
                if (latch.count == 0L) return@execute
                val t0 = System.currentTimeMillis()
                try {
                    val c = URL(ep).openConnection() as HttpURLConnection
                    conns.add(c); answered.add("start:$host")
                    c.requestMethod = "POST"; c.connectTimeout = 5000; c.readTimeout = 15000; c.doOutput = true
                    c.setRequestProperty("User-Agent", "BorneNeutre/1.3 (Android; +https://github.com/stabilo3k/bornes-smart-zoe)")
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    c.setRequestProperty("Accept", "application/json")
                    c.outputStream.use { it.write(body.toByteArray()) }
                    val code = c.responseCode
                    answered.add("end:$host")
                    if (code == 200) {
                        val r = parse(c.inputStream.bufferedReader().readText())
                        if (result.compareAndSet(null, r)) { DiagLog.add("OSM autour ($radiusM m) $host : OK ${r.size} borne(s), ${System.currentTimeMillis() - t0} ms"); latch.countDown() }
                    } else {
                        errs.add("$host : HTTP $code"); failures.incrementAndGet()
                        DiagLog.add("OSM autour ($radiusM m) $host : HTTP $code, ${System.currentTimeMillis() - t0} ms")
                    }
                } catch (e: Exception) {
                    answered.add("end:$host")
                    if (latch.count > 0L) {
                        errs.add("$host : " + e.javaClass.simpleName); failures.incrementAndGet()
                        DiagLog.add("OSM autour ($radiusM m) $host : ${e.javaClass.simpleName} ${(e.message ?: "").take(60)}, ${System.currentTimeMillis() - t0} ms")
                    }
                }
            }
        }
        latch.await(BUDGET_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        val ok = result.get()
        if (ok == null) {
            // Une ligne par serveur qui n'avait pas fini à la fin du budget (test13).
            ENDPOINTS.forEach { ep -> val h = ep.substringAfter("//").substringBefore("/")
                if ("end:$h" !in answered) DiagLog.add("OSM autour ($radiusM m) $h : " + (if ("start:$h" in answered) "pas de réponse à ${BUDGET_MS / 1000} s" else "pas essayé (budget épuisé)")) }
            DiagLog.add("OSM autour ($radiusM m) : aucun serveur n'a répondu en ${BUDGET_MS / 1000} s, abandon")
        }
        latch.countDown()
        synchronized(conns) { conns.forEach { runCatching { it.disconnect() } } }
        pool.shutdown() // les fils sortent seuls : le verrou est à zéro et les connexions sont coupées
        if (ok != null) { lastError = ""; return ok }
        lastError = synchronized(errs) { errs.joinToString(" ; ") }
        return null
    }

    private const val STAGGER_MS = 3000L
    private const val BUDGET_MS = 10_000L

    /** Téléchargement unique d'une grande zone (liste mensuelle) ; renvoie le texte brut ou null. Une seule requête, 60 s max. */
    fun fetchBox(s: Double, w: Double, n: Double, e: Double): String? {
        val q = "[out:json][timeout:60];nwr[\"amenity\"=\"charging_station\"]($s,$w,$n,$e);out center tags;"
        val body = "data=" + URLEncoder.encode(q, "UTF-8")
        for (ep in ENDPOINTS) {
            val t0 = System.currentTimeMillis()
            val host = ep.substringAfter("//").substringBefore("/")
            try {
                val c = URL(ep).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 70000; c.doOutput = true
                c.setRequestProperty("User-Agent", "BorneNeutre/1.2 (Android; +https://github.com/stabilo3k/bornes-smart-zoe)"); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                if (code == 200) { val t = c.inputStream.bufferedReader().readText(); DiagLog.add("OSM zone $host : OK ${t.length / 1024} Ko, ${System.currentTimeMillis() - t0} ms"); return t }
                DiagLog.add("OSM zone $host : HTTP $code, ${System.currentTimeMillis() - t0} ms")
            } catch (e: Exception) {
                DiagLog.add("OSM zone $host : ${e.javaClass.simpleName} ${(e.message ?: "").take(60)}, ${System.currentTimeMillis() - t0} ms")
            }
        }
        return null
    }

    /** Bornes de `found` qui ne sont pas à moins de `maxM` mètres d'une borne de `known` (doublons écartés ; index en grille pour les longues listes). */
    fun dedupe(known: List<Station>, found: List<Station>, maxM: Double = 25.0): List<Station> {
        val step = 0.0006
        val grid = HashMap<Long, MutableList<Station>>()
        fun cell(lat: Double, lon: Double) = Math.floor(lat / step).toLong() to Math.floor(lon / step).toLong()
        fun key(y: Long, x: Long) = y * 1_000_003L + x
        known.forEach { val (y, x) = cell(it.lat, it.lon); grid.getOrPut(key(y, x)) { ArrayList() }.add(it) }
        return found.filter { f ->
            val (cy, cx) = cell(f.lat, f.lon)
            !(-1L..1L).any { dy -> (-1L..1L).any { dx -> grid[key(cy + dy, cx + dx)]?.any { Geo.distanceM(f.lat, f.lon, it.lat, it.lon) < maxM } == true } }
        }
    }

    /** Pour chaque borne de `found` à moins de `maxM` m d'une borne de `known` : identifiant de la borne connue la plus proche (relevés fusionnés sur une seule fiche). */
    fun aliasOf(known: List<Station>, found: List<Station>, maxM: Double = 40.0): Map<String, String> {
        val out = HashMap<String, String>()
        for (f in found) {
            var best: Station? = null
            var bd = maxM
            for (k in known) {
                if (k.id == f.id) continue
                val d = Geo.distanceM(f.lat, f.lon, k.lat, k.lon)
                if (d < bd) { bd = d; best = k }
            }
            if (best != null) out[f.id] = best.id
        }
        return out
    }

    /** Bornes candidates triées par distance ; les bornes OSM à moins de 25 m d'une borne déjà connue sont écartées (doublons). */
    fun candidates(lat: Double, lon: Double, known: List<Station>, found: List<Station>, maxM: Double): List<Pair<Station, Double>> {
        val all = known + dedupe(known, found)
        return all.filter { it.current != "DC" }
            .map { it to Geo.distanceM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxM }
            .sortedBy { it.second }
    }
}
