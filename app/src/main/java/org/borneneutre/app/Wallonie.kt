package org.borneneutre.app

import android.content.Context
import org.json.JSONObject

/** Bornes de Wallonie : « Infrastructures de recharge pour véhicules électriques en Wallonie » (Service public de Wallonie), licence CC BY 4.0.
 *  Fichier embarqué (assets/bornes-wallonie.json), un enregistrement par emplacement, fabriqué à partir du CSV officiel (copie locale, jamais d'appel depuis le téléphone).
 *  Pas de tension ni de nombre de phases dans la source : toujours « pas essayée » (gris), jamais un verdict de compatibilité. */
object Wallonie {
    const val SOURCE = "Source : Service public de Wallonie (SPW) - Infrastructures de recharge pour véhicules électriques en Wallonie (2026-01-30)"

    fun parse(json: String): List<Station> {
        val root = JSONObject(json)
        val src = root.optString("source", "").ifBlank { SOURCE } // la date des données est écrite dans le fichier
        val arr = root.optJSONArray("stations") ?: return emptyList()
        val out = ArrayList<Station>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val tags = JSONObject()
            val pts = r.optInt("p", 0); if (pts > 0) tags.put("capacity", pts.toString())
            val kw = r.optDouble("kw", 0.0); if (kw > 0) tags.put("charging_station:output", "${if (kw % 1.0 == 0.0) kw.toInt().toString() else kw.toString()} kW")
            if (r.optInt("s", 0) == 1) tags.put("access", "semipublic")
            r.optString("a", "").takeIf { it.isNotBlank() }?.let { tags.put("addr:full", it) }
            tags.put("source", src)
            r.optString("d", "").takeIf { it.length >= 10 }?.let { tags.put(StationInfo.ADDED_KEY, it) }
            val op = r.optString("o", "")
            val dc = r.optString("c", "AC") == "DC" || Overpass.isFastOperator(op)
            out.add(Station("wal-" + r.optString("i", i.toString()), (if (op.isNotBlank()) op else "Borne (Wallonie)") + (r.optString("a", "").takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""), op,
                r.optDouble("la"), r.optDouble("lo"), if (dc) "DC" else "AC", "", "osm", tags.toString()))
        }
        return out
    }

    /** Liste téléchargée (mise à jour mensuelle) si elle existe et reste lisible, sinon liste embarquée. */
    fun loadEmbedded(ctx: Context): List<Station> {
        val local = try { DataUpdate.localFile(ctx, DataUpdate.WAL_FILE)?.let { parse(it.readText()) } } catch (e: Exception) { null }
        if (local != null && local.size >= 1000) return local
        return try { parse(ctx.assets.open("bornes-wallonie.json").bufferedReader().readText()) } catch (e: Exception) { emptyList() }
    }
}
