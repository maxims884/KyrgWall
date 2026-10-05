package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.billingclient.api.BillingClient
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ManagerKt  constructor() {
    var paginationList = ArrayList<PictureKt>()
    var position: Int? = null
    lateinit var db: FirebaseFirestore
    var currentType = ""
    var arrayAdapter: PhotoAdapterKt? = null
    var customGalleryAdapter: CustomGalleryAdapterKt? = null
    private var lastVisible: DocumentSnapshot? = null
    private var loading = false
    var pullToRefresh: SwipeRefreshLayout? = null
    var pgsBar: ProgressBar? = null
    var emptyView: View? = null
    var sp: SharedPreferences? = null
    var billingClient: BillingClient? = null
    var context: Context? = null
    companion object{
        private const val PAGE_SIZE = 30L
        // После смены темы активити пересоздаётся; возвращаем пользователя в настройки
        var reopenSettings = false
        private  var instance: ManagerKt? = null
        fun getInstance() = synchronized(this){
            if(instance == null)
                instance = ManagerKt()
            instance
        }
    }

    fun loadFirstItems(type: String) {
        currentType = type
        lastVisible = null
        emptyView?.visibility = View.GONE
        if (type == FavoritesKt.TYPE) {
            paginationList.addAll(FavoritesKt.getAll(context!!))
            onItemsChanged()
            return
        }
        if (type.startsWith(SearchKt.PREFIX)) {
            loading = true
            CatalogKt.load(context!!) { pictures ->
                if (type != currentType) return@load
                loading = false
                paginationList.addAll(SearchKt.filter(pictures, type.removePrefix(SearchKt.PREFIX)))
                onItemsChanged()
            }
            return
        }
        if (type == CatalogKt.FEED) {
            // Лента: все категории вперемешку, при каждом открытии в новом порядке
            loading = true
            CatalogKt.load(context!!) { pictures ->
                if (type != currentType) return@load
                loading = false
                paginationList.addAll(CatalogKt.feedOrder(pictures))
                onItemsChanged()
            }
            return
        }
        loading = true
        db.collection(type)
                .orderBy("url")
                .limit(PAGE_SIZE)
                .get()
                .addOnSuccessListener { documentSnapshots ->
                    // Пока шёл запрос, пользователь мог открыть другую вкладку
                    if (type != currentType) return@addOnSuccessListener
                    paginationList.addAll(documentSnapshots.toObjects(PictureKt::class.java))
                    lastVisible = documentSnapshots.documents.lastOrNull()
                }
                .addOnCompleteListener {
                    if (type != currentType) return@addOnCompleteListener
                    loading = false
                    onItemsChanged()
                }
    }

    fun loadNextItems() {
        val type = currentType
        val last = lastVisible
        if (type == FavoritesKt.TYPE || type == CatalogKt.FEED || type.startsWith(SearchKt.PREFIX)
            || last == null || loading) return
        loading = true
        db.collection(type)
                .orderBy("url")
                .limit(PAGE_SIZE)
                .startAfter(last)
                .get()
                .addOnSuccessListener { documentSnapshots ->
                    if (type != currentType) return@addOnSuccessListener
                    paginationList.addAll(documentSnapshots.toObjects(PictureKt::class.java))
                    // Пустая страница — картинки в категории закончились
                    lastVisible = documentSnapshots.documents.lastOrNull()
                    onItemsChanged()
                }
                .addOnCompleteListener {
                    if (type == currentType) loading = false
                }
    }

    private fun onItemsChanged() {
        pullToRefresh?.isRefreshing = false
        pgsBar?.visibility = View.INVISIBLE
        emptyView?.visibility = if (paginationList.isEmpty()) View.VISIBLE else View.GONE
        arrayAdapter?.refresh()
        customGalleryAdapter?.notifyDataSetChanged()
    }

    fun isOnline(): Boolean {
        val cm = context?.getSystemService(AppCompatActivity.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (cm != null) {
            val netInfo = cm.activeNetworkInfo
            return netInfo != null && netInfo.isConnectedOrConnecting
        }
        return false
    }

    fun logEvent(name: String) {
        val params = Bundle()
        params.putString("type", currentType)
        FirebaseAnalytics.getInstance(context!!).logEvent(name, params)
    }

    fun setPrefRemoveAd(value: Int) {
        val editor = sp!!.edit()
        editor.putInt("ad", value)
        editor.apply()
    }

    fun getPrefRemoveAd(): Int {
        var value = 0
        value = sp!!.getInt("ad", value)
        return value
    }
}
