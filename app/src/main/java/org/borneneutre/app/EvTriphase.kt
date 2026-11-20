package org.borneneutre.app

/** Bornes EnergyVision triphasées « à essayer » (anneau violet hachuré) : DÉSACTIVÉ dans la version publique 1.3.1 (liste vide).
 *  Source : flux DATEX II d'EnergyVision (table statique du 20/11/2026 13:00 UTC), croisé par coordonnées (< 30 m) avec le jeu de Bruxelles ;
 *  retenues : phases AC_3_PHASE dans le jeu ET puissance du flux ≥ 11 kW (102 stations). Aucune clé ni donnée brute du flux n'est reprise ici.
 *  Le flux ne donne pas le neutre (tension toujours « 230 » par convention) : c'est un « à essayer » (hypothèse), jamais « compatible ». */
object EvTriphase {
    val IDS: Set<String> = emptySet() // liste retirée de la version publique : en attente de l'accord écrit d'EnergyVision
    /** Les stations de Bruxelles portent l'identifiant « bx-<uuid> » (Brussels.kt) : le préfixe est retiré avant la recherche. */
    fun contient(id: String): Boolean = id.removePrefix("bx-") in IDS
}
fun Station.anneauEv(forCar: List<Releve>): Boolean = current != "DC" && EvTriphase.contient(id) && forCar.none { Essai400.concluant(it) }
