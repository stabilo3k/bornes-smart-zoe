package org.borneneutre.app

import org.junit.Assert.*
import org.junit.Test

class ExportImportTest {
    private val s1 = Station("user-abc", "Borne test", "", 50.85, 4.35, "AC", "", "user")
    private val r1 = Releve("u111", "user-abc", "2026-11-01", Result.OK, "", false, DEFAULT_CAR, kw = 11.0, demarrage = "carte")
    private val r2 = Releve("u222", "user-abc", "2026-11-05", Result.REFUS, "", false, DEFAULT_CAR, motif = "voyant")

    @Test fun exportPuisImportSurTelephoneVideRetrouveTout() {
        val json = Store.userJson(listOf(s1), listOf(r1, r2))
        val (us, ur) = Store.importUser(json, emptyList(), emptyList())
        assertEquals(listOf("user-abc"), us.map { it.id })
        assertEquals(setOf("u111", "u222"), ur.map { it.id }.toSet())
        val a = ur.first { it.id == "u111" }
        assertEquals(11.0, a.kw!!, 0.0); assertEquals("carte", a.demarrage); assertEquals(Result.OK, a.result)
        assertEquals("voyant", ur.first { it.id == "u222" }.motif)
        assertTrue(ur.none { it.seed })
    }

    @Test fun reimporterNeCreePasDeDoublon() {
        val json = Store.userJson(listOf(s1), listOf(r1, r2))
        val (us, ur) = Store.importUser(json, listOf(s1), listOf(r1))
        assertEquals(1, us.size); assertEquals(2, ur.size)
        val (us2, ur2) = Store.importUser(json, us, ur)
        assertEquals(1, us2.size); assertEquals(2, ur2.size)
    }

    @Test fun fichierInvalideLeveUneException() {
        assertTrue(runCatching { Store.importUser("pas du json", emptyList(), emptyList()) }.isFailure)
        assertTrue(runCatching { Store.importUser("{\"x\":1}", emptyList(), emptyList()) }.isFailure)
    }

    @Test fun exportNeContientAucuneDonneePersonnelle() {
        val json = Store.userJson(listOf(s1), listOf(r1)).lowercase()
        assertFalse(json.contains("device")); assertFalse(json.contains("phone")); assertFalse(json.contains("plaque"))
    }
}
