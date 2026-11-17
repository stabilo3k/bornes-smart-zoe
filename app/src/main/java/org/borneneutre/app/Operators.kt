package org.borneneutre.app

/** Pages de contact d'exploitants, vérifiées sur leurs sites (17/11/2026). Aucun numéro de téléphone n'est supposé : il figure sur la borne. */
object Operators {
    data class Contact(val name: String, val url: String)
    private val known = listOf(
        listOf("allego") to Contact("Allego", "https://www.allego.eu/fr/contacter/"),
        listOf("total") to Contact("TotalEnergies EV Charge", "https://evcharge.totalenergies.com/en/contact-us")
    )

    fun forOperator(operator: String): Contact? {
        val o = operator.lowercase()
        return known.firstOrNull { (keys, _) -> keys.any { o.contains(it) } }?.second
    }
}
