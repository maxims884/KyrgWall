package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.ortiz.touchview.TouchImageView

class CustomGalleryAdapterKt(c: Context, images: ArrayList<PictureKt>):PagerAdapter() {

    private var context: Context? = null
    private lateinit var images: java.util.ArrayList<PictureKt>
    private var inflater: LayoutInflater? = null

    init {
        context = c
        this.images = images
    }

    override fun getCount(): Int {
        return images.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object` as ConstraintLayout
    }

    override fun destroyItem(container: View, position: Int, `object`: Any) {
        (container as ViewPager).removeView(`object` as ConstraintLayout?)
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val imgDisplay: ImageView
        //Button btnClose;
        inflater = context
            ?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewLayout = inflater!!.inflate(
            R.layout.layout_fullscreen_image, container,
            false
        )
        imgDisplay = viewLayout.findViewById<View>(R.id.imgDisplay) as TouchImageView
        val url = images[position].url
        val urlSmall = images[position].urlSmall
        if (url != null && !url.isEmpty()) {
            Glide.with(context!!).load(url).thumbnail(
                Glide.with(context!!).load(urlSmall).thumbnail(0.1f)
            ).into(imgDisplay)
        }
        (container as ViewPager).addView(viewLayout)
        return viewLayout
    }

}