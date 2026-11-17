package org.borneneutre.app

/** « Urgence batterie » : les bornes les plus proches, classées, calculées hors ligne. Aucune garantie : un relevé passé ne promet rien. */
data class UrgenceItem(val station: Station, val distM: Double, val status: SimpleStatus, val tried: Boolean, val score: Double = 0.0)

object Urgence {
    /**
     * Jusqu'à [nVerified] bornes où la voiture [car] a déjà chargé (les plus proches d'abord, dans [maxKm]),
     * puis jusqu'à [nUnknown] bornes proches pas encore essayées. Exclues : bornes où la voiture refuse, bornes avec une panne signalée, bornes privées.
     */
    fun nearest(lat: Double, lon: Double, stations: List<Station>, releves: List<Releve>, car: String, brokenIds: Set<String>,
                nVerified: Int = 3, nUnknown: Int = 2, maxKm: Double = 50.0, unknownKm: Double = 10.0, score: (Station) -> Double = { 0.0 }): List<UrgenceItem> {
        val byStation = releves.groupBy { it.stationId }
        val cands = stations.asSequence()
            .filter { (it.current != "DC" || carAcceptsDc(car)) && it.kind != "privee" && it.id !in brokenIds }
            .map { it to Geo.distanceM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxKm * 1000 }
            .sortedBy { it.second }
        val verified = ArrayList<UrgenceItem>()
        val unknown = ArrayList<UrgenceItem>()
        for ((s, d) in cands) {
            val mine = (byStation[s.id] ?: emptyList()).filter { (canonicalCar(it.car) ?: DEFAULT_CAR) == car }
            val st = simpleStatus(mine)
            when {
                mine.isEmpty() -> if (d <= unknownKm * 1000 && StationInfo.accessLabel(s) != "Accès privé") unknown.add(UrgenceItem(s, d, st, false, score(s)))
                mine.any { it.result == Result.OK } && st.color != COLOR_NO -> if (verified.size < nVerified) verified.add(UrgenceItem(s, d, st, true))
            }
            if (verified.size >= nVerified && unknown.size >= 40) break
        }
        // Bornes non essayées : les mieux estimées d'abord (zones vertes), puis les plus proches.
        return verified + unknown.sortedWith(compareByDescending<UrgenceItem> { it.score }.thenBy { it.distM }).take(nUnknown)
    }

    fun distLabel(m: Double) = if (m < 1000) "${m.toInt()} m" else String.format(java.util.Locale.FRANCE, "%.1f km", m / 1000)
}
