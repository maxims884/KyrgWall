package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Список всех картинок из всех категорий для ленты и фоновых задач.
 * Хранится на устройстве и обновляется с сервера раз в несколько дней,
 * чтобы не читать весь Firestore при каждом запуске.
 */
object CatalogKt {
    const val FEED = "feed"
    const val CARDS = "cards"
    val WALLPAPER_TYPES = listOf("nature", "animals", "arch", "relig", "stars")
    private val ALL_TYPES = listOf(CARDS) + WALLPAPER_TYPES
    private const val FILE = "catalog.json"
    private val MAX_AGE = TimeUnit.DAYS.toMillis(3)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun isFresh(context: Context): Boolean {
        val file = file(context)
        return file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE
    }

    /** Картинки, сохранённые на устройстве; пустой список, если их ещё нет */
    @Synchronized
    fun cached(context: Context): ArrayList<PictureKt> {
        val items = ArrayList<PictureKt>()
        val file = file(context)
        if (!file.exists()) return items
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val p = PictureKt()
                p.url = o.optString("url")
                p.urlSmall = o.optString("urlSmall")
                p.type = o.optString("type")
                items.add(p)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    @Synchronized
    private fun save(context: Context, items: List<PictureKt>) {
        val array = JSONArray()
        for (p in items) {
            array.put(JSONObject().put("url", p.url).put("urlSmall", p.urlSmall).put("type", p.type))
        }
        file(context).writeText(array.toString())
    }

    private fun queries() = ALL_TYPES.map { FirebaseFirestore.getInstance().collection(it).get() }

    private fun parse(snapshots: List<QuerySnapshot>): ArrayList<PictureKt> {
        val items = ArrayList<PictureKt>()
        for (i in snapshots.indices) {
            for (p in snapshots[i].toObjects(PictureKt::class.java)) {
                if (p.url.isNullOrEmpty()) continue
                // У старых документов поле type могло быть не заполнено
                if (p.type.isNullOrEmpty()) p.type = ALL_TYPES[i]
                items.add(p)
            }
        }
        return items
    }

    /** Отдаёт каталог в главном потоке: с устройства, если он свежий, иначе с сервера */
    fun load(context: Context, onReady: (List<PictureKt>) -> Unit) {
        if (isFresh(context)) {
            val items = cached(context)
            if (items.isNotEmpty()) {
                onReady(items)
                return
            }
        }
        Tasks.whenAllSuccess<QuerySnapshot>(queries())
            .addOnSuccessListener { snapshots ->
                val items = parse(snapshots)
                if (items.isNotEmpty()) save(context, items)
                onReady(items)
            }
            // Нет сети — показываем то, что есть на устройстве
            .addOnFailureListener { onReady(cached(context)) }
    }

    /** То же самое для фоновых задач. Вызывать не из главного потока */
    fun loadBlocking(context: Context): List<PictureKt> {
        val cached = cached(context)
        if (isFresh(context) && cached.isNotEmpty()) return cached
        return try {
            val items = parse(Tasks.await(Tasks.whenAllSuccess<QuerySnapshot>(queries()), 60, TimeUnit.SECONDS))
            if (items.isNotEmpty()) save(context, items)
            items
        } catch (e: Exception) {
            e.printStackTrace()
            cached
        }
    }
}
