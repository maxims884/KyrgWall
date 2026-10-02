package kg.black13.kyrgyzstanwallpaper

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.android.billingclient.api.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.*

class SettingsKt : Fragment() {

    private lateinit var btnBuy: Button
    private lateinit var btnDonate: Button
    private lateinit var switchWeekly: MaterialSwitch
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setWeekly(true)
            } else {
                switchWeekly.isChecked = false
                Toast.makeText(requireContext(), R.string.notifications_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.settings, container, false)
        val btnRate = view.findViewById(R.id.btnRate) as Button
        btnBuy = view.findViewById(R.id.btn2)
        btnDonate = view.findViewById(R.id.btn3)

        view.findViewById<MaterialToolbar>(R.id.settingsToolbar)
            .setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        setupAutoWallpaper(view)
        setupWeekly(view)

        btnRate.setOnClickListener {
            val appPackageName = ManagerKt.getInstance()?.context?.packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
            } catch (err: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }
        }

        ManagerKt.getInstance()?.billingClient = BillingClient.newBuilder( ManagerKt.getInstance()?.context!!).setListener(PurchasesUpdatedListener { billingResult, mutableList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && mutableList != null) {
                for (purchase in mutableList) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        verifyPayment(purchase)
                    }
                }
            }
        })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build()

        connectToGooglePlayBilling()
        return  view
    }

    private fun setupAutoWallpaper(view: View) {
        val context = requireContext()
        val switchAuto = view.findViewById<MaterialSwitch>(R.id.switchAuto)
        val source = view.findViewById<MaterialButtonToggleGroup>(R.id.autoSource)

        switchAuto.isChecked = PrefsKt.isAutoWallpaper(context)
        source.check(if (PrefsKt.isAutoFromFavorites(context)) R.id.sourceFavorites else R.id.sourceAll)
        setSourceEnabled(source, switchAuto.isChecked)

        switchAuto.setOnCheckedChangeListener { _, checked ->
            PrefsKt.setAutoWallpaper(context, checked)
            setSourceEnabled(source, checked)
            SchedulerKt.sync(context)
            if (checked) {
                ManagerKt.getInstance()?.logEvent("auto_wallpaper_on")
                warnIfNoFavorites()
            }
        }
        source.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                PrefsKt.setAutoFromFavorites(context, checkedId == R.id.sourceFavorites)
                warnIfNoFavorites()
            }
        }
    }

    private fun setSourceEnabled(source: MaterialButtonToggleGroup, enabled: Boolean) {
        for (i in 0 until source.childCount) source.getChildAt(i).isEnabled = enabled
    }

    private fun warnIfNoFavorites() {
        val context = requireContext()
        if (PrefsKt.isAutoWallpaper(context) && PrefsKt.isAutoFromFavorites(context)
            && FavoritesKt.getAll(context).isEmpty()
        ) {
            Toast.makeText(context, R.string.settings_auto_no_favorites, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupWeekly(view: View) {
        val context = requireContext()
        switchWeekly = view.findViewById(R.id.switchWeekly)
        switchWeekly.isChecked = PrefsKt.isWeekly(context) && hasNotificationPermission()
        switchWeekly.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasNotificationPermission()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                setWeekly(checked)
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setWeekly(enabled: Boolean) {
        PrefsKt.setWeekly(requireContext(), enabled)
        SchedulerKt.sync(requireContext())
    }

    private fun connectToGooglePlayBilling() {
        ManagerKt.getInstance()?.billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    getProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                connectToGooglePlayBilling()
            }
        })
    }

    private fun getProductDetails() {
        val products: MutableList<QueryProductDetailsParams.Product> = ArrayList()
        for (id in listOf("ad_off2", "charity2")) {
            products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build())
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products)
        ManagerKt.getInstance()?.billingClient?.queryProductDetailsAsync(params.build()
        ) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                (ManagerKt.getInstance()?.context as Activity).runOnUiThread {
                    for (details in result.productDetailsList) {
                        when (details.productId) {
                            "ad_off2" -> bindProduct(btnBuy, details)
                            "charity2" -> bindProduct(btnDonate, details)
                        }
                    }
                }
            }
        }
    }

    private fun bindProduct(button: Button, details: ProductDetails) {
        // Раздел покупок скрыт, пока Google Play не отдал товары
        view?.findViewById<View>(R.id.purchasesTitle)?.visibility = View.VISIBLE
        button.visibility = View.VISIBLE
        button.text = details.title
        button.setOnClickListener{
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
            ManagerKt.getInstance()?.billingClient!!.launchBillingFlow(ManagerKt.getInstance()?.context as Activity, billingFlowParams)
        }
    }
    private fun verifyPayment(purchase: Purchase){
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ManagerKt.getInstance()?.billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchase.products.contains("ad_off2")) {
                        ManagerKt.getInstance()?.setPrefRemoveAd(1)
                    }
                }
            }
        }
    }
}
