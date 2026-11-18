package org.borneneutre.app

import android.content.Context
import org.json.JSONObject

/** Bornes de la Région de Bruxelles-Capitale : données ouvertes de Bruxelles Mobilité (Paradigm / Eco-Movement), licence CC BY.
 *  Fichier embarqué (assets/bornes-bruxelles.json), un enregistrement par emplacement. Pas d'adresse ni de puissance dans la source :
 *  seulement l'exploitant, le nombre de points et le type de courant (mono / triphasé / continu). */
object Brussels {
    const val SOURCE = "Bruxelles Mobilité (data.mobility.brussels) / Eco-Movement"

    fun parse(json: String): List<Station> {
        val arr = JSONObject(json).optJSONArray("stations") ?: return emptyList()
        val out = ArrayList<Station>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val types = r.optString("c", "").split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val ac = types.any { it.startsWith("AC") }
            val dc = types.any { it == "DC" }
            val tags = JSONObject()
            val pts = r.optInt("p", 0)
            if (pts > 0) tags.put("capacity", pts.toString())
            val ph = when {
                types.contains("AC_3_PHASE") && types.contains("AC_1_PHASE") -> "monophasé et triphasé"
                types.contains("AC_3_PHASE") -> "triphasé"
                types.contains("AC_1_PHASE") -> "monophasé"
                else -> ""
            }
            if (ph.isNotEmpty()) tags.put("phases", ph)
            tags.put("source", SOURCE)
            val op = r.optString("o", "")
            out.add(Station("bx-" + r.optString("i", i.toString()), op.ifBlank { "Borne (Bruxelles)" }, op,
                r.optDouble("la"), r.optDouble("lo"), if (dc && !ac) "DC" else if (ac) "AC" else "?", "", "osm", tags.toString()))
        }
        return out
    }

    fun loadEmbedded(ctx: Context): List<Station> {
        val local = try { DataUpdate.localFile(ctx, DataUpdate.BXL_FILE)?.let { parse(it.readText()) } } catch (e: Exception) { null }
        if (!local.isNullOrEmpty()) return local
        return try { parse(ctx.assets.open("bornes-bruxelles.json").bufferedReader().readText()) } catch (e: Exception) { emptyList() }
    }
}
