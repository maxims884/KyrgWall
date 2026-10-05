package kg.black13.kyrgyzstanwallpaper

import java.io.Serializable

class PictureKt : Serializable {
     var url : String? = ""
     var urlSmall: String? = ""
     var type: String? = ""
     // Теги для поиска: в Firestore это массив строк или строка через запятую
     var tags: Any? = null

     fun tagList(): List<String> {
          return when (val value = tags) {
               is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
               is String -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
               else -> emptyList()
          }
     }
}
