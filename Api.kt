package com.example.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

data class Item(val id: String, val title: String, val ext: String = "m3u8")

object Api {
    var base = ""; var user = ""; var pass = ""

    private suspend fun get(action: String?, extra: String = ""): String = withContext(Dispatchers.IO) {
        val a = if (action == null) "" else "&action=$action"
        URL("$base/player_api.php?username=$user&password=$pass$a$extra").readText()
    }

    suspend fun login(): Boolean =
        JSONObject(get(null)).optJSONObject("user_info")?.optInt("auth") == 1

    suspend fun cats(kind: String): List<Pair<String, String>> {
        val a = JSONArray(get("get_${kind}_categories"))
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("category_id") to o.getString("category_name") } }
    }

    suspend fun items(kind: String, cat: String): List<Item> {
        val action = if (kind == "series") "get_series" else "get_${kind}_streams"
        val a = JSONArray(get(action, "&category_id=$cat"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val id = (if (kind == "series") o.get("series_id") else o.get("stream_id")).toString()
            Item(id, o.optString("name"), o.optString("container_extension", "m3u8").ifEmpty { "m3u8" })
        }
    }

    suspend fun episodes(seriesId: String): List<Item> {
        val eps = JSONObject(get("get_series_info", "&series_id=$seriesId")).optJSONObject("episodes") ?: return emptyList()
        val out = mutableListOf<Item>()
        eps.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }.forEach { s ->
            val arr = eps.getJSONArray(s)
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                out += Item(e.getString("id"), "S$s E${e.optString("episode_num")} - ${e.optString("title")}", e.optString("container_extension", "mp4"))
            }
        }
        return out
    }

    fun url(kind: String, i: Item) = when (kind) {
        "live" -> "$base/live/$user/$pass/${i.id}.m3u8"
        "vod" -> "$base/movie/$user/$pass/${i.id}.${i.ext}"
        else -> "$base/series/$user/$pass/${i.id}.${i.ext}"
    }
}
