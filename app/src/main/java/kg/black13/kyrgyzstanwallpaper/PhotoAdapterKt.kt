package kg.black13.kyrgyzstanwallpaper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Сетка картинок. После каждых adDelta картинок вставляется нативная реклама на всю ширину.
 */
class PhotoAdapterKt(
    private val items: ArrayList<PictureKt>,
    private val adDelta: Int,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val TYPE_PICTURE = 0
    private val TYPE_AD = 1

    // Строка — либо индекс картинки в items (Int), либо NativeAd
    private val rows = ArrayList<Any>()

    class PictureHolder(view: View) : RecyclerView.ViewHolder(view) {
        val picture: ImageView = view.findViewById(R.id.picture)
        val favorite: ImageButton = view.findViewById(R.id.btnFavorite)
    }

    class AdHolder(val adView: NativeAdView) : RecyclerView.ViewHolder(adView)

    /** Вызывать после изменения списка картинок или загрузки рекламы */
    fun refresh() {
        rows.clear()
        val ads = AdsKt.nativeAds
        for (i in items.indices) {
            if (i > 0 && i % adDelta == 0 && ads.isNotEmpty() && AdsKt.isEnabled()) {
                rows.add(ads[(i / adDelta - 1) % ads.size])
            }
            rows.add(i)
        }
        notifyDataSetChanged()
    }

    fun isAd(position: Int): Boolean {
        return rows.getOrNull(position) is NativeAd
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (isAd(position)) TYPE_AD else TYPE_PICTURE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_AD) {
            AdHolder(inflater.inflate(R.layout.item_ad_content, parent, false) as NativeAdView)
        } else {
            PictureHolder(inflater.inflate(R.layout.item_content, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        if (holder is AdHolder) {
            bindAd(holder.adView, row as NativeAd)
            return
        }
        holder as PictureHolder
        val index = row as Int
        val picture = items[index]
        val context = holder.itemView.context

        // Миниатюра очень лёгкая, показываем её, пока грузится сама картинка
        Glide.with(holder.picture).load(picture.url)
            .thumbnail(Glide.with(holder.picture).load(picture.urlSmall))
            .into(holder.picture)
        holder.itemView.setOnClickListener { onClick(index) }

        holder.favorite.setImageResource(favoriteIcon(FavoritesKt.isFavorite(context, picture)))
        holder.favorite.setOnClickListener {
            val added = FavoritesKt.toggle(context, picture)
            if (added) ManagerKt.getInstance()?.logEvent("favorite")
            holder.favorite.setImageResource(favoriteIcon(added))
        }
    }

    private fun favoriteIcon(favorite: Boolean): Int {
        return if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
    }

    private fun bindAd(adView: NativeAdView, ad: NativeAd) {
        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body = adView.findViewById<TextView>(R.id.ad_body)
        val icon = adView.findViewById<ImageView>(R.id.ad_icon)
        val action = adView.findViewById<Button>(R.id.ad_call_to_action)

        headline.text = ad.headline
        body.text = ad.body
        body.visibility = if (ad.body.isNullOrEmpty()) View.GONE else View.VISIBLE
        icon.setImageDrawable(ad.icon?.drawable)
        icon.visibility = if (ad.icon == null) View.GONE else View.VISIBLE
        action.text = ad.callToAction
        action.visibility = if (ad.callToAction.isNullOrEmpty()) View.GONE else View.VISIBLE

        adView.headlineView = headline
        adView.bodyView = body
        adView.iconView = icon
        adView.callToActionView = action
        adView.mediaView = adView.findViewById(R.id.ad_media)
        adView.setNativeAd(ad)
    }
}
