package org.borneneutre.app

/** « Urgence batterie » : les bornes les plus proches, classées, calculées hors ligne. Aucune garantie : un relevé passé ne promet rien. */
data class UrgenceItem(val station: Station, val distM: Double, val status: SimpleStatus, val tried: Boolean, val score: Double = 0.0, val broken: Boolean = false, val dc: Boolean = false)

object Urgence {
    /**
     * Jusqu'à [nVerified] bornes où la voiture [car] a déjà chargé (les plus proches d'abord, dans [maxKm]),
     * puis jusqu'à [nUnknown] bornes proches pas encore essayées. Exclues : bornes où la voiture refuse, bornes privées. Une panne signalée n'exclut plus la borne (un signalement isolé peut être faux ou périmé) : elle est marquée et classée après les autres.
     */
    fun nearest(lat: Double, lon: Double, stations: List<Station>, releves: List<Releve>, car: String, brokenIds: Set<String>,
                nVerified: Int = 3, nUnknown: Int = 2, maxKm: Double = 50.0, unknownKm: Double = 10.0, score: (Station) -> Double = { 0.0 }): List<UrgenceItem> {
        val byStation = releves.groupBy { it.stationId }
        val cands = stations.asSequence()
            .filter { (it.current != "DC" || carAcceptsDc(car)) && it.kind != "privee" }
            .map { it to Geo.distanceM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxKm * 1000 }
            .sortedBy { it.second }
        val verified = ArrayList<UrgenceItem>()
        val unknown = ArrayList<UrgenceItem>()
        for ((s, d) in cands) {
            val mine = (byStation[s.id] ?: emptyList()).filter { (canonicalCar(it.car) ?: DEFAULT_CAR) == car }
            val st = simpleStatus(mine)
            when {
                mine.isEmpty() -> if (d <= unknownKm * 1000 && StationInfo.accessLabel(s) != "Accès privé") unknown.add(UrgenceItem(s, d, st, false, score(s), s.id in brokenIds, s.current == "DC"))
                mine.any { it.result == Result.OK } && st.color != COLOR_NO -> if (verified.size < 40) verified.add(UrgenceItem(s, d, st, true, 0.0, s.id in brokenIds, s.current == "DC"))
            }
            if (verified.size >= 40 && unknown.size >= 40) break
        }
        // Les bornes sans panne signalée d'abord, puis la distance (l'estimation des zones ne sert plus à classer : elle est expérimentale).
        val cmp = compareBy<UrgenceItem> { it.broken }.thenBy { it.distM }
        return verified.sortedWith(cmp).take(nVerified) + unknown.sortedWith(cmp).take(nUnknown)
    }

    fun distLabel(m: Double) = if (m < 1000) "${m.toInt()} m" else String.format(java.util.Locale.FRANCE, "%.1f km", m / 1000)
}
