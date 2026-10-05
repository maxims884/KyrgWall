package kg.black13.kyrgyzstanwallpaper

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable
import java.util.Date

@IgnoreExtraProperties
class PictureKt : Serializable {
     var url : String? = ""
     var urlSmall: String? = ""
     var type: String? = ""
     // Теги для поиска: в Firestore это массив строк или строка через запятую
     var tags: Any? = null
     // Автор и лицензия: для фото с Wikimedia Commons их обязательно показывать
     var author: String? = null
     var license: String? = null
     var sourceUrl: String? = null
     // Когда картинка добавлена; при записи из приложения ставится временем сервера
     @get:ServerTimestamp
     var createdAt: Date? = null

     fun tagList(): List<String> {
          return when (val value = tags) {
               is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
               is String -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
               else -> emptyList()
          }
     }

     /** Подпись под картинкой: "© автор · лицензия", если она нужна */
     fun credit(): String? {
          if (author.isNullOrBlank()) return null
          return "© " + author + (if (license.isNullOrBlank()) "" else " · $license")
     }
}
