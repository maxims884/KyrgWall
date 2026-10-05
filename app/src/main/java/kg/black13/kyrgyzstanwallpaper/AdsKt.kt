package kg.black13.kyrgyzstanwallpaper

import android.app.Activity
import android.os.SystemClock
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd

object AdsKt {
    // Межстраничная после каждого 3-го действия с картинкой (установка, сохранение, отправка)
    private const val ACTIONS_PER_AD = 3
    // ...и на каждое 6-е открытие картинки, но не чаще раза в минуту
    private const val OPENS_PER_AD = 6
    private const val MIN_INTERVAL_MS = 60_000L
    private const val NATIVE_ADS = 3

    val nativeAds = ArrayList<NativeAd>()
    private var bannerView: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var actionCount = 0
    private var openCount = 0
    private var lastShown = 0L

    fun isEnabled(): Boolean {
        return ManagerKt.getInstance()?.getPrefRemoveAd() == 0
    }

    fun init(activity: Activity, bannerContainer: ViewGroup) {
        if (!isEnabled()) return
        MobileAds.initialize(activity)
        loadBanner(activity, bannerContainer)
        loadInterstitial(activity)
        AdLoader.Builder(activity, activity.getString(R.string.ad_for_grid))
            .forNativeAd { nativeAd ->
                nativeAds.add(nativeAd)
                ManagerKt.getInstance()?.arrayAdapter?.refresh()
            }
            .build()
            .loadAds(AdRequest.Builder().build(), NATIVE_ADS)
    }

    // Адаптивный баннер на всю ширину экрана
    private fun loadBanner(activity: Activity, container: ViewGroup) {
        val metrics = activity.resources.displayMetrics
        val adView = AdView(activity)
        adView.adUnitId = activity.getString(R.string.banner_ad_unit_id)
        adView.setAdSize(
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                activity, (metrics.widthPixels / metrics.density).toInt()
            )
        )
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
        bannerView = adView
    }

    private fun loadInterstitial(activity: Activity) {
        InterstitialAd.load(
            activity,
            activity.getString(R.string.ad_between_page),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                }
            })
    }

    /** Вызывать после действия с картинкой. onDone выполнится сразу или после закрытия рекламы */
    fun afterAction(activity: Activity, onDone: () -> Unit = {}) {
        actionCount++
        if (actionCount % ACTIONS_PER_AD == 0) show(activity, onDone) else onDone()
    }

    fun onPictureOpened(activity: Activity) {
        openCount++
        if (openCount % OPENS_PER_AD == 0 && SystemClock.elapsedRealtime() - lastShown > MIN_INTERVAL_MS) {
            show(activity) {}
        }
    }

    private fun show(activity: Activity, onDone: () -> Unit) {
        val ad = interstitialAd
        if (ad == null || !isEnabled() || activity.isFinishing) {
            onDone()
            return
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                loadInterstitial(activity)
                onDone()
            }
        }
        lastShown = SystemClock.elapsedRealtime()
        ad.show(activity)
    }

    fun resume() {
        bannerView?.resume()
    }

    fun pause() {
        bannerView?.pause()
    }

    fun destroy() {
        bannerView?.destroy()
        bannerView = null
        interstitialAd = null
        for (ad in nativeAds) ad.destroy()
        nativeAds.clear()
    }
}
