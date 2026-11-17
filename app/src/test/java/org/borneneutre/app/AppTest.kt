package org.borneneutre.app

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppTest {
    private val sample = """{"elements":[
      {"type":"node","id":1,"lat":50.8967,"lon":4.3211,"tags":{"amenity":"charging_station","operator":"DATS 24","socket:type2":"2"}},
      {"type":"way","id":2,"center":{"lat":50.9,"lon":4.32},"tags":{"amenity":"charging_station","name":"Rapide","socket:type2_combo":"2"}},
      {"type":"node","id":3,"tags":{"amenity":"charging_station"}}
    ]}"""

    private fun st(id: String, lat: Double = 50.0, lon: Double = 4.0, cur: String = "AC") = Station(id, id, "", lat, lon, cur, "", "x")
    private fun r(sid: String, res: Result, ts: String = "2026-11-01") = Releve("r$sid$res$ts", sid, ts, res, null, "", false, DEFAULT_CAR)

    @Test fun overpassParsesNodesAndCenters() {
        val l = Overpass.parse(sample)
        assertEquals(2, l.size)
        assertEquals("osm-node-1", l[0].id); assertEquals("DATS 24", l[0].name); assertEquals("AC", l[0].current)
        assertEquals("osm-way-2", l[1].id); assertEquals("DC", l[1].current); assertEquals(50.9, l[1].lat, 1e-9)
    }

    @Test fun overpassQueryTargetsChargingStations() {
        assertTrue(Overpass.query(50.1, 4.2, 80).contains("around:80,50.1,4.2"))
    }

    @Test fun candidatesSortedFilteredAndDeduplicated() {
        val known = listOf(st("a", 50.00000, 4.0), st("b", 50.0010, 4.0))
        val osmDup = st("osm-1", 50.00001, 4.0)
        val osmNear = st("osm-2", 50.0003, 4.0)
        val osmDc = st("osm-3", 50.00005, 4.0, "DC")
        val c = Overpass.candidates(50.0, 4.0, known, listOf(osmDup, osmNear, osmDc), 60.0)
        assertEquals(listOf("a", "osm-2"), c.map { it.first.id })
        assertTrue(c[0].second < c[1].second)
    }

    @Test fun closedCarList() {
        assertEquals("Renault Zoé", canonicalCar("renault zoe"))
        assertEquals(DEFAULT_CAR, canonicalCar(""))
        assertNull(canonicalCar("Tesla Model 3"))
        assertNull(canonicalCar("Twingo"))
        assertNull(canonicalCar("Non précisé"))
        assertEquals(7, CARS.size)
        assertEquals("Nissan Townstar EV", canonicalCar("nissan townstar ev"))
        assertNull(canonicalCar("Mercedes Citan"))
    }

    @Test fun simpleStatuses() {
        assertEquals("#6b7280", simpleStatus(emptyList()).color)
        assertEquals(COLOR_OK, simpleStatus(listOf(r("a", Result.OK))).color)
        assertEquals(COLOR_NO, simpleStatus(listOf(r("a", Result.REFUS))).color)
        assertEquals(COLOR_MIX, simpleStatus(listOf(r("a", Result.OK), r("a", Result.REFUS))).color)
    }

    @Test fun importKeepsOnlyKnownCars() {
        val json = """{"stations":[],"releves":[
          {"id":"x1","stationId":"s","ts":"2026-11-01","result":"OK","car":"Tesla Model 3"},
          {"id":"x2","stationId":"s","ts":"2026-11-01","result":"OK","car":"renault zoe"},
          {"id":"x3","stationId":"s","ts":"2026-11-01","result":"REFUS"}]}"""
        val (_, rel) = Store.importUser(json, emptyList(), emptyList())
        assertEquals(listOf("x2", "x3"), rel.map { it.id })
        assertEquals("Renault Zoé", rel[0].car); assertEquals(DEFAULT_CAR, rel[1].car)
    }

    @Test fun importMergesWithoutDuplicates() {
        val json = """{"stations":[],"releves":[{"id":"x2","stationId":"s","ts":"2026-11-01","result":"OK","car":"Renault Zoé"}]}"""
        val first = Store.importUser(json, emptyList(), emptyList())
        val second = Store.importUser(json, first.first, first.second)
        assertEquals(1, second.second.size)
    }

    @Test fun seedsAreConsistent() {
        val sj = JSONArray(File("src/main/assets/stations.json").readText())
        val ids = (0 until sj.length()).map { sj.getJSONObject(it).getString("id") }.toSet()
        val rj = JSONArray(File("src/main/assets/releves.json").readText())
        assertEquals(12, rj.length())
        for (i in 0 until rj.length()) {
            val o = rj.getJSONObject(i)
            assertTrue(o.getString("stationId"), o.getString("stationId") in ids)
            assertTrue(isKnownCar(o.optString("car", "")))
        }
    }

    @Test fun faqParsesAndHasUniqueIds() {
        val items = Faq.parse(File("src/main/assets/faq.json").readText())
        assertTrue(items.size >= 15)
        assertEquals(items.size, items.map { it.id }.toSet().size)
        items.forEach { assertTrue(it.id, it.q.isNotBlank() && it.a.isNotBlank()) }
    }

    @Test fun refusWeighsFullWithoutWitness() {
        val s = mapOf("a" to st("a"))
        val e = Estimation.evidenceFrom(listOf(Releve("1", "a", "2026-11-01", Result.REFUS, null, "", false, DEFAULT_CAR)), s)
        assertEquals(1.0, e[0].weight, 1e-9)
    }

    @Test fun estimateFollowsNeighbours() {
        val s = mapOf("a" to st("a", 50.8966, 4.3211))
        val ok = Estimation.evidenceFrom(listOf(Releve("1", "a", "2026-11-01", Result.OK, null, "", false, DEFAULT_CAR)), s)
        assertTrue(Estimation.estimate(50.8968, 4.3212, ok).p > 0.65)
    }

    @Test fun osmParamsKeptInternally() {
        val l = Overpass.parse(sample)
        assertTrue(l[0].params.contains("socket:type2"))
        val (us, _) = Store.importUser("""{"stations":[{"id":"osm-node-1","name":"n","lat":50.0,"lon":4.0,"params":"{\"a\":1}"}],"releves":[]}""", emptyList(), emptyList())
        assertEquals("{\"a\":1}", us[0].params)
    }

    @Test fun syncRowsHoldNoPersonalData() {
        val s = mapOf("a" to st("a", 50.9, 4.3))
        val rows = Sync.toRows(listOf(r("a", Result.OK), Releve("t", "a", "2026-11-01", Result.OK, null, "", false, "Tesla")), s, "device-123456", emptySet())
        assertEquals(1, rows.length())
        val o = rows.getJSONObject(0)
        assertEquals(setOf("client_id", "device", "station_id", "station_name", "lat", "lon", "current", "result", "car", "ts"), o.keys().asSequence().toSet())
        assertEquals(0, Sync.toRows(listOf(r("a", Result.OK)), s, "device-123456", setOf(r("a", Result.OK).id)).length())
    }

    @Test fun syncFromRowsSkipsKnownAndUnknownCars() {
        val json = """[{"client_id":"k","station_id":"s1","station_name":"B","lat":50.9,"lon":4.3,"current":"AC","result":"OK","car":"Renault Zoé","ts":"2026-11-02"},
          {"client_id":"mine","station_id":"s1","station_name":"B","lat":50.9,"lon":4.3,"current":"AC","result":"OK","car":"Renault Zoé","ts":"2026-11-02"},
          {"client_id":"x","station_id":"s2","station_name":"C","lat":50.9,"lon":4.3,"current":"AC","result":"OK","car":"Tesla","ts":"2026-11-02"}]"""
        val (stn, rel) = Sync.fromRows(json, setOf("mine"), emptySet())
        assertEquals(listOf("k"), rel.map { it.id }); assertEquals(listOf("s1"), stn.map { it.id })
    }

    @Test fun oneRefusalDoesNotTurnTenSuccessesOrange() {
        val l = (1..9).map { r("a", Result.OK, "2026-10-%02d".format(it)) } + r("a", Result.REFUS, "2026-11-01")
        assertEquals(COLOR_OK, simpleStatus(l).color)
        assertEquals(COLOR_MIX, simpleStatus(listOf(r("a", Result.OK), r("a", Result.REFUS))).color)
    }

    @Test fun uploadSkipsRejectedRowsAndStopsOnNetworkError() {
        val rows = JSONArray(); (1..12).forEach { rows.put(org.json.JSONObject().put("client_id", "c$it")) }
        // lot refusé (400) à cause d'une ligne fautive : les autres passent une à une
        val done = Sync.upload(rows) { body -> if (body.contains("\"c3\"") && !body.contains("\"c4\"")) 400 else if (body.contains("\"c3\"")) 400 else 201 }
        assertEquals(12, done.size)
        // panne réseau : on s'arrête
        val none = Sync.upload(rows) { -1 }
        assertEquals(0, none.size)
    }

    @Test fun privateStationsAreNeverSent() {
        val priv = Station("p", "Maison", "", 50.9, 4.3, "AC", "", "privee")
        assertEquals(0, Sync.toRows(listOf(r("p", Result.OK)), mapOf("p" to priv), "device-123456", emptySet()).length())
    }

    @Test fun uploadNeverMarksNonConstraintErrors() {
        val rows = JSONArray(); (1..3).forEach { rows.put(org.json.JSONObject().put("client_id", "z$it")) }
        // 404 (base absente), 429, 5xx, limite anti-abus (P0001) : rien n'est marqué envoyé
        assertEquals(-1, Sync.classify(404, "{\"code\":\"PGRST205\"}"))
        assertEquals(-1, Sync.classify(400, "{\"code\":\"P0001\",\"message\":\"limite quotidienne atteinte\"}"))
        assertEquals(-1, Sync.classify(429, null))
        assertEquals(Sync.REJECTED, Sync.classify(400, "{\"code\":\"23514\"}"))
        assertEquals(0, Sync.upload(rows) { Sync.classify(404, "{\"code\":\"PGRST205\"}") }.size)
    }

    @Test fun zoneLegendUsesPaleSquares() {
        assertEquals(2, LEGEND_ZONES.size)
        assertTrue(LEGEND_ZONES.all { it.square })
        assertEquals(setOf("#bfe0c0", "#eebdbd"), LEGEND_ZONES.map { it.color }.toSet())
    }

    @Test fun remoteFlagSentOnlyWhenTrue() {
        val st = Station("s1", "Lidl", "", 50.9, 4.3, "AC", "", "osm")
        val near = Releve("a1", "s1", "2026-11-16", Result.OK, null, "", false, DEFAULT_CAR)
        val far = near.copy(id = "a2", remote = true)
        val rows = Sync.toRows(listOf(near, far), mapOf("s1" to st), "dev", emptySet())
        assertFalse(rows.getJSONObject(0).has("remote"))
        assertTrue(rows.getJSONObject(1).getBoolean("remote"))
    }
}
