package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FavoritesKt {
    const val TYPE = "favorites"
    private const val KEY = "items"
    private var cache: ArrayList<PictureKt>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("Favorites", Context.MODE_PRIVATE)

    private fun items(context: Context): ArrayList<PictureKt> {
        cache?.let { return it }
        val items = ArrayList<PictureKt>()
        val array = JSONArray(prefs(context).getString(KEY, "[]"))
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val p = PictureKt()
            p.url = o.optString("url")
            p.urlSmall = o.optString("urlSmall")
            p.type = o.optString("type")
            items.add(p)
        }
        cache = items
        return items
    }

    // Новые избранные лежат в начале списка
    @Synchronized
    fun getAll(context: Context): ArrayList<PictureKt> {
        return ArrayList(items(context))
    }

    @Synchronized
    fun isFavorite(context: Context, picture: PictureKt): Boolean {
        return items(context).any { it.url == picture.url }
    }

    /** @return true, если картинка теперь в избранном */
    @Synchronized
    fun toggle(context: Context, picture: PictureKt): Boolean {
        val items = items(context)
        val removed = items.removeAll { it.url == picture.url }
        if (!removed) items.add(0, picture)
        val array = JSONArray()
        for (p in items) {
            array.put(JSONObject().put("url", p.url).put("urlSmall", p.urlSmall).put("type", p.type))
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
        return !removed
    }
}
