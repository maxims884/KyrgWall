package kg.black13.kyrgyzstanwallpaper

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.*

class PhotoAdapterKt(data: ArrayList<PictureKt>?, prb: ProgressBar) : BaseAdapter() {
    private var items: ArrayList<PictureKt>? = null
    var pr: ProgressBar? = null
    private val LIST_AD_DELTA = 9

    init {
        items = data
        pr =prb
    }

    override fun getCount(): Int {
        var additionalContent = 0
        if (items?.size!! > 0 && LIST_AD_DELTA > 0 && items!!.size > LIST_AD_DELTA && ManagerKt.getInstance()?.nativeAd != null) {
            additionalContent = items!!.size / LIST_AD_DELTA
        }
        return items!!.size + additionalContent
    }

    override fun getItem(position: Int): Any {
        return items!![getRealPosition(position)]
    }

    private fun getRealPosition(position: Int): Int {
        return if (LIST_AD_DELTA == 0 || ManagerKt.getInstance()?.nativeAd == null) {
            position
        } else {
            position - position / LIST_AD_DELTA
        }
    }
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var result: View? = null
        return if (position % LIST_AD_DELTA == 0 && position != 0 && ManagerKt.getInstance()?.nativeAd != null) {
                val adView = LayoutInflater.from(ManagerKt.getInstance()?.context)
                    .inflate(R.layout.item_ad_content, null) as NativeAdView
                val media: MediaView = adView.findViewById(R.id.ad_media)
                media.setMediaContent(Objects.requireNonNull(ManagerKt.getInstance()?.nativeAd!!.mediaContent))
                adView.mediaView = media
                adView.setNativeAd(ManagerKt.getInstance()?.nativeAd!!)
                adView
        } else {
            result = LayoutInflater.from(ManagerKt.getInstance()?.context).inflate(R.layout.item_content, parent, false)
            val photo = result.findViewById<ImageView>(R.id.picture)
            pr?.visibility = View.INVISIBLE
            if (items!!.size > 0) {
                val url = items!![getRealPosition(position)].url
                val urlSmall = items!![getRealPosition(position)].urlSmall
                    if (url != null && url.isNotEmpty()) {

                        Glide.with(ManagerKt.getInstance()?.context!!).load(url).
                            thumbnail(
                                Glide.with(ManagerKt.getInstance()?.context!!)
                                        .load(urlSmall)
                                        .listener(object : RequestListener<Drawable?> {
                                            override fun onLoadFailed(
                                                    e: GlideException?,
                                                    model: Any,
                                                    target: Target<Drawable?>,
                                                    isFirstResource: Boolean
                                            ): Boolean {
                                                //on load failed
                                                return false
                                            }

                                            override fun onResourceReady(
                                                    resource: Drawable?,
                                                    model: Any,
                                                    target: Target<Drawable?>,
                                                    dataSource: DataSource,
                                                    isFirstResource: Boolean
                                            ): Boolean {
                                                //on load success
                                                pr?.visibility = View.INVISIBLE
                                                return false
                                            }
                                        })
                                        .thumbnail(0.01f)
                        ).
                        listener(object : RequestListener<Drawable?> {
                            override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any,
                                    target: Target<Drawable?>,
                                    isFirstResource: Boolean
                            ): Boolean {
                                //on load failed
                                return false
                            }

                            override fun onResourceReady(
                                    resource: Drawable?,
                                    model: Any,
                                    target: Target<Drawable?>,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                            ): Boolean {
                                //on load success
                                pr?.visibility = View.INVISIBLE
                                return false
                            }
                        }).into(photo)
                    }
                }
                photo.setOnClickListener {
                    ManagerKt.getInstance()?.position = getRealPosition(position)
                    ManagerKt.getInstance()?.setCurrentTabStack(FragmentIDsKt.Gallery.value)
                }
//            }
            result
        }
    }
}