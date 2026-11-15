package org.borneneutre.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Result { OK, REFUS, LIMITE }

data class Station(
    val id: String,
    val name: String,
    val operator: String,
    val lat: Double,
    val lon: Double,
    val current: String, // AC, DC ou ?
    val note: String,
    val kind: String, // releve, candidat, osm, prive, user
    val params: String = "" // paramètres techniques de la borne (étiquettes OSM, JSON), gardés en interne, jamais affichés
)

data class Releve(
    val id: String,
    val stationId: String,
    val ts: String,
    val result: Result,
    val otherCar: Boolean?,
    val note: String,
    val seed: Boolean,
    val car: String = ""
)

const val DEFAULT_CAR = "Smart EQ fortwo"

/** Liste fermée : voitures dont le chargeur 22 kW est connu pour refuser certains réseaux 3×230 V (Brugel 2020 ; Elbilgrossisten). */
val CARS = listOf("Smart EQ fortwo", "Smart EQ forfour", "Renault Zoé", "Renault Twingo électrique", "Renault Kangoo E-Tech", "Nissan Townstar EV", "Mercedes eCitan")

private fun norm(x: String): String =
    java.text.Normalizer.normalize(x.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Retourne le nom canonique de la voiture, ou null si elle n'est pas dans la liste fermée. Un champ vide (anciens relevés) = Smart EQ fortwo. */
fun canonicalCar(car: String): String? {
    if (car.isBlank()) return DEFAULT_CAR
    val n = norm(car)
    return CARS.firstOrNull { norm(it) == n }
}

fun isKnownCar(car: String) = canonicalCar(car) != null

object Geo {
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}

/** Statut simple d'une borne pour les voitures de la liste : vert / rouge / orange / gris. */
data class SimpleStatus(val label: String, val color: String, val n: Int, val tried: Boolean)

fun simpleStatus(releves: List<Releve>): SimpleStatus {
    val ok = releves.count { it.result == Result.OK }
    val ko = releves.count { it.result == Result.REFUS }
    val n = releves.size
    val s = if (n > 1) "s" else ""
    if (n == 0) return SimpleStatus("Pas encore essayée", "#6b7280", 0, false)
    val share = ok.toDouble() / n
    return when {
        ko == 0 && ok == n -> SimpleStatus("Ça charge ($n relevé$s)", "#1b9e3a", n, true)
        ok == 0 && ko == n -> SimpleStatus("Ne charge pas ($n relevé$s)", "#d62728", n, true)
        n >= 5 && share >= 0.8 -> SimpleStatus("Ça charge en général ($ok sur $n, $ko refus)", "#1b9e3a", n, true)
        n >= 5 && share <= 0.2 -> SimpleStatus("Ne charge pas en général ($ko refus sur $n)", "#d62728", n, true)
        else -> SimpleStatus("Essais opposés : $ok réussi${if (ok > 1) "s" else ""}, $ko refus", "#f28e2b", n, true)
    }
}

object Store {
    private const val FILE = "user_data.json"

    fun loadSeedStations(ctx: Context): List<Station> =
        parseStations(JSONArray(ctx.assets.open("stations.json").bufferedReader().readText()))

    fun loadSeedReleves(ctx: Context): List<Releve> =
        parseReleves(JSONArray(ctx.assets.open("releves.json").bufferedReader().readText()), true)

    /** A priori par borne issus des données ouvertes de Bruxelles (hypothèses non validées). */
    fun loadPriors(ctx: Context): Map<String, Double> = try {
        parsePriors(ctx.assets.open("priors-bruxelles.json").bufferedReader().readText())
    } catch (e: Exception) {
        emptyMap()
    }

    fun parsePriors(json: String): Map<String, Double> {
        val a = JSONObject(json).getJSONArray("priors")
        return (0 until a.length()).associate {
            val o = a.getJSONObject(it)
            o.getString("stationId") to o.getDouble("prior")
        }
    }

    fun saveCommunity(ctx: Context, json: String) { try { File(ctx.filesDir, "community.json").writeText(json) } catch (_: Exception) {} }
    fun loadCommunity(ctx: Context): String? = try { File(ctx.filesDir, "community.json").takeIf { it.exists() }?.readText() } catch (_: Exception) { null }

    fun loadUser(ctx: Context): Pair<List<Station>, List<Releve>> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return Pair(emptyList(), emptyList())
        return try {
            val o = JSONObject(f.readText())
            Pair(parseStations(o.getJSONArray("stations")), parseReleves(o.getJSONArray("releves"), false))
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    fun saveUser(ctx: Context, stations: List<Station>, releves: List<Releve>) {
        File(ctx.filesDir, FILE).writeText(userJson(stations, releves))
    }

    fun loadCar(ctx: Context): String =
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("car", DEFAULT_CAR)?.let { canonicalCar(it) } ?: DEFAULT_CAR

    fun saveCar(ctx: Context, car: String) {
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("car", canonicalCar(car) ?: DEFAULT_CAR).apply()
    }

    /** Fusionne un export reçu (même format que « Exporter ») ; lève une exception si le texte n'est pas valide. */
    fun importUser(text: String, stations: List<Station>, releves: List<Releve>): Pair<List<Station>, List<Releve>> {
        val o = JSONObject(text.trim())
        val ns = parseStations(o.optJSONArray("stations") ?: JSONArray())
        val nr = parseReleves(o.getJSONArray("releves"), false).filter { isKnownCar(it.car) }.map { it.copy(car = canonicalCar(it.car)!!) }
        val sIds = stations.map { it.id }.toSet()
        val rIds = releves.map { it.id }.toSet()
        return Pair(stations + ns.filter { it.id !in sIds }, releves + nr.filter { it.id !in rIds })
    }

    fun userJson(stations: List<Station>, releves: List<Releve>): String {
        val o = JSONObject()
        o.put("stations", JSONArray().also { a -> stations.forEach { a.put(stationJson(it)) } })
        o.put("releves", JSONArray().also { a -> releves.forEach { a.put(releveJson(it)) } })
        return o.toString(1)
    }

    private fun stationJson(s: Station) = JSONObject()
        .put("id", s.id).put("name", s.name).put("operator", s.operator)
        .put("lat", s.lat).put("lon", s.lon).put("current", s.current)
        .put("note", s.note).put("kind", s.kind).also { if (s.params.isNotBlank()) it.put("params", s.params) }

    private fun releveJson(r: Releve) = JSONObject()
        .put("id", r.id).put("stationId", r.stationId).put("ts", r.ts)
        .put("result", r.result.name).put("note", r.note).put("car", r.car)
        .also { if (r.otherCar != null) it.put("otherCar", r.otherCar) }

    private fun parseStations(a: JSONArray): List<Station> = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        Station(
            o.getString("id"), o.getString("name"), o.optString("operator", ""),
            o.getDouble("lat"), o.getDouble("lon"), o.optString("current", "?"),
            o.optString("note", ""), o.optString("kind", "user"), o.optString("params", "")
        )
    }

    private fun parseReleves(a: JSONArray, seed: Boolean): List<Releve> = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        Releve(
            o.getString("id"), o.getString("stationId"), o.optString("ts", ""),
            Result.valueOf(o.getString("result")),
            if (o.has("otherCar") && !o.isNull("otherCar")) o.getBoolean("otherCar") else null,
            o.optString("note", ""), seed, o.optString("car", "")
        )
    }
}
