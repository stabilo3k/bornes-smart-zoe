package org.borneneutre.app

import android.content.Context
import org.json.JSONArray

data class FaqItem(val id: String, val q: String, val a: String, val src: String, val img: String = "", val sec: String = "")

object Faq {
    fun parse(json: String): List<FaqItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FaqItem(o.getString("id"), o.getString("q"), o.getString("a"), o.optString("src", ""), o.optString("img", ""), o.optString("sec", ""))
        }
    }

    fun load(ctx: Context): List<FaqItem> =
        parse(ctx.assets.open("faq.json").bufferedReader().readText())
}
