package org.borneneutre.app

/** « Urgence batterie » : les bornes les plus proches, classées, calculées hors ligne. Aucune garantie : un relevé passé ne promet rien. */
data class UrgenceItem(val station: Station, val distM: Double, val status: SimpleStatus, val tried: Boolean, val score: Double = 0.0, val broken: Boolean = false, val dc: Boolean = false, val violet: Boolean = false, val indice: IndiceInfo? = null)

object Urgence {
    /**
     * Jusqu'à [nVerified] bornes où la voiture [car] a déjà chargé (les plus proches d'abord, dans [maxKm]),
     * puis jusqu'à [nUnknown] bornes proches pas encore essayées. Exclues : bornes où la voiture refuse, bornes privées. Une panne signalée n'exclut plus la borne (un signalement isolé peut être faux ou périmé) : elle est marquée et classée après les autres.
     */
    fun nearest(lat: Double, lon: Double, stations: List<Station>, releves: List<Releve>, car: String, brokenIds: Set<String>,
                nVerified: Int = 3, nUnknown: Int = 2, maxKm: Double = 50.0, unknownKm: Double = 10.0, violet: (Station) -> Boolean = { false },
                /** Non null = classement « heuristique » des bornes non essayées, d'après le niveau d'indice (jamais un pourcentage). */
                indiceOf: ((Station) -> IndiceInfo)? = null): List<UrgenceItem> {
        val byStation = releves.groupBy { it.stationId }
        val cands = stations.asSequence()
            .filter { (it.current != "DC" || carAcceptsDc(car)) && it.kind != "privee" }
            .map { it to Geo.distanceM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxKm * 1000 }
            .sortedBy { it.second }
        val verified = ArrayList<UrgenceItem>()
        val unknown = ArrayList<UrgenceItem>()
        for ((s, d) in cands) {
            val mine = (byStation[s.id] ?: emptyList()).filter { (canonicalCar(it.car) ?: DEFAULT_CAR) == car && !it.horsVerdict() }
            val st = simpleStatus(mine)
            when {
                mine.isEmpty() -> if (d <= unknownKm * 1000 && StationInfo.accessLabel(s) != "Accès privé") unknown.add(UrgenceItem(s, d, st, false, 0.0, s.id in brokenIds, s.current == "DC", violet(s), indiceOf?.invoke(s)))
                mine.any { it.result == Result.OK } && st.color != COLOR_NO -> if (verified.size < 40) verified.add(UrgenceItem(s, d, st, true, 0.0, s.id in brokenIds, s.current == "DC"))
            }
            if (verified.size >= 40 && unknown.size >= 40) break
        }
        // Preuves d'abord (essais réels). Parmi les non essayées : sans panne signalée, puis violettes (annoncées 400 V, hypothèse) avant les autres, puis la distance.
        // L'anneau gris n'exclut aucune borne.
        val cmpV = compareBy<UrgenceItem> { it.broken }.thenBy { it.distM }
        val cmpU = compareBy<UrgenceItem> { it.broken }.thenBy { !it.violet }.thenBy { it.distM }
        val nearest = unknown.filter { !it.broken }.minOfOrNull { it.distM } ?: Double.MAX_VALUE
        val cmpU2 = compareBy<UrgenceItem> { it.broken }.thenBy { !(it.violet && it.distM <= 2 * nearest + 500) }.thenBy { it.distM }
        if (indiceOf != null) {
            // Heuristique : parmi les bornes « proches de la plus proche » (≤ 2 × distance + 500 m), indice favorable, puis aucun indice, puis défavorable ;
            // les bornes plus lointaines suivent, à la distance. Une panne signalée repousse toujours la borne. Rien n'est exclu.
            val limite = 2 * nearest + 500
            fun classe(u: UrgenceItem) = if (u.distM > limite) 3 else when (u.indice?.favorable) { true -> 0; null -> 1; false -> 2 }
            val cmpH = compareBy<UrgenceItem> { it.broken }.thenBy { classe(it) }.thenBy { it.distM }
            return verified.sortedWith(cmpV).take(nVerified) + unknown.sortedWith(cmpH).take(nUnknown)
        }
        return verified.sortedWith(cmpV).take(nVerified) + unknown.sortedWith(cmpU2).take(nUnknown)
    }

    fun distLabel(m: Double) = if (m < 1000) "${m.toInt()} m" else String.format(java.util.Locale.FRANCE, "%.1f km", m / 1000)
}
