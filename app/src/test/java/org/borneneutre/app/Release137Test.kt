package org.borneneutre.app

import org.junit.Assert.*
import org.junit.Test

/** Corrections de la revue du 22/11/2026 (import invalide, QR trop large, indice de voisinage, classify 22xxx). */
class Release137Test {
    private val car = DEFAULT_CAR
    private fun exportWith(stationLat: String, ts: String, id: String = "r1") =
        """{"stations":[{"id":"s1","name":"A","operator":"","lat":$stationLat,"lon":4.35,"current":"AC","note":"","kind":"user"}],
           "releves":[{"id":"$id","stationId":"s1","ts":"$ts","result":"OK","note":"","car":"$car"}]}"""

    @Test fun importRejetteCoordonneesNonFinies() {
        val (s, r) = Store.importUser(exportWith("\"NaN\"", "2026-11-20"), emptyList(), emptyList())
        assertTrue(s.isEmpty()); assertEquals(1, r.size)
    }
    @Test fun importRejetteHorsBelgique() {
        val (s, _) = Store.importUser(exportWith("10.0", "2026-11-20"), emptyList(), emptyList())
        assertTrue(s.isEmpty())
        val (s2, _) = Store.importUser(exportWith("50.85", "2026-11-20"), emptyList(), emptyList())
        assertEquals(1, s2.size)
    }
    @Test fun importRejetteDateIllisible() {
        assertTrue(Store.importUser(exportWith("50.85", "hier"), emptyList(), emptyList()).second.isEmpty())
        assertTrue(Store.importUser(exportWith("50.85", ""), emptyList(), emptyList()).second.isEmpty())
        assertEquals(1, Store.importUser(exportWith("50.85", "2026-11-20"), emptyList(), emptyList()).second.size)
    }
    @Test fun importDedoublonneDansLeFichier() {
        val t = """{"releves":[{"id":"x","stationId":"s","ts":"2026-11-20","result":"OK","car":"$car"},{"id":"x","stationId":"s","ts":"2026-11-20","result":"OK","car":"$car"}]}"""
        assertEquals(1, Store.importUser(t, emptyList(), emptyList()).second.size)
    }
    @Test fun importIdTropLong() {
        assertTrue(Store.importUser(exportWith("50.85", "2026-11-20", "x".repeat(81)), emptyList(), emptyList()).second.isEmpty())
    }
    @Test fun qrMotsOrdinairesNePassentPlus() {
        assertTrue(QrBorne.candidates("https://www.chargepoint.com/qr/start").isEmpty())
        assertTrue(QrBorne.candidates("devenez_member Borne electrique").isEmpty())
        assertEquals(listOf("BECPIE1234567"), QrBorne.candidates("https://www.chargepoint.com/qr/BE*CPI*E1234567"))
    }
    @Test fun qrCleDeRepliDistinguePlusDe80Caracteres() {
        val a = "https://exemple.be/" + "a".repeat(90) + "1"; val b = "https://exemple.be/" + "a".repeat(90) + "2"
        assertNotEquals(QrBorne.key(a), QrBorne.key(b))
    }
    @Test fun classifyTraite22CommeRefuse() {
        assertEquals(Sync.REJECTED, Sync.classify(400, "{\"code\":\"22007\",\"message\":\"invalid\"}"))
        assertEquals(Sync.REJECTED, Sync.classify(400, "{\"code\":\"23514\"}"))
        assertEquals(-1, Sync.classify(500, "{\"code\":\"XX000\"}"))
    }
    @Test fun voisinsIndiceMemeSite() {
        val a = Station("a", "A", "", 50.8500, 4.3500, "AC", "", "user", "")
        val b = Station("b", "B", "", 50.8501, 4.3500, "AC", "", "user", "")
        val far = Station("c", "C", "", 50.9, 4.4, "AC", "", "user", "")
        val rel = listOf(Releve("r", "b", "2026-11-20", Result.REFUS, "", false, car, true, null, "", ""))
        assertNotNull(Voisins.hint(a, listOf(a, b, far), rel, car))
        assertTrue(Voisins.hint(a, listOf(a, b, far), rel, car)!!.contains("ne charge pas"))
        assertNull(Voisins.hint(far, listOf(a, b, far), rel, car))
        assertNull(Voisins.hint(b, listOf(a, b, far), rel, car)) // elle a déjà son propre essai
    }
}
