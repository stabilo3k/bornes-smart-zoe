package org.borneneutre.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Release132Test {
    // 0,0001° de latitude ≈ 11,1 m
    private fun s(id: String, dLatM: Double = 0.0, op: String = "", params: String = "") = Station(id, id, op, 50.85 + dLatM / 111_000.0, 4.35, "AC", "", "osm", params)
    private fun rel(sid: String, res: Result, motif: String = "") = Releve("r$sid$res$motif", sid, "2026-11-01", res, "", false, DEFAULT_CAR, motif = motif)

    // ---- passe Fable n° 13 ----
    @Test fun fusion_masterOsmPerimeeFusionneAvecRemplacante() {
        val r = Fusion.build(listOf(s("osm-OLD", 0.0, "")), listOf(listOf(s("osm-NEW", 2.0, ""))))
        assertEquals(1, r.all.size); assertEquals("osm-OLD", r.alias["osm-NEW"])
    }
    @Test fun fusion_reporteExploitantEtCourant() {
        val k = s("m1", 0.0, "").copy(current = "?")
        val r = Fusion.build(listOf(k), listOf(listOf(s("bx-1", 3.0, "Allego").copy(current = "DC"))))
        assertEquals("Allego", r.all[0].operator); assertEquals("DC", r.all[0].current)
    }
    @Test fun fusion_aliasOnlyIgnoreLesDoublons() {
        val f = s("osm-9", 60.0, "Allego")
        assertEquals(mapOf("osm-9" to "m1"), Fusion.aliasOnly(listOf(s("m1", 0.0, "Allego")), listOf(f, f)))
    }
    @Test fun fusion_exploitantCourtNEstPasInclus() { assertFalse(Fusion.accepts(30.0, "Ion", "Ionity")) }
    @Test fun refusHorsVerdictNeCompteurPasCommeEssai() {
        assertFalse(Essai400.concluant(rel("a", Result.REFUS, "badge")))
        assertTrue(Essai400.concluant(rel("a", Result.REFUS, "")))
    }
    @Test fun plausibleApresMajApp() { assertTrue(DataUpdate.plausible(8000, 8100)) }

    @Test fun fusion_exploitantInconnuTexteFusionne() {
        // borne embarquée « inconnu » + OSM (0 m) + Bruxelles EnergyVision (17 m) = une seule borne, exploitant repris
        val r = Fusion.build(listOf(s("osm-1", 0.0, "inconnu")), listOf(listOf(s("osm-node-9", 0.0, "EnergyDrive")), emptyList(), listOf(s("bx-1", 17.0, "EnergyVision"))))
        assertEquals(1, r.all.size); assertEquals("EnergyDrive", r.all[0].operator)
    }
    @Test fun fusion_pitpointEstTotalEnergies() { assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "PitPoint")), listOf(s("bx-1", 18.0, "TotalEnergies"))).isEmpty()) }
    @Test fun fusion_marquesDuMemeGroupe() {
        assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "EnergyVision")), listOf(s("osm-1", 18.0, "brusol"))).isEmpty())
        assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "EnergyVision")), listOf(s("osm-1", 18.0, "EnergyDrive"))).isEmpty())
        assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "TotalEnergies")), listOf(s("osm-1", 18.0, "charge.brussels"))).isEmpty())
        assertEquals(1, Fusion.dedupe(listOf(s("m1", 0.0, "Allego")), listOf(s("osm-1", 18.0, "brusol"))).size)
        assertEquals(1, Fusion.dedupe(listOf(s("m1", 0.0, "TotalEnergies")), listOf(s("osm-1", 18.0, "EnergyDrive"))).size)
    }
    // ---- fusion graduée ----
    @Test fun fusion_10mToujours() { assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "A")), listOf(s("osm-1", 8.0, "B"))).isEmpty()) }
    @Test fun fusion_10a25ExploitantsDifferentsConnus() { assertEquals(1, Fusion.dedupe(listOf(s("m1", 0.0, "Allego")), listOf(s("osm-1", 18.0, "EnergyVision"))).size) }
    @Test fun fusion_10a25ExploitantInconnuFusionne() { assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "Allego")), listOf(s("osm-1", 18.0, ""))).isEmpty()) }
    @Test fun fusion_25a40MemeExploitantSeulement() {
        assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "EnergyVision")), listOf(s("osm-1", 33.0, "energyvision"))).isEmpty())
        assertEquals(1, Fusion.dedupe(listOf(s("m1", 0.0, "EnergyVision")), listOf(s("osm-1", 33.0, ""))).size)
    }
    @Test fun fusion_40a80ExigeUnicite() {
        assertTrue(Fusion.dedupe(listOf(s("m1", 0.0, "Allego")), listOf(s("osm-1", 60.0, "Allego"))).isEmpty())
        // deux bornes d'une même source du même exploitant dans la zone : pas de fusion à cette distance
        assertEquals(2, Fusion.dedupe(listOf(s("m1", 0.0, "Allego")), listOf(s("osm-1", 60.0, "Allego"), s("osm-2", 62.0, "Allego"))).size)
        assertEquals(1, Fusion.dedupe(listOf(s("m1", 0.0, "Allego")), listOf(s("osm-1", 95.0, "Allego"))).size)
    }
    @Test fun fusion_deuxBornesDUneMemeSourceRestentDeux() {
        val r = Fusion.build(emptyList(), listOf(listOf(s("osm-1", 0.0, "A"), s("osm-2", 5.0, "A"))))
        assertEquals(2, r.all.size); assertTrue(r.alias.isEmpty())
    }
    @Test fun fusion_pasDeChaine_aliasVersBorneConservee() {
        val r = Fusion.build(listOf(s("m1", 0.0, "A")), listOf(listOf(s("osm-1", 6.0, "A")), listOf(s("bx-1", 9.0, "A")), listOf(s("wal-1", 12.0, "A"))))
        assertEquals(1, r.all.size)
        assertEquals("m1", r.alias["osm-1"]); assertEquals("m1", r.alias["bx-1"]); assertEquals("m1", r.alias["wal-1"])
        assertTrue(r.alias.values.all { v -> r.all.any { it.id == v } })
    }
    @Test fun fusion_aliasQuiSuitLaFiche_etRelevesRedirigeables() {
        val r = Fusion.build(listOf(s("m1", 0.0, "")), listOf(listOf(s("bx-9", 4.0, ""))))
        assertEquals("m1", r.alias["bx-9"])
    }
    @Test fun fusion_phasesReporteesSurLaBorneConservee() {
        val bx = s("bx-1", 5.0, "EnergyVision", """{"phases":"triphasé"}""")
        val r = Fusion.build(listOf(s("osm-node-5", 0.0, "EnergyVision")), listOf(listOf(bx)))
        assertEquals(1, r.all.size)
        assertEquals("triphasé", StationInfo.phases(r.all[0]))
    }
    @Test fun fusion_memeIdentifiantNEstPasUnAlias() {
        val r = Fusion.build(listOf(s("osm-node-5", 0.0, "")), listOf(listOf(s("osm-node-5", 0.0, ""))))
        assertEquals(1, r.all.size); assertTrue(r.alias.isEmpty())
    }
    @Test fun fusion_aliasOnlyNAjouteRien() {
        val a = Fusion.aliasOnly(listOf(s("m1", 0.0, "A")), listOf(s("osm-7", 7.0, "A"), s("osm-8", 400.0, "A")))
        assertEquals(mapOf("osm-7" to "m1"), a)
    }

    // ---- motifs hors verdict ----
    @Test fun motifsHorsVerdict() {
        assertTrue(rel("a", Result.REFUS, "badge").horsVerdict()); assertTrue(rel("a", Result.REFUS, "eteinte").horsVerdict()); assertTrue(rel("a", Result.REFUS, "carte").horsVerdict())
        assertFalse(rel("a", Result.REFUS, "voyant").horsVerdict()); assertFalse(rel("a", Result.REFUS).horsVerdict()); assertFalse(rel("a", Result.OK, "badge").horsVerdict())
    }

    // ---- repère « Ajoutée » ----
    @Test fun ajoutee_quaranteCinqJours() {
        val t = LocalDate.of(2026, 12, 20)
        fun st(d: String) = s("osm-1", 0.0, "", """{"${StationInfo.ADDED_KEY}":"$d"}""")
        assertNotNull(StationInfo.recentlyAdded(st("2026-11-21"), t))      // 29 jours
        assertNotNull(StationInfo.recentlyAdded(st("2026-11-05"), t))      // 45 jours : encore visible
        assertNull(StationInfo.recentlyAdded(st("2026-11-04"), t))         // 46 jours : expiré
        assertNull(StationInfo.recentlyAdded(st("2027-01-01"), t))         // date future : ignorée
        assertNull(StationInfo.recentlyAdded(s("osm-2"), t))               // sans date : jamais « Ajoutée »
        assertTrue(StationInfo.addedLabel(st("2026-11-21"), t)!!.contains("21/11"))
    }
    @Test fun ajoutee_dateLueAuNiveauDeLElement() {
        val l = Overpass.parse("""{"elements":[{"type":"node","id":1,"lat":50.8,"lon":4.3,"d":"2026-11-21","tags":{"amenity":"charging_station"}}]}""")
        assertNotNull(StationInfo.recentlyAdded(l[0], LocalDate.of(2026, 11, 25)))
    }

    // ---- pastille bleue, 2 téléphones ----
    @Test fun pastilleBleueEtAutreAcceptees() { assertNotNull(pastilleLabel("bleue")); assertNotNull(pastilleLabel("autre")); assertNull(pastilleLabel("rouge")) }
    @Test fun pastillesOrdreDuDialogue() { assertEquals(listOf("verte", "blanche", "bleue", "autre", "aucune"), PASTILLES.map { it.first }) }
    @Test fun pastilleBleueJamaisVerdict() {
        val p = listOf(Pastille("p1", "s1", "bleue", false, 1_700_000_000_000))
        assertEquals("Pas encore essayée", simpleStatus(emptyList()).label.take(18))
        assertTrue(Pastilles.summary(p, "s1")!!.contains("bleu"))
    }
    @Test fun confirmeParDeuxTelephones() {
        val p = listOf(Pastille("p1", "s1", "verte", false, 1_700_000_000_000), Pastille("p2", "s1", "verte", false, 1_700_100_000_000))
        assertTrue(Pastilles.summary(p, "s1")!!.contains("vu 2 fois, non confirmé"))
        assertTrue(Pastilles.summary(p, "s1", mapOf("s1|verte" to 1))!!.contains("vu 1 fois, non confirmé"))
        assertTrue(Pastilles.summary(p, "s1", mapOf("s1|verte" to 2))!!.contains("confirmé par 2 téléphones"))
        assertEquals(mapOf("s1|verte" to 3), Pastilles.phonesFromRows("""[{"station_id":"s1","code":"verte","n_telephones":3},{"station_id":"s1","code":"inconnu","n_telephones":9}]"""))
    }

    // ---- score d'indice ----
    private fun conc(id: String = "osm-9") = Station(id, id, "TotalEnergies", 50.8901, 4.3110, "AC", "", "osm") // PP003220 (liste violette)
    @Test fun indice_essaiPrimeSurTout() {
        val i = Indice.of(conc(), listOf(rel("osm-9", Result.OK)), "blanche", listOf(Pastille("p", "osm-9", "blanche", false, 1_700_000_000_000)))
        assertEquals(Indice.ESSAYEE, i.niveau)
    }
    @Test fun indice_pastilleVerteAvantViolet() {
        val i = Indice.of(conc(), emptyList(), null, listOf(Pastille("p", "osm-9", "verte", false, 1_700_000_000_000)))
        assertEquals(Indice.FAVORABLE, i.niveau); assertTrue(i.texte.contains("selon des conducteurs")); assertFalse(i.texte.contains("P.F."))
    }
    @Test fun indice_violetEstFavorableMaisNonVerifie() {
        val i = Indice.of(conc(), emptyList(), null, emptyList())
        assertEquals(Indice.FAVORABLE, i.niveau); assertTrue(i.texte.contains("Non vérifié")); assertTrue(i.texte.contains("Probablement"))
    }
    @Test fun indice_monophaseDefavorable_inconnuSansIndice() {
        val mono = Station("bx-3", "x", "EnergyVision", 50.0, 4.0, "AC", "", "osm", """{"phases":"monophasé"}""")
        assertEquals(Indice.DEFAVORABLE, Indice.of(mono, emptyList(), null, emptyList()).niveau)
        assertEquals(Indice.AUCUN, Indice.of(Station("osm-1", "x", "", 50.0, 4.0, "AC", "", "osm"), emptyList(), null, emptyList()).niveau)
    }
    @Test fun indice_neDonneJamaisDePourcentage() {
        val all = listOf(Indice.of(conc(), emptyList(), null, emptyList()), Indice.of(Station("osm-1", "x", "", 50.0, 4.0, "AC", "", "osm"), emptyList(), null, emptyList()))
        assertTrue(all.none { it.texte.contains("%") })
    }

    // ---- Urgence ----
    @Test fun urgence_violettesAvantLesAutresParmiLesNonEssayees() {
        val near = Station("a", "a", "", 50.8500, 4.3500, "AC", "", "osm"); val far = Station("b", "b", "", 50.8505, 4.3500, "AC", "", "osm")
        val l = Urgence.nearest(50.85, 4.35, listOf(near, far), emptyList(), DEFAULT_CAR, emptySet(), violet = { it.id == "b" })
        assertEquals("b", l[0].station.id)
    }

    // ---- fiabilité des listes ----
    @Test fun garde_fouRelatif() {
        assertTrue(DataUpdate.plausible(1000, 905)); assertFalse(DataUpdate.plausible(1000, 890))
        assertTrue(DataUpdate.plausible(1000, 1250)); assertFalse(DataUpdate.plausible(1000, 1251)); assertTrue(DataUpdate.plausible(0, 5))
    }
    @Test fun empreinteDeclaree() {
        val meta = JSONObject("""{"files":{"a.json":{"sha256":"${DataUpdate.sha256("abc")}"}}}""")
        assertEquals(DataUpdate.sha256("abc"), DataUpdate.declaredSha(meta, "a.json")); assertNull(DataUpdate.declaredSha(meta, "b.json"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DataUpdate.sha256("abc"))
    }

    // ---- données embarquées ----
    @Test fun aucunTextePublicNomme() {
        val faq = java.io.File("src/main/assets/faq.json").readText()
        assertFalse(faq.contains("(P.F.)"))
    }
}

class PastilleMarkersTest {
    private val now = 1_800_000_000_000L; private val day = 24L * 3600 * 1000
    private fun p(sid: String, c: String, ago: Long) = Pastille("$sid$c$ago", sid, c, false, now - ago * day)
    @org.junit.Test fun uneFoisEtDeuxTelephones() {
        val l = listOf(p("a", "verte", 1), p("b", "bleue", 2))
        assertEquals(mapOf("a" to 1, "b" to 1), Pastilles.markers(l, emptyMap(), now))
        assertEquals(2, Pastilles.markers(l, mapOf("a|verte" to 2), now)["a"])
    }
    @org.junit.Test fun contradictionBorneeA90Jours() {
        assertNull(Pastilles.markers(listOf(p("a", "verte", 1), p("a", "blanche", 30)), emptyMap(), now)["a"])
        assertEquals(1, Pastilles.markers(listOf(p("a", "verte", 1), p("a", "blanche", 120)), emptyMap(), now)["a"])
    }
    @org.junit.Test fun blancheOuAucuneSansMarqueur() {
        assertTrue(Pastilles.markers(listOf(p("a", "blanche", 1), p("b", "aucune", 1), p("c", "autre", 1)), emptyMap(), now).isEmpty())
    }
}

class UrgenceHeuristiqueTest {
    private fun s(id: String, dLat: Double) = Station(id, id, "", 50.9 + dLat, 4.3, "AC", "", "osm")
    private fun ind(f: Boolean?) = IndiceInfo(if (f == true) Indice.FAVORABLE else if (f == false) Indice.DEFAVORABLE else Indice.AUCUN, "x", f)

    @Test fun heuristique_favorablePasseAvantDansLeRayon_defavorableApres_sansExclure() {
        val a = s("proche-defav", 0.0005); val b = s("un-peu-plus-loin-fav", 0.0015); val c = s("neutre", 0.0010)
        val m = mapOf("proche-defav" to ind(false), "un-peu-plus-loin-fav" to ind(true), "neutre" to ind(null))
        val out = Urgence.nearest(50.9, 4.3, listOf(a, b, c), emptyList(), DEFAULT_CAR, emptySet(), nUnknown = 3, indiceOf = { m[it.id]!! })
        assertEquals(listOf("un-peu-plus-loin-fav", "neutre", "proche-defav"), out.map { it.station.id })
    }

    @Test fun heuristique_laBorneLointaineNePasseJamaisDevantLaProche() {
        val near = s("near-defav", 0.0005); val far = s("far-fav", 0.05)
        val out = Urgence.nearest(50.9, 4.3, listOf(near, far), emptyList(), DEFAULT_CAR, emptySet(), nUnknown = 2, indiceOf = { if (it.id == "far-fav") ind(true) else ind(false) })
        assertEquals("near-defav", out.first().station.id)
    }

    @Test fun heuristique_panneToujoursEnDernier_etSansHeuristiqueRienNeChange() {
        val a = s("a", 0.0005); val b = s("b", 0.0010)
        val out = Urgence.nearest(50.9, 4.3, listOf(a, b), emptyList(), DEFAULT_CAR, setOf("a"), nUnknown = 2, indiceOf = { ind(true) })
        assertEquals("b", out.first().station.id)
        val sans = Urgence.nearest(50.9, 4.3, listOf(a, b), emptyList(), DEFAULT_CAR, emptySet(), nUnknown = 2)
        assertEquals("a", sans.first().station.id)
    }
}
