package org.borneneutre.app

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zone d'alimentation, approximée par des règles géographiques locales (hypothèse, pas une carte officielle).
 * La compatibilité change à la frontière entre gestionnaires de réseau : les relevés voisins
 * ne comptent qu'à l'intérieur d'une même zone.
 */
enum class Zone(val label: String, val prior: Double) {
    SIBELGA("Bruxelles (Sibelga)", 0.35),
    FLUVIUS("Flandre (Fluvius)", 0.70),
    ORES("Wallonie (ORES)", 0.50),
    INCONNUE("zone inconnue", 0.50)
}

/** Un point d'évidence : la position d'une borne, ce que l'essai a donné, et sa zone. */
data class Evidence(val lat: Double, val lon: Double, val score: Double, val weight: Double, val zone: Zone = Zone.INCONNUE)

data class Estimate(val p: Double, val confidence: Double, val zone: Zone = Zone.INCONNUE) {
    val label: String
        get() = when {
            confidence < 0.3 -> "Peu de données" + when {
                p >= 0.6 -> " (zone plutôt favorable)"
                p <= 0.4 -> " (zone plutôt défavorable)"
                else -> ""
            }
            p >= 0.65 -> "Probablement compatible"
            p <= 0.35 -> "Probablement incompatible"
            else -> "Incertain"
        }
}

object Estimation {
    /** Portée de l'influence d'un relevé, en mètres : elle décroît vite avec la distance. */
    const val SIGMA_M = 250.0

    /** Poids de l'a priori de la zone, et de l'a priori propre à une borne (donnée ouverte). */
    const val ZONE_PRIOR_WEIGHT = 0.6
    const val STATION_PRIOR_WEIGHT = 0.8

    /**
     * Zone d'un point. Règles locales, valables seulement là où on a des relevés :
     * Jette/Wemmel (la Chaussée Romaine sépare Bruxelles de Wemmel) et Herne/Enghien (limite linguistique).
     * Ailleurs : zone inconnue, a priori neutre.
     */
    fun zoneOf(lat: Double, lon: Double): Zone {
        if (lat in 50.875..50.915 && lon in 4.295..4.345)
            return if (lat < 50.8935 || lon >= 4.3225) Zone.SIBELGA else Zone.FLUVIUS
        if (lat in 50.675..50.72 && lon in 4.00..4.09)
            return if (lat < 50.6945 || lon > 4.042) Zone.ORES else Zone.FLUVIUS
        return Zone.INCONNUE
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p = PI / 180
        val a = sin((lat2 - lat1) * p / 2).let { it * it } +
            cos(lat1 * p) * cos(lat2 * p) * sin((lon2 - lon1) * p / 2).let { it * it }
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    /** Un refus pèse autant qu'un succès (le partage de puissance n'est pas retenu comme cause : décision du projet). */
    fun evidenceFrom(releves: List<Releve>, stations: Map<String, Station>): List<Evidence> =
        releves.filterNot { it.horsVerdict() }.mapNotNull { r ->
            val s = stations[r.stationId] ?: return@mapNotNull null
            val (score, w) = when (r.result) {
                Result.OK -> 1.0 to 1.0
                Result.LIMITE -> 0.5 to 0.8
                Result.REFUS -> 0.0 to 1.0
            }
            Evidence(s.lat, s.lon, score, w, zoneOf(s.lat, s.lon))
        }

    /**
     * Estimation pour une borne sans relevé : a priori de la borne (si connu) ou de sa zone,
     * corrigé par les relevés voisins de la même zone seulement.
     */
    fun estimate(lat: Double, lon: Double, ev: List<Evidence>, stationPrior: Double? = null): Estimate {
        val zone = zoneOf(lat, lon)
        var sw = 0.0
        var sws = 0.0
        for (e in ev) {
            if (e.zone != zone) continue
            val d = distanceM(lat, lon, e.lat, e.lon)
            if (d > SIGMA_M * 4) continue
            val k = exp(-(d / SIGMA_M) * (d / SIGMA_M)) * e.weight
            sw += k
            sws += k * e.score
        }
        val prior = stationPrior ?: zone.prior
        val pw = if (stationPrior != null) STATION_PRIOR_WEIGHT else ZONE_PRIOR_WEIGHT
        val p = (sws + pw * prior) / (sw + pw)
        return Estimate(p, sw, zone)
    }

    /** Cellules de la grille autour des relevés, avec leur estimation. */
    fun grid(ev: List<Evidence>, cellM: Double = 150.0, reachM: Double = 600.0): List<GridCell> {
        if (ev.isEmpty()) return emptyList()
        val dLat = cellM / 111320.0
        val seen = HashSet<Long>()
        val out = ArrayList<GridCell>()
        for (e in ev) {
            val dLon = cellM / (111320.0 * cos(e.lat * PI / 180))
            val reachLat = reachM / 111320.0
            val reachLon = reachM / (111320.0 * cos(e.lat * PI / 180))
            val iMin = ((e.lat - reachLat) / dLat).toLong() - 1
            val iMax = ((e.lat + reachLat) / dLat).toLong() + 1
            val jMin = ((e.lon - reachLon) / dLon).toLong() - 1
            val jMax = ((e.lon + reachLon) / dLon).toLong() + 1
            for (i in iMin..iMax) for (j in jMin..jMax) {
                val key = i * 100000L + j
                if (!seen.add(key)) continue
                val lat0 = i * dLat
                val lon0 = j * dLon
                val est = estimate(lat0 + dLat / 2, lon0 + dLon / 2, ev)
                if (est.confidence < 0.12) continue
                out.add(GridCell(lat0, lon0, lat0 + dLat, lon0 + dLon, est))
            }
        }
        return out
    }

    /** Couleur d'une probabilité : rouge, jaune, vert. */
    fun colorFor(p: Double): String {
        val x = max(0.0, min(1.0, p))
        val r: Int
        val g: Int
        if (x < 0.5) {
            r = 214; g = (60 + (200 - 60) * (x / 0.5)).toInt()
        } else {
            r = (214 - (214 - 40) * ((x - 0.5) / 0.5)).toInt(); g = 175 + ((x - 0.5) / 0.5 * 20).toInt()
        }
        return String.format("#%02x%02x%02x", r, g, 60)
    }
}

data class GridCell(val lat0: Double, val lon0: Double, val lat1: Double, val lon1: Double, val est: Estimate)
