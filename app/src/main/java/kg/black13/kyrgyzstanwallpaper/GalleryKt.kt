package kg.black13.kyrgyzstanwallpaper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.ortiz.touchview.TouchImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryKt : Fragment() {
    var viewPager: ViewPager? = null
    private var btnFavorite: MaterialButton? = null
    private var btnSet: MaterialButton? = null
    private val scope = MainScope()
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) savePicture()
            else Toast.makeText(requireContext(), R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.gallerry, container, false)
        val manager = ManagerKt.getInstance()!!
        viewPager = view.findViewById(R.id.pager)
        manager.customGalleryAdapter = CustomGalleryAdapterKt(requireContext(), manager.paginationList)
        viewPager!!.adapter = manager.customGalleryAdapter
        viewPager!!.currentItem = manager.position ?: 0

        viewPager!!.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                updateButtons()
                // Дошли до последней картинки — подгружаем следующую страницу
                if (position == manager.customGalleryAdapter!!.count - 1) manager.loadNextItems()
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })

        btnFavorite = view.findViewById(R.id.btnFavorite)
        btnSet = view.findViewById(R.id.btnSet)
        updateButtons()
        setupSwipeDismiss(view)
        (activity as MainActivityKt).setViewerMode(true)

        view.findViewById<View>(R.id.btnBack).setOnClickListener { parentFragmentManager.popBackStack() }
        // У открытки главная кнопка отправляет её в WhatsApp, у обоев — ставит на экран
        btnSet!!.setOnClickListener { if (isCard()) sharePicture(true) else chooseWallpaperTarget() }
        view.findViewById<View>(R.id.btnShare).setOnClickListener { sharePicture(false) }

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                savePicture()
            }
        }

        btnFavorite!!.setOnClickListener {
            val picture = currentPicture() ?: return@setOnClickListener
            val added = FavoritesKt.toggle(requireContext(), picture)
            if (added) manager.logEvent("favorite")
            Toast.makeText(
                requireContext(),
                if (added) R.string.favorite_added else R.string.favorite_removed,
                Toast.LENGTH_SHORT
            ).show()
            updateButtons()
            // Сердечки в сетке под просмотром должны совпадать
            manager.arrayAdapter?.refresh()
        }
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ManagerKt.getInstance()?.customGalleryAdapter = null
        (activity as MainActivityKt).setViewerMode(false)
    }

    // Свайп вниз закрывает просмотр: фон светлеет, кнопки исчезают
    private fun setupSwipeDismiss(view: View) {
        val swipe = view.findViewById<SwipeDismissLayoutKt>(R.id.swipeDismiss)
        val root = view.findViewById<View>(R.id.galleryRoot)
        val controls = listOf<View>(view.findViewById(R.id.btnBack), view.findViewById(R.id.actionBar))
        swipe.canDismiss = {
            // Увеличенную картинку пользователь двигает, а не закрывает
            val page = viewPager!!.findViewWithTag<View>(viewPager!!.currentItem)
            page?.findViewById<TouchImageView>(R.id.imgDisplay)?.isZoomed != true
        }
        swipe.onDrag = { progress ->
            root.background.mutate().alpha =((1f - progress) * 255).toInt()
            for (control in controls) control.alpha = (1f - progress * 3).coerceAtLeast(0f)
        }
        swipe.onDismiss = { if (isAdded) parentFragmentManager.popBackStack() }
    }

    private fun currentPicture(): PictureKt? {
        return ManagerKt.getInstance()?.paginationList?.getOrNull(viewPager!!.currentItem)
    }

    private fun isCard(): Boolean {
        return currentPicture()?.type == CatalogKt.CARDS
    }

    private fun updateButtons() {
        val picture = currentPicture() ?: return
        val favorite = FavoritesKt.isFavorite(requireContext(), picture)
        btnFavorite?.setIconResource(if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)

        val card = isCard()
        btnSet?.setText(if (card) R.string.send_whatsapp else R.string.set_wallpaper)
        btnSet?.setIconResource(if (card) R.drawable.ic_share else R.drawable.ic_wallpaper)
        btnSet?.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(), if (card) R.color.colorWhatsApp else R.color.colorPrimary
        )
    }

    private fun chooseWallpaperTarget() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.dialog_set_wallpaper, null)
        val options = mapOf(
            R.id.optionHome to PictureActionsKt.TARGET_HOME,
            R.id.optionLock to PictureActionsKt.TARGET_LOCK,
            R.id.optionBoth to PictureActionsKt.TARGET_BOTH
        )
        for ((id, target) in options) {
            sheet.findViewById<View>(id).setOnClickListener {
                dialog.dismiss()
                setWallpaper(target)
            }
        }
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun setWallpaper(target: Int) {
        val url = currentPicture()?.url ?: return
        val context = requireContext().applicationContext
        if (target != PictureActionsKt.TARGET_HOME && !PictureActionsKt.isLockScreenSupported(context)) {
            Toast.makeText(context, R.string.lock_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                try {
                    val bitmap = Glide.with(context).asBitmap().load(url).submit().get()
                    PictureActionsKt.setWallpaper(context, bitmap, target)
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
            if (done) {
                ManagerKt.getInstance()?.logEvent("set_wallpaper")
                activity?.let { AdsKt.afterAction(it) }
            }
        }
    }

    private fun savePicture() {
        val url = currentPicture()?.url ?: return
        val context = requireContext().applicationContext
        PictureActionsKt.loadFile(context, url) { file ->
            if (file == null) {
                Toast.makeText(context, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
                return@loadFile
            }
            scope.launch {
                val saved = withContext(Dispatchers.IO) { PictureActionsKt.saveToGallery(context, file, url) }
                Toast.makeText(
                    context,
                    if (saved) R.string.image_saved else R.string.image_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
                if (saved) {
                    ManagerKt.getInstance()?.logEvent("save_picture")
                    activity?.let { AdsKt.afterAction(it) }
                }
            }
        }
    }

    private fun sharePicture(toWhatsApp: Boolean) {
        val url = currentPicture()?.url ?: return
        val context = requireContext().applicationContext
        PictureActionsKt.loadFile(context, url) { file ->
            if (file == null) {
                Toast.makeText(context, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
                return@loadFile
            }
            scope.launch {
                val intent = withContext(Dispatchers.IO) { PictureActionsKt.shareIntent(context, file, url) }
                val activity = activity
                if (intent == null) {
                    Toast.makeText(context, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
                } else if (activity != null) {
                    ManagerKt.getInstance()?.logEvent("share_picture")
                    // Если подошла очередь рекламы, окно отправки откроется после неё
                    AdsKt.afterAction(activity) { PictureActionsKt.share(activity, intent, toWhatsApp) }
                }
            }
        }
    }
}
