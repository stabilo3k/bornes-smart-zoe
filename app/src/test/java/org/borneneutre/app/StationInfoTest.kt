package org.borneneutre.app

import org.junit.Assert.*
import org.junit.Test

class StationInfoTest {
    private fun st(params: String) = Station("x", "x", "", 50.0, 4.0, "AC", "", "osm", params)

    @Test fun power_parses_units() {
        assertEquals(22.0, StationInfo.powerKw(st("""{"socket:type2:output":"22 kW"}"""))!!, 0.01)
        assertEquals(11.0, StationInfo.powerKw(st("""{"maxpower":"11000"}"""))!!, 0.01)
        assertEquals(22.0, StationInfo.powerKw(st("""{"charging_station:output":"11;22"}"""))!!, 0.01)
        assertNull(StationInfo.powerKw(st("""{"amenity":"charging_station"}""")))
        assertNull(StationInfo.powerKw(st("")))
    }

    @Test fun access_labels() {
        assertEquals("Réservée aux clients", StationInfo.accessLabel(st("""{"access":"customers"}""")))
        assertNull(StationInfo.accessLabel(st("""{"access":"yes"}""")))
    }

    @Test fun history_for_car() {
        val r = listOf(
            Releve("1", "x", "2026-11-01", Result.OK, null, "", false, "Smart EQ fortwo"),
            Releve("2", "x", "2026-11-10", Result.REFUS, null, "", false, "Smart EQ fortwo"),
            Releve("3", "x", "2026-11-12", Result.OK, null, "", false, "Renault Zoé"))
        val h = StationInfo.history(r, "Smart EQ fortwo")
        assertEquals("2026-11-01", h.lastOk); assertEquals(1, h.ok); assertEquals(2, h.n)
    }
}

class EtatsUrgenceTest {
    private val now = 1_800_000_000_000L
    private fun e(id: String, k: EtatKind, d: Boolean, ageMin: Long, st: String = "s1") = Etat(id, st, k, d, "", "", now - ageMin * 60_000)

    @Test fun expiry() {
        val l = listOf(e("1", EtatKind.OCCUPEE, false, 200), e("2", EtatKind.OCCUPEE, false, 100), e("3", EtatKind.PANNE, false, 60 * 24 * 2), e("4", EtatKind.PANNE, false, 60 * 24 * 4))
        assertEquals(setOf("2", "3"), Etats.active(l, now).map { it.id }.toSet())
    }

    @Test fun lines_latest_occupancy_wins() {
        val l = listOf(e("1", EtatKind.OCCUPEE, false, 120), e("2", EtatKind.LIBRE, false, 10), e("3", EtatKind.OCCUPEE, false, 100))
        val lines = Etats.lines(l)
        assertEquals(1, lines.size); assertEquals(EtatKind.LIBRE, lines[0].kind)
    }

    @Test fun ok_releve_clears_panne_and_declared() {
        val ts = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val ok = Releve("r", "s1", ts, Result.OK, null, "", false, "Smart EQ fortwo")
        val l = listOf(e("1", EtatKind.PANNE, false, 30), e("2", EtatKind.MARCHE, true, 30), e("3", EtatKind.OCCUPEE, false, 30))
        assertEquals(listOf("3"), Etats.shown(l, "s1", listOf(ok)).map { it.id })
        assertEquals(3, Etats.shown(l, "s1", emptyList()).size)
    }

    @Test fun ago_and_text() {
        assertEquals("il y a 5 min", Etats.ago(now - 5 * 60_000, now))
        assertEquals("il y a 3 h", Etats.ago(now - 3 * 3_600_000L, now))
        assertTrue(Etats.lineText(EtatLine(EtatKind.MARCHE, true, 2, now), now).contains("non vérifié"))
    }

    @Test fun json_roundtrip_and_kw() {
        val l = listOf(e("1", EtatKind.FILE, false, 5))
        assertEquals(l, Etats.fromJson(Etats.toJson(l)))
        assertEquals(18.9, Etats.parseKw("18,9")!!, 0.001); assertNull(Etats.parseKw("0")); assertNull(Etats.parseKw("abc"))
    }

    @Test fun etat_rows_skip_private_and_sent() {
        val st = mapOf("s1" to Station("s1", "A", "", 50.9, 4.3, "AC", "", "osm"), "p" to Station("p", "P", "", 50.9, 4.3, "AC", "", "privee"))
        val l = listOf(e("1", EtatKind.PANNE, false, 1), e("2", EtatKind.PANNE, false, 1, "p"), e("3", EtatKind.MARCHE, true, 1))
        val rows = Sync.etatRows(l, st, "device-123", setOf("3"))
        assertEquals(1, rows.length()); assertEquals("test", rows.getJSONObject(0).getString("level"))
    }

    @Test fun urgence_ranks_verified_first_and_skips_refus_and_broken() {
        val car = "Smart EQ fortwo"
        fun s(id: String, dLat: Double) = Station(id, id, "", 50.9 + dLat, 4.3, "AC", "", "osm")
        val st = listOf(s("a", 0.001), s("b", 0.002), s("c", 0.003), s("d", 0.004), s("e", 0.0005))
        fun r(id: String, res: Result) = Releve("r$id", id, "2026-11-01", res, null, "", false, car)
        val out = Urgence.nearest(50.9, 4.3, st, listOf(r("a", Result.OK), r("b", Result.REFUS), r("c", Result.OK)), car, setOf("d"))
        assertEquals(listOf("a", "c", "e"), out.map { it.station.id })
        assertTrue(out[0].tried); assertFalse(out[2].tried)
    }
}

class FichesTest {
    private fun f(id: String, ts: Long, p: Int?, q: Int?, kw: Double?) = Fiche(id, "s1", "Lidl", 50.9, 4.3, p, q, kw, ts)

    @Test fun merge_takes_newest_value_per_field() {
        val m = Fiches.merge(listOf(f("a", 1, 2, 1, null), f("b", 5, null, 2, 11.0)))["s1"]!!
        assertEquals(2, m.points); assertEquals(2, m.plugs); assertEquals(11.0, m.kw!!, 0.01)
    }

    @Test fun describe_and_parse() {
        assertEquals("2 bornes · 1 prise par borne", Fiches.describe(2, 1))
        assertNull(Fiches.describe(null, null))
        assertNull(Fiches.parseCount("0", 8)); assertEquals(3, Fiches.parseCount(" 3 ", 8))
    }

    @Test fun extra_stations_only_for_unknown_ids() {
        val l = listOf(f("a", 1, 2, 1, null))
        assertEquals(1, Fiches.extraStations(l, emptySet()).size)
        assertEquals(0, Fiches.extraStations(l, setOf("s1")).size)
    }

    @Test fun power_label_priority() {
        val s = Station("x", "x", "", 50.9, 4.3, "AC", "", "osm", """{"maxpower":"22000"}""")
        assertTrue(StationInfo.powerLabel(s, 11.0, 7.0).contains("22 kW (nominal)"))
        val n = Station("y", "y", "", 50.9, 4.3, "AC", "", "osm", "")
        assertTrue(StationInfo.powerLabel(n, 11.0, 7.0).contains("(déclaré)"))
        assertTrue(StationInfo.powerLabel(n, null, 7.0).contains("(relevé"))
        assertTrue(StationInfo.powerLabel(n).contains("non renseignée"))
    }

    @Test fun urgence_unknown_ranked_by_score() {
        val car = "Smart EQ fortwo"
        fun s(id: String, dLat: Double) = Station(id, id, "", 50.9 + dLat, 4.3, "AC", "", "osm")
        val out = Urgence.nearest(50.9, 4.3, listOf(s("near", 0.0005), s("good", 0.004)), emptyList(), car, emptySet(), score = { if (it.id == "good") 0.9 else 0.2 })
        assertEquals(listOf("good", "near"), out.map { it.station.id })
    }

    @org.junit.Test fun phoneParsing() {
        org.junit.Assert.assertEquals("0800 58 143", Fiches.parsePhone(" 0800 58 143 "))
        org.junit.Assert.assertNull(Fiches.parsePhone("abc"))
        org.junit.Assert.assertNull(Fiches.parsePhone("12"))
    }
}

class FlandersTest {
    @org.junit.Test fun groupsConnectors() {
        val j = """{"features":[
          {"properties":{"uniek_identificatienummer":"A*1","uitbater":"50five","toegankelijkheid":"publiek","maximaal_vermogen_kw":22,"stroomtype":"ac_3_phase","adres":"Industrielaan 18","latitude":50.908912,"longitude":4.04283,"evse":1}},
          {"properties":{"uniek_identificatienummer":"A*2","uitbater":"50five","toegankelijkheid":"publiek","maximaal_vermogen_kw":22,"stroomtype":"ac_3_phase","adres":"Industrielaan 18","latitude":50.908912,"longitude":4.04283,"evse":2}}]}"""
        val l = Flanders.parse(j)
        org.junit.Assert.assertEquals(1, l.size)
        org.junit.Assert.assertEquals(22.0, StationInfo.powerKw(l[0])!!, 0.01)
        org.junit.Assert.assertEquals(2, StationInfo.osmPoints(l[0]))
        org.junit.Assert.assertEquals("AC", l[0].current)
        org.junit.Assert.assertEquals("Industrielaan 18", StationInfo.address(l[0]))
        org.junit.Assert.assertEquals("Open Data Vlaanderen / Eco-Movement", StationInfo.sourceLabel(l[0]))
        org.junit.Assert.assertEquals(1, Flanders.fromJson(Flanders.toJson(l)).size)
    }
}

class BrusselsTest {
    @org.junit.Test fun parsesCompactList() {
        val j = """{"stations":[{"i":"abc","o":"EnergyVision","la":50.8334,"lo":4.303,"p":2,"c":"AC_1_PHASE;AC_3_PHASE","t":"UNKNOWN"},{"i":"d","o":"X","la":50.9,"lo":4.4,"p":1,"c":"DC","t":"ON_STREET"}]}"""
        val l = Brussels.parse(j)
        org.junit.Assert.assertEquals(2, l.size)
        org.junit.Assert.assertEquals("bx-abc", l[0].id)
        org.junit.Assert.assertEquals("AC", l[0].current)
        org.junit.Assert.assertEquals("DC", l[1].current)
        org.junit.Assert.assertEquals("monophasé et triphasé", StationInfo.phases(l[0]))
        org.junit.Assert.assertEquals(2, StationInfo.osmPoints(l[0]))
        org.junit.Assert.assertEquals(Brussels.SOURCE, StationInfo.sourceLabel(l[0]))
    }
}
