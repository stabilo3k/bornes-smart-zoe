package org.borneneutre.app

import org.junit.Assert.*
import org.junit.Test

class DiagLogTest {
    @Test fun copieLimiteeEtRecentesDabord() {
        val lines = (1..1000).map { "22/11 12:00:%02d ligne de test numéro $it".format(it % 60) } // la 1re = la plus récente
        val out = DiagLog.limitCopy("EN-TETE", lines, 2000)
        assertTrue(out.length <= 2000)
        assertTrue(out.startsWith("EN-TETE\n22/11 12:00:01 ligne de test numéro 1"))
        assertTrue(out.contains("ligne(s) plus ancienne(s) non copiée(s)"))
    }
    @Test fun petitJournalCopieEnEntier() {
        val out = DiagLog.limitCopy("H", listOf("a", "b"), 15000)
        assertEquals("H\na\nb", out)
        assertEquals("H\n(journal vide)", DiagLog.limitCopy("H", emptyList(), 15000))
    }
}
