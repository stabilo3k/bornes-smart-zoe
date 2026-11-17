package org.borneneutre.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Bornes de recharge publiques et semi-publiques de Flandre (jeu de données « Laadpunten voor elektrische voertuigen »,
 * Departement Mobiliteit en Openbare Werken, source Eco-Movement, licence Open Data Vlaanderen). Un enregistrement par connecteur,
 * regroupés ici par emplacement. Ne couvre ni Bruxelles ni la Wallonie.
 */
object Flanders {
    private const val WFS = "https://geoserver.gis.cloud.mow.vlaanderen.be/geoserver/beleid/wfs"
    @Volatile var lastError: String = ""

    private fun url(lat: Double, lon: Double, radiusM: Int): String {
        val dLat = radiusM / 111_000.0
        val dLon = radiusM / (111_000.0 * Math.cos(Math.toRadians(lat)))
        val f = String.format(java.util.Locale.US, "latitude>%.5f AND latitude<%.5f AND longitude>%.5f AND longitude<%.5f", lat - dLat, lat + dLat, lon - dLon, lon + dLon)
        return WFS + "?SERVICE=WFS&version=2.0.0&request=GetFeature&typeNames=beleid:laadpunten_public&count=2000&outputFormat=application%2Fjson&srsName=EPSG:4326&CQL_FILTER=" + URLEncoder.encode(f, "UTF-8")
    }

    /** Regroupe les connecteurs par emplacement et opérateur. */
    fun parse(json: String): List<Station> {
        val arr = JSONObject(json).optJSONArray("features") ?: return emptyList()
        class Acc(var lat: Double, var lon: Double, val op: String, val addr: String, val city: String) {
            val evse = HashSet<String>(); var kw = 0.0; var ac = false; var dc = false; var semi = false
        }
        val groups = LinkedHashMap<String, Acc>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i).optJSONObject("properties") ?: continue
            val lat = p.optDouble("latitude", Double.NaN); val lon = p.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val op = p.optString("uitbater", "")
            val key = String.format(java.util.Locale.US, "%.5f,%.5f,%s", lat, lon, op)
            val g = groups.getOrPut(key) { Acc(lat, lon, op, p.optString("adres", ""), p.optString("gemeente", "")) }
            g.evse.add(p.opt("evse")?.toString()?.takeIf { it != "null" && it.isNotBlank() } ?: p.optString("uniek_identificatienummer", "") + i)
            g.kw = maxOf(g.kw, p.optDouble("maximaal_vermogen_kw", 0.0).let { if (it.isNaN()) 0.0 else it })
            val st = p.optString("stroomtype", "").lowercase()
            if (st.startsWith("dc")) g.dc = true else if (st.startsWith("ac")) g.ac = true
            if (!p.optString("toegankelijkheid", "publiek").equals("publiek", ignoreCase = true)) g.semi = true
        }
        return groups.map { (key, g) ->
            val tags = JSONObject()
            if (g.kw > 0) tags.put("charging_station:output", "${if (g.kw % 1.0 == 0.0) g.kw.toInt().toString() else g.kw.toString()} kW")
            tags.put("capacity", g.evse.size.toString())
            if (g.semi) tags.put("access", "semipublic")
            tags.put("source", "Open Data Vlaanderen / Eco-Movement")
            listOf(g.addr, g.city).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { tags.put("addr:full", it) }
            val name = listOf(g.op, g.addr).filter { it.isNotBlank() }.joinToString(" – ").ifBlank { "Borne (Flandre)" }
            Station("fl-" + key, name, g.op, g.lat, g.lon, if (g.dc && !g.ac) "DC" else if (g.ac) "AC" else "?", "", "osm", tags.toString())
        }
    }

    fun fetch(lat: Double, lon: Double, radiusM: Int): List<Station>? {
        val t0 = System.currentTimeMillis()
        return try {
            val c = URL(url(lat, lon, radiusM)).openConnection() as HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "BorneNeutre/1.0 (Android)"); c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code != 200) { lastError = "HTTP $code"; DiagLog.add("Flandre autour ($radiusM m) : HTTP $code, ${System.currentTimeMillis() - t0} ms"); return null }
            val r = parse(c.inputStream.bufferedReader().readText())
            lastError = ""; DiagLog.add("Flandre autour ($radiusM m) : OK ${r.size} borne(s), ${System.currentTimeMillis() - t0} ms"); r
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName
            DiagLog.add("Flandre autour ($radiusM m) : ${e.javaClass.simpleName} ${(e.message ?: "").take(60)}, ${System.currentTimeMillis() - t0} ms"); null
        }
    }

    /** Sauvegarde / relecture du cache local (liste de stations déjà regroupées). */
    fun toJson(list: List<Station>): String = JSONArray().also { a -> list.forEach { s ->
        a.put(JSONObject().put("id", s.id).put("n", s.name).put("o", s.operator).put("la", s.lat).put("lo", s.lon).put("c", s.current).put("p", s.params)) } }.toString()

    fun fromJson(json: String): List<Station> {
        val a = JSONArray(json); val out = ArrayList<Station>()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i)
            out.add(Station(o.getString("id"), o.getString("n"), o.optString("o"), o.getDouble("la"), o.getDouble("lo"), o.optString("c", "?"), "", "osm", o.optString("p", "{}"))) }
        return out
    }
}
