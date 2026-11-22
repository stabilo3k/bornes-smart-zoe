package org.borneneutre.app

import org.junit.Assert.*
import org.junit.Test

class QrBorneTest {
    @Test fun extraitIdentifiantDePointDeCharge() {
        assertEquals(listOf("BETCBE110360"), QrBorne.candidates("https://pay.example.be/start?evse=BE*TCB*E110360"))
    }
    @Test fun extraitReferenceConcession() {
        assertEquals(listOf("PP003220"), QrBorne.candidates("charge PP003220 ici"))
    }
    @Test fun rienDeReconnaissable() {
        assertTrue(QrBorne.candidates("bonjour tout le monde").isEmpty())
        assertTrue(QrBorne.key("bonjour tout le monde").startsWith("H")); assertEquals(QrBorne.key("bonjour tout le monde"), QrBorne.key("Bonjour, tout le monde !"))
    }
    @Test fun reconnaitUneReferenceDeLaConcession() {
        val r = QrBorne.find("https://x.be/PP003222", emptyMap())
        assertTrue(r is QrBorne.Found.Concession)
        assertEquals("PP003222", (r as QrBorne.Found.Concession).ref)
    }
    @Test fun codeAssocieSurLeTelephone() {
        val r = QrBorne.find("BE*TCB*E110360", mapOf("BETCBE110360" to "st42"))
        assertEquals("st42", (r as QrBorne.Found.Station).id)
    }
    @Test fun inconnu() { assertTrue(QrBorne.find("zzz", emptyMap()) is QrBorne.Found.Unknown) }
    @Test fun aller_retour_json() {
        val m = mapOf("A1" to "s1", "B2" to "s2")
        assertEquals(m, QrBorne.learnedFromJson(QrBorne.learnedToJson(m)))
    }
}
