package kg.black13.kyrgyzstanwallpaper

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.appbar.MaterialToolbar
import com.ortiz.touchview.TouchImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Выбор области обоев. Широкая картинка не помещается на экран телефона целиком,
 * поэтому пользователь двигает и увеличивает её в рамке с пропорциями экрана:
 * обоями становится то, что видно в рамке.
 */
class WallpaperCropKt : Fragment() {
    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TARGET = "target"
        // Картинки шире этого соотношения сторон (ширина / высота) предлагаем обрезать вручную.
        // У более узких по бокам теряется немного, их ставим сразу
        const val WIDE_ASPECT = 0.8f

        fun newInstance(url: String, target: Int): WallpaperCropKt {
            val fragment = WallpaperCropKt()
            val args = Bundle()
            args.putString(ARG_URL, url)
            args.putInt(ARG_TARGET, target)
            fragment.arguments = args
            return fragment
        }
    }

    private val scope = MainScope()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.wallpaper_crop, container, false)
        val url = requireArguments().getString(ARG_URL)!!
        val target = requireArguments().getInt(ARG_TARGET)
        val area = view.findViewById<View>(R.id.cropArea)
        val frame = view.findViewById<View>(R.id.cropFrame)
        val image = view.findViewById<TouchImageView>(R.id.cropImage)
        val progress = view.findViewById<View>(R.id.cropProgress)
        val apply = view.findViewById<View>(R.id.cropApply)

        view.findViewById<MaterialToolbar>(R.id.cropToolbar)
            .setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.cropCancel).setOnClickListener { parentFragmentManager.popBackStack() }

        frame.clipToOutline = true
        apply.isEnabled = false
        area.post { fitFrame(area, frame) }

        Glide.with(this).load(url).into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                progress.visibility = View.GONE
                image.setImageDrawable(resource)
                apply.isEnabled = true
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), R.string.image_load_failed, Toast.LENGTH_SHORT).show()
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
        })

        apply.setOnClickListener {
            apply.isEnabled = false
            progress.visibility = View.VISIBLE
            setWallpaper(url, target, RectF(image.zoomedRect))
        }
        return view
    }

    // Рамка повторяет пропорции всего экрана телефона, включая строку состояния и панель навигации
    private fun fitFrame(area: View, frame: View) {
        if (!isAdded) return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        requireActivity().windowManager.defaultDisplay.getRealMetrics(metrics)
        val screenAspect = metrics.widthPixels / metrics.heightPixels.toFloat()
        val availableWidth = area.width - area.paddingLeft - area.paddingRight
        val height = minOf(area.height.toFloat(), availableWidth / screenAspect)
        val params = frame.layoutParams
        params.height = height.toInt()
        params.width = (height * screenAspect).toInt()
        frame.layoutParams = params
    }

    /** visible — видимая в рамке часть картинки в долях от 0 до 1 */
    private fun setWallpaper(url: String, target: Int, visible: RectF) {
        val activity = requireActivity()
        val context = activity.applicationContext
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                try {
                    val source = Glide.with(context).asBitmap().load(url).submit().get()
                    PictureActionsKt.setWallpaper(context, crop(source, visible), target)
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            Toast.makeText(
                context,
                if (done) R.string.wallpaper_set else R.string.wallpaper_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (isAdded) parentFragmentManager.popBackStack()
            if (done) {
                ManagerKt.getInstance()?.logEvent("set_wallpaper_cropped")
                AdsKt.afterAction(activity)
            }
        }
    }

    private fun crop(source: Bitmap, visible: RectF): Bitmap {
        val left = (visible.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (visible.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (visible.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (visible.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }
}
