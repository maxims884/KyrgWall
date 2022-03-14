package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.billingclient.api.BillingClient
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.util.*

class ManagerKt  constructor() {
    val AD_UNIT_ID = "ca-app-pub-2230097402282612/4583659604"
    var interstitialAd: InterstitialAd? = null
    var paginationList = ArrayList<PictureKt>()
    var position: Int? = null
    var mNavigator: FragmentNavigatorKt? = null
    val DEFAULT_POSITION = 0
    var mAdView: AdView? = null
    var mAdView1: AdView? = null
    lateinit var db: FirebaseFirestore
    var currentType = ""
    var arrayAdapter: PhotoAdapterKt? = null
    var customGalleryAdapter: CustomGalleryAdapterKt? = null
    private lateinit var lastVisible: DocumentSnapshot
    var pullToRefresh: SwipeRefreshLayout? = null
    var pgsBar: ProgressBar? = null
    var nativeAd: NativeAd? = null
    var loader: AdLoader? = null
        get() = field
        set(value) {
            field = value
        }
    var adSequence: Int = 0
    var sp: SharedPreferences? = null
        get() = field
        set(value) {
            field = value
        }
    var billingClient: BillingClient? = null
    var context: Context? = null
    private val scope = CoroutineScope(newSingleThreadContext("name"))
    companion object{
        private  var instance: ManagerKt? = null
        fun getInstance() = synchronized(this){
            if(instance == null)
                instance = ManagerKt()
            instance
        }
    }

    fun loadFirstItems(type: String) {
        scope.launch {
            currentType = type
            db.collection(type)
                    .orderBy("url")
                    .limit(30)
                    .get()
                    .addOnSuccessListener { documentSnapshots ->
                        val items = ArrayList<PictureKt>()
                        for (doc in documentSnapshots) {
                            val e = doc.toObject(
                                    PictureKt::class.java
                            )
                            items.add(e)
                        }
                        paginationList.addAll(items)
                        arrayAdapter!!.notifyDataSetChanged()
                        if (documentSnapshots.size() > 0) lastVisible =
                                documentSnapshots.documents[documentSnapshots.size() - 1]
                    }
                    .addOnCompleteListener {
                        pullToRefresh!!.isRefreshing = false
                    }
            if (loader != null) loader!!.loadAd(
                    AdRequest.Builder().build()
            )
        }
    }

    fun loadNextItems() {
        scope.launch {
            val next = db.collection(currentType)
                    .orderBy("url")
                    .limit(30)
                    .startAfter(lastVisible)
            next.get()
                    .addOnSuccessListener { documentSnapshots ->
                        val items = ArrayList<PictureKt>()
                        for (doc in documentSnapshots) {
                            val e = doc.toObject(
                                    PictureKt::class.java
                            )
                            items.add(e)
                        }
                        if (documentSnapshots.size() > 0) lastVisible =
                                documentSnapshots.documents[documentSnapshots.size() - 1]
                        paginationList.addAll(items)
                        arrayAdapter!!.notifyDataSetChanged()
                        if (customGalleryAdapter != null) customGalleryAdapter!!.notifyDataSetChanged()
                    }
        }
    }

    fun isOnline(): Boolean {
        val cm = context?.getSystemService(AppCompatActivity.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (cm != null) {
            val netInfo = cm.activeNetworkInfo
            return netInfo != null && netInfo.isConnectedOrConnecting
        }
        return false
    }

    fun setCurrentTabStack(position: Int) {
        mNavigator!!.showFragmentStack(position)
    }

    fun setCurrentTab(position: Int) {
        mNavigator!!.showFragment(position)
    }

    fun setCurrentTab(position: Int, reset: Boolean) {
        if (mNavigator!!.getCurrentPosition() == position) return
        mNavigator!!.showFragment(position, reset)
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