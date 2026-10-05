package kg.black13.kyrgyzstanwallpaper

import java.util.Locale

/**
 * Поиск по каталогу на устройстве. Ищет по тегам картинки (поле tags в Firestore),
 * а если тегов нет — по словам, связанным с категорией, на трёх языках.
 */
object SearchKt {
    const val PREFIX = "search:"

    private val KEYWORDS = mapOf(
        "nature" to listOf(
            "природа", "пейзаж", "горы", "озеро", "лес", "река", "небо", "закат", "иссык-куль",
            "жаратылыш", "тоо", "көл", "токой", "дарыя", "асман", "ысык-көл",
            "nature", "landscape", "mountains", "lake", "forest", "river", "sky", "sunset", "issyk-kul"
        ),
        "animals" to listOf(
            "животные", "лошадь", "конь", "орел", "беркут", "барс", "собака", "тайган",
            "жаныбарлар", "жылкы", "бүркүт", "илбирс", "тайган",
            "animals", "horse", "eagle", "leopard", "dog"
        ),
        "arch" to listOf(
            "архитектура", "здание", "город", "бишкек", "площадь", "памятник",
            "имарат", "шаар", "аянт", "эстелик",
            "architecture", "building", "city", "bishkek", "square", "monument"
        ),
        "relig" to listOf(
            "религия", "мечеть", "ислам", "коран", "намаз",
            "дин", "мечит", "куран",
            "religion", "mosque", "islam", "quran"
        ),
        "stars" to listOf(
            "люди", "человек", "девушка", "национальный", "костюм", "калпак", "традиции",
            "адамдар", "кыз", "улуттук", "кийим", "салт",
            "people", "girl", "traditional", "costume", "kalpak"
        ),
        CatalogKt.CARDS to listOf(
            "открытка", "поздравление", "праздник", "айт", "нооруз", "жума", "день рождения",
            "куттуктоо", "майрам", "туулган күн",
            "card", "greeting", "holiday", "birthday", "eid", "nooruz"
        ),
    )

    private fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('ё', 'е').trim()

    // "гор" находит "горы", "лошади" находит "лошадь": сравниваем по общему началу слова
    private fun similar(a: String, b: String): Boolean {
        val common = a.commonPrefixWith(b).length
        return common >= 2 && common >= minOf(a.length, b.length, 4)
    }

    private fun matches(words: List<String>, word: String): Boolean {
        return words.any { phrase -> phrase.split(' ', '-').any { similar(it, word) } || phrase.startsWith(word) }
    }

    /** Сначала картинки, у которых совпали теги, потом совпавшие по категории */
    fun filter(pictures: List<PictureKt>, query: String): List<PictureKt> {
        val words = normalize(query).split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        val byTags = ArrayList<PictureKt>()
        val byCategory = ArrayList<PictureKt>()
        for (picture in pictures) {
            val tags = picture.tagList().map { normalize(it) }
            val keywords = KEYWORDS[picture.type] ?: emptyList()
            if (words.all { matches(tags, it) }) {
                byTags.add(picture)
            } else if (words.all { matches(tags, it) || matches(keywords, it) }) {
                byCategory.add(picture)
            }
        }
        return byTags + byCategory
    }
}
