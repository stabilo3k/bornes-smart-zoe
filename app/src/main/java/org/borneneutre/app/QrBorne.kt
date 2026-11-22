package org.borneneutre.app

/** Lecture du QR d'une borne : on ne garde qu'un identifiant (jamais l'image). Tout se passe sur le téléphone. */
object QrBorne {
    /** Normalisation : majuscules, lettres et chiffres seulement (BE*TCB*E110360 → BETCBE110360). */
    fun norm(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    // Identifiant EVSE (ex. BE*TCB*E110360) : MAJUSCULES, séparateur « * » ou « - » seulement, au moins un chiffre (évite « chargepoint », « Borne electrique »).
    private val EVSE = Regex("""\b[A-Z]{2}[*\-][A-Z0-9]{3}[*\-]E[A-Z0-9*\-]{3,}""")
    private val EVSE_COMPACT = Regex("""\b[A-Z]{2}[A-Z0-9]{3}E\d{4,}\b""")
    private val PP = Regex("""\bPP\d{6}\b""", RegexOption.IGNORE_CASE)

    /** Identifiants probables trouvés dans le texte d'un QR (identifiant de point de charge, référence de la concession), normalisés, sans doublon. */
    fun candidates(text: String): List<String> {
        val text = text.replace("%2A", "*", ignoreCase = true).replace("%2a", "*") // QR dont l'astérisque est encodé dans l'URL
        val out = LinkedHashSet<String>()
        PP.findAll(text).forEach { out.add(norm(it.value)) }
        (EVSE.findAll(text) + EVSE_COMPACT.findAll(text)).map { norm(it.value) }.filter { c -> c.any { ch -> ch.isDigit() } }
            .sortedByDescending { it.length }.forEach { out.add(it) }
        return out.toList()
    }

    /** Clé à retenir pour reconnaître ce QR plus tard : le premier identifiant, sinon une empreinte du texte entier (pas de collision entre deux URL longues). */
    fun key(text: String): String = candidates(text).firstOrNull() ?: ("H" + sha(norm(text)))

    private fun sha(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(12).joinToString("") { "%02X".format(it) }

    sealed class Found {
        data class Station(val id: String, val via: String) : Found()
        data class Concession(val ref: String, val adresse: String, val lat: Double, val lon: Double) : Found()
        object Unknown : Found()
    }

    /** Reconnaît le QR : d'abord les codes déjà associés sur ce téléphone, puis les références de la concession (PP00xxxx). */
    fun find(text: String, learned: Map<String, String>): Found {
        val k = key(text)
        learned[k]?.let { return Found.Station(it, "association faite sur ce téléphone") }
        learned[norm(text).take(80)]?.let { return Found.Station(it, "association faite sur ce téléphone") } // ancienne clé (1.3.6)
        val n = norm(text)
        Essai400.BORNES.firstOrNull { n.contains(norm(it.ref)) }?.let { return Found.Concession(it.ref, it.adresse, it.lat, it.lon) }
        return Found.Unknown
    }

    fun learnedToJson(m: Map<String, String>): String = org.json.JSONObject(m as Map<*, *>).toString()
    fun learnedFromJson(s: String?): Map<String, String> = runCatching {
        val o = org.json.JSONObject(s ?: "{}"); o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())
}
