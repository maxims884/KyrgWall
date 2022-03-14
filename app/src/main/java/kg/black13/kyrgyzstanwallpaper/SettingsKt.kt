package kg.black13.kyrgyzstanwallpaper

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.android.billingclient.api.*
import java.util.*

class SettingsKt : Fragment() {

    private lateinit var btnBuy: Button
    private lateinit var btnDonate: Button
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.settings, container, false)
        val btnRate = view.findViewById(R.id.btnRate) as Button
        btnBuy = view.findViewById(R.id.btn2)
        btnDonate = view.findViewById(R.id.btn3)

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
                .enablePendingPurchases()
                .build()

        connectToGooglePlayBilling()
        return  view
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
        val productIds: MutableList<String> = ArrayList()
        productIds.add("ad_off2")
        productIds.add("charity2")
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(productIds).setType(BillingClient.SkuType.INAPP)
        ManagerKt.getInstance()?.billingClient?.querySkuDetailsAsync(params.build()
        ) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
                btnBuy.text = list[0].title
                btnBuy.setOnClickListener{
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(list[0])
                        .build()
                    ManagerKt.getInstance()?.billingClient!!.launchBillingFlow(ManagerKt.getInstance()?.context as Activity, billingFlowParams)
                }
                btnDonate.text = list[1].title
                btnDonate.setOnClickListener{
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(list[1])
                        .build()
                    ManagerKt.getInstance()?.billingClient!!.launchBillingFlow(  ManagerKt.getInstance()?.context as Activity, billingFlowParams)
                }
            }
        }
    }
    private fun verifyPayment(purchase: Purchase){
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ManagerKt.getInstance()?.billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        ManagerKt.getInstance()?.setPrefRemoveAd(1)
                    }
                }
            }
        }
    }
}