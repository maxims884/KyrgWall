package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Список всех картинок из всех категорий для ленты, поиска и фоновых задач.
 * Хранится на устройстве. Новые картинки (бот добавляет их каждый день) докачиваются
 * раз в несколько часов одним маленьким запросом, а весь список — раз в неделю,
 * чтобы не читать весь Firestore при каждом запуске.
 */
object CatalogKt {
    const val FEED = "feed"
    const val CARDS = "cards"
    val WALLPAPER_TYPES = listOf("nature", "animals", "arch", "relig", "stars")
    private val ALL_TYPES = listOf(CARDS) + WALLPAPER_TYPES
    private const val FILE = "catalog.json"
    // Как часто проверять новые картинки и как часто перекачивать весь список (на случай удалений)
    private val NEW_CHECK = TimeUnit.HOURS.toMillis(6)
    private val FULL_REFRESH = TimeUnit.DAYS.toMillis(7)
    private const val PREFS = "Catalog"
    // В ленте сверху показываем добавленное за последние дни
    private val NEW_FOR = TimeUnit.DAYS.toMillis(3)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
                p.tags = o.optString("tags")
                p.author = o.optString("author").ifEmpty { null }
                p.license = o.optString("license").ifEmpty { null }
                p.sourceUrl = o.optString("sourceUrl").ifEmpty { null }
                val created = o.optLong("createdAt")
                if (created > 0) p.createdAt = Date(created)
                items.add(p)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    @Synchronized
    private fun save(context: Context, items: List<PictureKt>, full: Boolean) {
        val array = JSONArray()
        for (p in items) {
            array.put(
                JSONObject().put("url", p.url).put("urlSmall", p.urlSmall).put("type", p.type)
                    .put("tags", p.tagList().joinToString(","))
                    .put("author", p.author ?: "").put("license", p.license ?: "")
                    .put("sourceUrl", p.sourceUrl ?: "").put("createdAt", p.createdAt?.time ?: 0L)
            )
        }
        file(context).writeText(array.toString())
        val now = System.currentTimeMillis()
        val editor = prefs(context).edit().putLong("checked", now)
        if (full) editor.putLong("full", now)
        editor.apply()
    }

    private fun query(type: String, since: Date?): Task<QuerySnapshot> {
        val collection = FirebaseFirestore.getInstance().collection(type)
        return if (since == null) collection.get() else collection.whereGreaterThan("createdAt", since).get()
    }

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

    // Что нужно сделать: null — ничего, иначе дата, после которой докачать новые (Date(0) — всё заново)
    private fun plan(context: Context, cached: List<PictureKt>): Date? {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        if (cached.isEmpty() || now - p.getLong("full", 0) > FULL_REFRESH) return Date(0)
        if (now - p.getLong("checked", 0) < NEW_CHECK) return null
        val newest = cached.mapNotNull { it.createdAt?.time }.maxOrNull() ?: 0L
        return Date(maxOf(newest, 1L))
    }

    private fun merge(cached: List<PictureKt>, fresh: List<PictureKt>): List<PictureKt> {
        val known = cached.mapNotNull { it.url }.toHashSet()
        return fresh.filter { it.url !in known } + cached
    }

    /** Отдаёт каталог в главном потоке: с устройства, при необходимости докачав новое */
    fun load(context: Context, onReady: (List<PictureKt>) -> Unit) {
        val cached = cached(context)
        val since = plan(context, cached)
        if (since == null) {
            onReady(cached)
            return
        }
        val full = since.time == 0L
        Tasks.whenAllSuccess<QuerySnapshot>(ALL_TYPES.map { query(it, if (full) null else since) })
            .addOnSuccessListener { snapshots ->
                val fresh = parse(snapshots)
                val items = if (full) fresh else merge(cached, fresh)
                if (items.isNotEmpty()) save(context, items, full)
                onReady(items)
            }
            // Нет сети — показываем то, что есть на устройстве
            .addOnFailureListener { onReady(cached) }
    }

    /** То же самое для фоновых задач. Вызывать не из главного потока */
    fun loadBlocking(context: Context): List<PictureKt> {
        val cached = cached(context)
        if (cached.isNotEmpty()) return cached
        return try {
            val tasks = ALL_TYPES.map { query(it, null) }
            val items = parse(Tasks.await(Tasks.whenAllSuccess<QuerySnapshot>(tasks), 60, TimeUnit.SECONDS))
            if (items.isNotEmpty()) save(context, items, true)
            items
        } catch (e: Exception) {
            e.printStackTrace()
            cached
        }
    }

    /** Порядок ленты: сначала новые (свежие сверху), потом остальное вперемешку */
    fun feedOrder(pictures: List<PictureKt>): List<PictureKt> {
        val border = System.currentTimeMillis() - NEW_FOR
        val (fresh, rest) = pictures.partition { (it.createdAt?.time ?: 0L) > border }
        return fresh.sortedByDescending { it.createdAt } + rest.shuffled()
    }
}
