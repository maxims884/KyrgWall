package kg.black13.kyrgyzstanwallpaper

import android.content.ContentValues
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kg.black13.kyrgyzstanwallpaper.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.app_bar_main.view.*
import java.util.*

class MainActivityKt: AppCompatActivity(),NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ManagerKt.getInstance()?.context = this
        val toolbar = binding.root.toolbarView as Toolbar?
        setSupportActionBar(toolbar)
        ManagerKt.getInstance()?.sp = getSharedPreferences("Ad", MODE_PRIVATE)
        ManagerKt.getInstance()?.db = FirebaseFirestore.getInstance()
        checkProducts()
        val drawer = binding.drawerLayout
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        val navigationView = binding.navView
        navigationView.setNavigationItemSelectedListener(this)
        ManagerKt.getInstance()?.mNavigator = FragmentNavigatorKt(supportFragmentManager, FragmentAdapterKt(), R.id.content)
        ManagerKt.getInstance()?.DEFAULT_POSITION?.let {
            ManagerKt.getInstance()?.mNavigator!!.setDefaultPosition(
                it
            )
        }
        ManagerKt.getInstance()?.mNavigator!!.onCreate(savedInstanceState)
        ManagerKt.getInstance()?.mAdView = binding.root.adView
        ManagerKt.getInstance()?.mAdView1 = binding.adView1
        if (ManagerKt.getInstance()?.getPrefRemoveAd() == 0) {
            val adRequest = AdRequest.Builder().build()
            val adRequest1 = AdRequest.Builder().build()
            //AdRequest.Builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
            ManagerKt.getInstance()?.mAdView!!.loadAd(adRequest)
            ManagerKt.getInstance()?.mAdView1!!.loadAd(adRequest1)
            loadAdPage()
            ManagerKt.getInstance()?.loader = AdLoader.Builder(this, getString(R.string.ad_for_grid))
                .forNativeAd { nativeAd -> ManagerKt.getInstance()?.nativeAd = nativeAd }.build()
        }
        ManagerKt.getInstance()?.setCurrentTab(ManagerKt.getInstance()?.mNavigator!!.getCurrentPosition())
//        getUrlFromStorage("relig");
    }

    override fun onBackPressed() {
        val drawer = binding.drawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_settings) {
            ManagerKt.getInstance()?.setCurrentTabStack(FragmentIDsKt.Settings.value)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        ManagerKt.getInstance()?.mNavigator!!.clearBackStack()
        // Handle navigation view item clicks here.
        // Handle navigation view item clicks here.
        val id = item.itemId
        var i: Int  = 0
        i = ManagerKt.getInstance()?.adSequence!!
        i += 1
        ManagerKt.getInstance()?.adSequence = i

        if (id == R.id.nav_nature) {
            if (ManagerKt.getInstance()?.interstitialAd != null && ManagerKt.getInstance()?.adSequence!! % 3 == 0) {
                ManagerKt.getInstance()?.interstitialAd!!.show(this)
            }
            ManagerKt.getInstance()?.paginationList?.clear()
            ManagerKt.getInstance()?.setCurrentTab(FragmentIDsKt.NatureFragment.value, true) // Handle the camera action
        } else if (id == R.id.nav_animals) {
            if (ManagerKt.getInstance()?.interstitialAd != null && ManagerKt.getInstance()?.adSequence!! % 3 == 0) {
                ManagerKt.getInstance()?.interstitialAd!!.show(this)
            }
            ManagerKt.getInstance()?.paginationList?.clear()
            ManagerKt.getInstance()?.setCurrentTab(FragmentIDsKt.AnimalsFragment.value, true)
        } else if (id == R.id.nav_arch) {
            if (ManagerKt.getInstance()?.interstitialAd != null && ManagerKt.getInstance()?.adSequence!! % 3 == 0) {
                ManagerKt.getInstance()?.interstitialAd!!.show(this)
            }
            ManagerKt.getInstance()?.paginationList?.clear()
            ManagerKt.getInstance()?.setCurrentTab(FragmentIDsKt.ArchFragment.value, true)
        } else if (id == R.id.nav_religion) {
            if (ManagerKt.getInstance()?.interstitialAd != null && ManagerKt.getInstance()?.adSequence!! % 3 == 0) {
                ManagerKt.getInstance()?.interstitialAd!!.show(this)
            }
            ManagerKt.getInstance()?.paginationList?.clear()
            ManagerKt.getInstance()?.setCurrentTab(FragmentIDsKt.ReligionFragment.value, true)
        } else if (id == R.id.nav_star) {
            if (ManagerKt.getInstance()?.interstitialAd != null && ManagerKt.getInstance()?.adSequence!! % 3 == 0) {
                ManagerKt.getInstance()?.interstitialAd!!.show(this)
            }
            ManagerKt.getInstance()?.paginationList?.clear()
            ManagerKt.getInstance()?.setCurrentTab(FragmentIDsKt.StarsFragment.value, true)
        }

        val drawer = binding.drawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState!!)
        ManagerKt.getInstance()?.mNavigator!!.onSaveInstanceState(outState)
    }



//    fun isOnline(): Boolean {
//        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
//        if (cm != null) {
//            val netInfo = cm.activeNetworkInfo
//            return netInfo != null && netInfo.isConnectedOrConnecting
//        }
//        return false
//    }

    override fun onResume() {
        super.onResume()
        if (ManagerKt.getInstance()?.getPrefRemoveAd() == 0) {
            ManagerKt.getInstance()?.mAdView!!.resume()
            ManagerKt.getInstance()?.mAdView1!!.resume()
        } else ManagerKt.getInstance()?.loader = null
    }

    override fun onPause() {
        super.onPause()
        if (ManagerKt.getInstance()?.getPrefRemoveAd() == 0) {
            ManagerKt.getInstance()?.mAdView!!.pause()
            ManagerKt.getInstance()?.mAdView1!!.pause()
        } else ManagerKt.getInstance()?.loader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ManagerKt.getInstance()?.getPrefRemoveAd() == 0) {
            ManagerKt.getInstance()?.mAdView!!.destroy()
            ManagerKt.getInstance()?.mAdView1!!.destroy()
        } else ManagerKt.getInstance()?.loader = null
//          MainActivityKt.getInstance().onDestroy();
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
            //                for(StorageReference ref : listResult.getItems()){
//
//                    ref.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
//                        @Override
//                        public void onSuccess(Uri uri) {
//                            Picture p = new Picture();
//                            p.setType(type);
//                            p.setUrl(uri.toString());
//                            dbImages.add(p).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
//                                @Override
//                                public void onSuccess(DocumentReference documentReference) {
//                                    Log.i(TAG, "Successfully added ");
//                                }
//                            });
//                        }
//                    });
//                };
        }.addOnFailureListener { e ->
            Log.i(
                ContentValues.TAG,
                "Failure to get items: $e"
            )
        }
    }

    fun loadAdPage() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            ManagerKt.getInstance()?.AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    // The mInterstitialAd reference will be null until
                    // an ad is loaded.

                    ManagerKt.getInstance()?.interstitialAd = interstitialAd
                    interstitialAd.setFullScreenContentCallback(
                        object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                // Called when fullscreen content is dismissed.
                                // Make sure to set your reference to null so you don't
                                // show it a second time.
                                ManagerKt.getInstance()?.interstitialAd = null
                                loadAdPage()
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                // Called when fullscreen content failed to show.
                                // Make sure to set your reference to null so you don't
                                // show it a second time.
                                ManagerKt.getInstance()?.interstitialAd = null
                            }

                            override fun onAdShowedFullScreenContent() {
                                // Called when fullscreen content is shown.
                            }
                        })
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                    ManagerKt.getInstance()?.interstitialAd = null
                }
            })
    }



    private fun checkProducts() {
        ManagerKt.getInstance()?.billingClient = BillingClient.newBuilder(this).enablePendingPurchases()
            .setListener { billingResult: BillingResult?, list: List<Purchase?>? -> }
            .build()

        ManagerKt.getInstance()?.billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {}
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    ManagerKt.getInstance()?.billingClient!!.queryPurchasesAsync(
                        BillingClient.SkuType.INAPP
                    ) { billingResult, list ->
                        if (list.size == 0) ManagerKt.getInstance()?.setPrefRemoveAd(0) else {
                            for (purchase in list) {
                                if (purchase.skus[0] == "charity2") {
                                    ManagerKt.getInstance()?.setPrefRemoveAd(0)
                                }
                            }
                            for (purchase in list) {
                                if (purchase.skus[0] == "ad_off2") {
                                    ManagerKt.getInstance()?.setPrefRemoveAd(1)
                                }
                            }
                        }
                    }
                }
            }
        })
    }
}