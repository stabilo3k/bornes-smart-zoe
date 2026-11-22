package org.borneneutre.app

import org.json.JSONObject

/** Fusion des bornes de sources différentes : UNE seule règle graduée, UNE seule fonction de décision (cahier 1.3.2, B3).
 *  - jamais deux bornes d'une même source (deux points d'une même liste restent deux bornes) ; une borne de la liste maîtresse compte comme source « manuel », quel que soit son identifiant ;
 *  - ≤ 10 m : même endroit, toujours ;
 *  - 10 à 25 m : même endroit, sauf si les deux exploitants sont connus et différents ;
 *  - 25 à 40 m : même exploitant connu ;
 *  - 40 à 80 m : même exploitant connu ET une seule candidate de part et d'autre ;
 *  - pas de chaîne : l'alias pointe toujours vers une borne conservée ; une borne de la communauté (liste maîtresse) est conservée en priorité.
 *  Exploitant « inconnu » = champ vide. */
object Fusion {
    const val M_TOUJOURS = 10.0
    const val M_MIXTE = 25.0
    const val M_MEME_EXPLOITANT = 40.0
    const val M_UNIQUE = 80.0

    data class Result(val all: List<Station>, val alias: Map<String, String>)

    fun source(id: String): String = when {
        id.startsWith("osm-") -> "osm"; id.startsWith("fl-") -> "fl"; id.startsWith("bx-") -> "bx"; id.startsWith("wal-") -> "wal"
        else -> "manuel"
    }

    private fun norm(op: String) = op.lowercase().filter { it.isLetterOrDigit() }
    /** « inconnu » / « unknown » (texte des listes embarquées) = exploitant non renseigné. */
    fun blank(op: String): Boolean = op.isBlank() || norm(op) in setOf("inconnu", "inconnue", "unknown", "onbekend", "notpublished")
    private fun known(op: String) = !blank(op) && norm(op).length >= 3
    /** Marques d'un même groupe = même exploitant pour la fusion.
     *  TotalEnergies : PitPoint (marque de recharge de Total, rapporté) et charge.brussels (concession régionale, rapporté) ;
     *  EnergyVision : EnergyDrive (marque de la concession bruxelloise), brusol (initiative d'EnergyVision, presse 2021). Tous R : à confirmer par écrit. */
    private fun grp(n: String) = when {
        n.startsWith("pitpoint") || n.startsWith("chargebrussels") -> "totalenergies"
        n.startsWith("energydrive") || n.startsWith("energidrive") || n.startsWith("brusol") -> "energyvision"
        else -> n
    }
    private fun sameOp(a: String, b: String): Boolean {
        if (!known(a) || !known(b)) return false
        val x = grp(norm(a)); val y = grp(norm(b))
        return x == y || (minOf(x.length, y.length) >= 4 && (x.contains(y) || y.contains(x)))
    }

    /** Décision pure à la distance d (mètres), sans la règle d'unicité (40-80 m). */
    fun accepts(d: Double, opA: String, opB: String): Boolean = when {
        d <= M_TOUJOURS -> true
        d < M_MIXTE -> !(known(opA) && known(opB) && !sameOp(opA, opB))
        d < M_MEME_EXPLOITANT -> sameOp(opA, opB)
        d < M_UNIQUE -> sameOp(opA, opB)
        else -> false
    }

    private class Index(val st: List<Station>) {
        val step = 0.0012
        val grid = HashMap<Long, MutableList<Station>>()
        private fun key(y: Long, x: Long) = y * 1_000_003L + x
        init { st.forEach { grid.getOrPut(key(Math.floor(it.lat / step).toLong(), Math.floor(it.lon / step).toLong())) { ArrayList() }.add(it) } }
        fun near(lat: Double, lon: Double, maxM: Double): List<Pair<Station, Double>> {
            val cy = Math.floor(lat / step).toLong(); val cx = Math.floor(lon / step).toLong()
            val out = ArrayList<Pair<Station, Double>>()
            for (dy in -1L..1L) for (dx in -1L..1L) grid[key(cy + dy, cx + dx)]?.forEach { k ->
                val d = Geo.distanceM(lat, lon, k.lat, k.lon); if (d < maxM) out.add(k to d)
            }
            return out
        }
    }

    /** Borne connue qui représente `f` (même identifiant, ou règle graduée), ou null si `f` est un endroit nouveau. `foundIdx` sert à la règle d'unicité. */
    private fun match(idx: Index, foundIdx: Index, f: Station, master: Set<String>): Pair<Station, Boolean>? {
        val cands = idx.near(f.lat, f.lon, M_UNIQUE)
        cands.firstOrNull { it.first.id == f.id }?.let { return it.first to true }
        val src = source(f.id)
        fun srcK(k: Station) = if (k.id in master) "manuel" else source(k.id)
        val ok = cands.filter { (k, d) -> srcK(k) != src && accepts(d, k.operator, f.operator) }
        if (ok.isEmpty()) return null
        val best = ok.minByOrNull { it.second }!!
        if (best.second >= M_MEME_EXPLOITANT) { // 40-80 m : candidates uniques des deux côtés
            val sameOpKnown = cands.count { (k, _) -> srcK(k) != src && sameOp(k.operator, f.operator) }
            val sameOpFound = foundIdx.near(f.lat, f.lon, M_UNIQUE).count { (o, _) -> o.id != f.id && source(o.id) == src && sameOp(o.operator, f.operator) } + 1
            if (sameOpKnown != 1 || sameOpFound != 1) return null
        }
        return best.first to false
    }

    /** Bornes de `found` qui ne sont pas déjà représentées par `known`. */
    fun dedupe(known: List<Station>, found0: List<Station>, master: Set<String> = emptySet()): List<Station> {
        val found = found0.distinctBy { it.id }
        if (known.isEmpty()) return found
        val idx = Index(known); val fidx = Index(found)
        return found.filter { match(idx, fidx, it, master) == null }
    }

    /** Assemble les listes dans l'ordre donné : la liste maîtresse d'abord, puis chaque source ; renvoie les bornes conservées, les alias
     *  (identifiant écarté → identifiant conservé, pour TOUTE borne écartée) et reporte « phases », l'exploitant (si vide) et le courant (si « ? ») sur la borne conservée. */
    fun build(master: List<Station>, sources: List<List<Station>>): Result {
        val kept = ArrayList(master)
        val alias = HashMap<String, String>()
        val phases = HashMap<String, String>()
        val ops = HashMap<String, String>(); val curs = HashMap<String, String>()
        val masterIds = master.map { it.id }.toSet()
        for (src in sources) {
            val idx = Index(kept); val fidx = Index(src)
            val add = ArrayList<Station>()
            for (f in src) {
                val m = match(idx, fidx, f, masterIds)
                if (m == null) { add.add(f); continue }
                val (k, same) = m
                if (same) continue
                alias[f.id] = k.id
                if (blank(k.operator) && !blank(f.operator) && k.id !in ops) ops[k.id] = f.operator
                if (k.current == "?" && f.current != "?" && k.id !in curs) curs[k.id] = f.current
                StationInfo.phases(f)?.let { p -> phases[k.id] = merge(phases[k.id] ?: StationInfo.phases(k), p) }
            }
            kept.addAll(add)
        }
        val all = kept.map { s0 ->
            var s = phases[s0.id]?.let { p -> withPhases(s0, p) } ?: s0
            ops[s.id]?.let { o -> s = s.copy(operator = o) }
            curs[s.id]?.let { c -> s = s.copy(current = c) }
            s
        }
        return Result(all, alias)
    }

    /** Alias seulement (zones OSM affichées à l'écran ou en cache) : n'ajoute aucune borne. */
    fun aliasOnly(kept: List<Station>, found0: List<Station>, master: Set<String> = emptySet()): Map<String, String> {
        val found = found0.distinctBy { it.id }
        val idx = Index(kept); val fidx = Index(found); val out = HashMap<String, String>()
        for (f in found) { val m = match(idx, fidx, f, master) ?: continue; if (!m.second) out[f.id] = m.first.id }
        return out
    }

    private fun merge(a: String?, b: String): String = when {
        a == null || a == b -> b
        else -> "monophasé et triphasé"
    }

    private fun withPhases(s: Station, p: String): Station {
        if (StationInfo.phases(s) == p) return s
        val t = try { JSONObject(s.params.ifBlank { "{}" }) } catch (e: Exception) { JSONObject() }
        t.put("phases", p)
        return s.copy(params = t.toString())
    }
}
