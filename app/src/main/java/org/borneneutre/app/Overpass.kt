package org.borneneutre.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Bornes de recharge OpenStreetMap autour de la position GPS (données © contributeurs OpenStreetMap, ODbL). */
object Overpass {
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    /** Dernière cause d'échec (code HTTP ou type d'erreur), affichée à l'utilisateur pour le diagnostic. */
    @Volatile var lastError: String = ""

    fun query(lat: Double, lon: Double, radiusM: Int): String =
        "[out:json][timeout:25];nwr(around:$radiusM,$lat,$lon)[\"amenity\"=\"charging_station\"];out center tags;"

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
            val dc = (t.has("socket:type2_combo") || t.has("socket:chademo")) && !t.has("socket:type2")
            val ac = t.has("socket:type2")
            out.add(Station("osm-" + e.getString("type") + "-" + e.getLong("id"), name, operator, lat, lon, if (dc) "DC" else if (ac) "AC" else "?", "", "osm", t.toString()))
        }
        return out
    }

    /** Appel réseau (à lancer hors du fil principal) ; renvoie null si aucun serveur ne répond. */
    fun fetch(lat: Double, lon: Double, radiusM: Int): List<Station>? {
        val body = "data=" + URLEncoder.encode(query(lat, lon, radiusM), "UTF-8")
        val errs = ArrayList<String>()
        for (ep in ENDPOINTS) {
            try {
                val c = URL(ep).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 30000; c.doOutput = true
                c.setRequestProperty("User-Agent", "BorneNeutre/1.0 (Android)")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.setRequestProperty("Accept", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                if (code == 200) { lastError = ""; return parse(c.inputStream.bufferedReader().readText()) }
                errs.add(ep.substringAfter("//").substringBefore("/") + " : HTTP " + code)
            } catch (e: Exception) {
                errs.add(ep.substringAfter("//").substringBefore("/") + " : " + e.javaClass.simpleName)
            }
        }
        lastError = errs.joinToString(" ; ")
        return null
    }

    /** Téléchargement unique d'une grande zone (liste mensuelle) ; renvoie le texte brut ou null. Une seule requête, 60 s max. */
    fun fetchBox(s: Double, w: Double, n: Double, e: Double): String? {
        val q = "[out:json][timeout:60];nwr[\"amenity\"=\"charging_station\"]($s,$w,$n,$e);out center tags;"
        val body = "data=" + URLEncoder.encode(q, "UTF-8")
        for (ep in ENDPOINTS) {
            try {
                val c = URL(ep).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 70000; c.doOutput = true
                c.setRequestProperty("User-Agent", "BorneNeutre/1.0 (Android)"); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode == 200) return c.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Bornes candidates triées par distance ; les bornes OSM à moins de 25 m d'une borne déjà connue sont écartées (doublons). */
    fun candidates(lat: Double, lon: Double, known: List<Station>, found: List<Station>, maxM: Double): List<Pair<Station, Double>> {
        val all = known + found.filter { f -> known.none { Geo.distanceM(f.lat, f.lon, it.lat, it.lon) < 25 } }
        return all.filter { it.current != "DC" }
            .map { it to Geo.distanceM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxM }
            .sortedBy { it.second }
    }
}
