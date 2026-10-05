package kg.black13.kyrgyzstanwallpaper

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.content.res.Configuration
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kg.black13.kyrgyzstanwallpaper.databinding.ActivityMainBinding

class MainActivityKt: AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Вкладки в том же порядке, что и на экране; первая — избранное
    private val tabTypes = listOf(
        FavoritesKt.TYPE, CatalogKt.FEED, CatalogKt.CARDS, "nature", "animals", "arch", "relig", "stars"
    )
    private val tabTitles = listOf(
        R.string.nav_favorites, R.string.tab_feed, R.string.tab_cards, R.string.tab_nature,
        R.string.tab_animals, R.string.tab_arch, R.string.tab_religion, R.string.tab_people
    )
    // Приложение открывается на ленте
    private val DEFAULT_TAB = 1

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Состояние экранов живёт в ManagerKt и не переживает пересоздание активити,
        // поэтому фрагменты не восстанавливаем, а открываем приложение заново
        super.onCreate(null)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ManagerKt.getInstance()?.context = this
        ManagerKt.getInstance()?.sp = getSharedPreferences("Ad", MODE_PRIVATE)
        ManagerKt.getInstance()?.db = FirebaseFirestore.getInstance()
        ManagerKt.getInstance()?.paginationList?.clear()
        checkProducts()

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) openSettings()
            true
        }

        for (i in tabTypes.indices) {
            val tab = binding.tabs.newTab()
            if (tabTypes[i] == FavoritesKt.TYPE) {
                tab.setIcon(R.drawable.ic_favorite).setContentDescription(tabTitles[i])
            } else {
                tab.setText(tabTitles[i])
            }
            binding.tabs.addTab(tab, false)
        }
        val picture = pictureFrom(intent)
        val startTab = DEFAULT_TAB
        binding.tabs.selectTab(binding.tabs.getTabAt(startTab))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showCategory(tabTypes[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        showCategory(tabTypes[startTab])
        if (picture != null) openPicture(picture)

        AdsKt.init(this, binding.adContainer)
        PrefsKt.markOpened(this)
        LanguageKt.remember(this)
        SchedulerKt.sync(this)
        askNotificationPermission()
        if (ManagerKt.reopenSettings) {
            ManagerKt.reopenSettings = false
            openSettings()
        }
    }

    /** Просмотр картинки всегда на чёрном фоне, независимо от темы */
    fun setViewerMode(viewer: Boolean) {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        binding.root.setBackgroundColor(
            if (viewer) Color.BLACK else ContextCompat.getColor(this, R.color.colorBackground)
        )
        val controller = WindowCompat.getInsetsController(window, binding.root)
        controller.isAppearanceLightStatusBars = !viewer && !night
        controller.isAppearanceLightNavigationBars = !viewer && !night
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val picture = pictureFrom(intent) ?: return
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        // Картинка из уведомления показывается первой в ленте.
        // Если открыта другая вкладка, сработает onTabSelected и загрузит ленту
        binding.tabs.selectTab(binding.tabs.getTabAt(DEFAULT_TAB))
        openPicture(picture)
    }

    // Картинка из уведомления
    private fun pictureFrom(intent: Intent?): PictureKt? {
        val url = intent?.getStringExtra(NotificationsKt.EXTRA_URL) ?: return null
        val picture = PictureKt()
        picture.url = url
        picture.urlSmall = intent.getStringExtra(NotificationsKt.EXTRA_URL_SMALL)
        picture.type = intent.getStringExtra(NotificationsKt.EXTRA_TYPE)
        return picture
    }

    // Ставит картинку первой в текущем списке и открывает её
    private fun openPicture(picture: PictureKt) {
        ManagerKt.getInstance()?.paginationList?.add(0, picture)
        ManagerKt.getInstance()?.arrayAdapter?.refresh()
        ManagerKt.getInstance()?.logEvent("open_notification")
        openGallery(0)
    }

    private fun showCategory(type: String) {
        ManagerKt.getInstance()?.paginationList?.clear()
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, ContentFragmentKt.newInstance(type))
            .commit()
    }

    fun openGallery(position: Int) {
        ManagerKt.getInstance()?.position = position
        supportFragmentManager.beginTransaction()
            .add(R.id.overlay, GalleryKt())
            .addToBackStack(null)
            .commit()
        AdsKt.onPictureOpened(this)
    }

    private fun openSettings() {
        supportFragmentManager.beginTransaction()
            .add(R.id.overlay, SettingsKt())
            .addToBackStack(null)
            .commit()
    }

    // Один раз спрашиваем разрешение на уведомления
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || PrefsKt.isNotificationsAsked(this)) return
        PrefsKt.setNotificationsAsked(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        AdsKt.resume()
    }

    override fun onPause() {
        super.onPause()
        AdsKt.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        AdsKt.destroy()
    }



    fun getUrlFromStorage(type: String?) {
        val storageRef = FirebaseStorage.getInstance().reference
        val dateRef = storageRef.child(type!!)
        val dbImages = ManagerKt.getInstance()?.db!!.collection(type)
        dateRef.listAll().addOnSuccessListener { listResult ->
            var i = 0
            while (i < listResult.items.size) {
                val p = PictureKt()
                val ref = listResult.items[i]
                val refSmall = listResult.items[i + 1]
                ref.downloadUrl.addOnSuccessListener { uri ->
                    p.type = type
                    p.url = uri.toString()
                    refSmall.downloadUrl.addOnSuccessListener { uri ->
                        p.urlSmall = uri.toString()
                        dbImages.add(p).addOnSuccessListener {
                            Log.i(
                                ContentValues.TAG,
                                "Successfully added "
                            )
                        }
                    }
                }
                i += 2
            }
        }.addOnFailureListener { e ->
            Log.i(
                ContentValues.TAG,
                "Failure to get items: $e"
            )
        }
    }



    private fun checkProducts() {
        ManagerKt.getInstance()?.billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener { billingResult: BillingResult?, list: List<Purchase?>? -> }
            .build()

        ManagerKt.getInstance()?.billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {}
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    ManagerKt.getInstance()?.billingClient!!.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
                    ) { billingResult, list ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
                        val adOff = list.any {
                            it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains("ad_off2")
                        }
                        ManagerKt.getInstance()?.setPrefRemoveAd(if (adOff) 1 else 0)
                    }
                }
            }
        })
    }
}
