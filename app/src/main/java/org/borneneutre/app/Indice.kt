package org.borneneutre.app

/** Score d'indice (1.3.2) : un NIVEAU EN MOTS, jamais un pourcentage ni un verdict. Priorité : essais réels > annonce 400 V
 *  (autocollant vert ou bleu vu, anneau violet de la concession) > phases ou listes défavorables > aucun indice.
 *  Chaque niveau porte sa source et sa date ; la phrase cite « selon des conducteurs », jamais une personne. */
data class IndiceInfo(val niveau: String, val texte: String, val favorable: Boolean?)

object Indice {
    const val ESSAYEE = "Essayée"
    const val FAVORABLE = "Indice favorable"
    const val DEFAVORABLE = "Indice défavorable"
    const val AUCUN = "Aucun indice"

    private fun jjmm(ts: Long): String = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) }

    /** `mine` : relevés de la voiture de l'utilisateur ; `pastilles` : constats visuels de cette borne. */
    fun of(s: Station, mine: List<Releve>, fichePastille: String?, pastilles: List<Pastille>): IndiceInfo {
        val essais = mine.filterNot { it.horsVerdict() }
        if (essais.isNotEmpty()) return IndiceInfo(ESSAYEE, "Essai réel enregistré (${essais.size}) : c'est le seul niveau qui vaut preuve pour cette voiture.", null)
        val vues = pastilles.filter { it.stationId == s.id }
        val vert = vues.filter { it.code == "verte" || it.code == "bleue" }.maxByOrNull { it.ts }
        val blancheVue = vues.filter { it.code == "blanche" }.maxByOrNull { it.ts }
        if (vert != null && blancheVue != null && blancheVue.ts > vert.ts) return IndiceInfo(AUCUN, "Indices contradictoires : autocollant « 22 kW – 3x400V + N » vu le ${jjmm(vert.ts)}, puis « 7,2 kW – 1×230 V » vu le ${jjmm(blancheVue.ts)} (selon des conducteurs). Constats visuels, à essayer.", null)
        if (vert != null) return IndiceInfo(FAVORABLE, "Autocollant « 22 kW – 3x400V + N » vu sur la borne le ${jjmm(vert.ts)} (selon des conducteurs). Constat visuel, pas un essai.", true)
        if (fichePastille == "verte" || fichePastille == "bleue") return IndiceInfo(FAVORABLE, "Autocollant « 22 kW – 3x400V + N » vu sur la borne (selon des conducteurs). Constat visuel, pas un essai.", true)
        val blancheRecente = blancheVue != null && System.currentTimeMillis() - blancheVue.ts < 90L * 24 * 3600 * 1000
        if (s.dansLaConcession() && Essai400.near(s.lat, s.lon) != null && !blancheRecente) return IndiceInfo(FAVORABLE, "Annoncée en 400 V par la Région (concession bruxelloise, liste du 16/11/2026). Probablement compatible. Non vérifié sur place.", true)
        val blanche = vues.filter { it.code == "blanche" }.maxByOrNull { it.ts }
        if (blanche != null || fichePastille == "blanche") return IndiceInfo(DEFAVORABLE, "Autocollant « 7,2 kW – 1×230 V » vu sur la borne (selon des conducteurs). Constat visuel : probablement non compatible.", false)
        if (StationInfo.phases(s) == "monophasé") return IndiceInfo(DEFAVORABLE, "Annoncée monophasée dans les données ouvertes de Bruxelles Mobilité. Hypothèse : probablement non compatible.", false)
        if (s.anneau230()) return IndiceInfo(DEFAVORABLE, "Sur la liste « 230 V » de TotalEnergies (données du 16/11/2026). Hypothèse : probablement non compatible.", false)
        return IndiceInfo(AUCUN, "Pas d'annonce 400 V connue. Compatibilité inconnue : à essayer.", null)
    }
}
