package org.borneneutre.app

import org.junit.Assert.*
import org.junit.Test

class PanneReparaTest {
    private val t0 = 1_800_000_000_000L
    private fun e(id: String, k: EtatKind, ts: Long, st: String = "b1") = Etat(id, st, k, false, "", "sur place", ts)

    @Test fun repareEffaceLaPanneAnterieure() {
        val all = listOf(e("1", EtatKind.PANNE, t0), e("2", EtatKind.REPARE, t0 + 60_000))
        val shown = Etats.shown(Etats.active(all, t0 + 120_000), "b1", emptyList())
        assertTrue(shown.isEmpty())
    }

    @Test fun nouvellePanneApresRepareReapparait() {
        val all = listOf(e("1", EtatKind.PANNE, t0), e("2", EtatKind.REPARE, t0 + 60_000), e("3", EtatKind.PANNE, t0 + 120_000))
        val shown = Etats.shown(Etats.active(all, t0 + 180_000), "b1", emptyList())
        assertEquals(listOf("3"), shown.map { it.id })
    }

    @Test fun repareNAffectePasUneAutreBorne() {
        val all = listOf(e("1", EtatKind.PANNE, t0, "b2"), e("2", EtatKind.REPARE, t0 + 60_000, "b1"))
        val shown = Etats.shown(Etats.active(all, t0 + 120_000), "b2", emptyList())
        assertEquals(listOf("1"), shown.map { it.id })
    }

    @Test fun codeRepareLuParLeServeur() {
        assertEquals(EtatKind.REPARE, EtatKind.of("repare"))
    }
}
