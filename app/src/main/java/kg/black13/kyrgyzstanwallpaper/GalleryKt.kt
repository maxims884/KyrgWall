package kg.black13.kyrgyzstanwallpaper

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.nambimobile.widgets.efab.FabOption
import java.io.IOException

class GalleryKt : Fragment() {
    var currentPosition = 0
    var viewPager: ViewPager? = null
    var fabOption: FabOption? = null
    var fabOptionLock: FabOption? = null
    var fabOptionHomeLock: FabOption? = null
    var bm: Bitmap? = null
    private var selectedIndex = 0
    private var isOnce = false
    private var pageEnd = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.gallerry, container, false)
        currentPosition =  ManagerKt.getInstance()?.position!!
        viewPager = view.findViewById(R.id.pager)
        ManagerKt.getInstance()?.customGalleryAdapter =
            ManagerKt.getInstance()?.context?.let { ManagerKt.getInstance()?.paginationList?.let { it1 ->
                CustomGalleryAdapterKt(it,
                    it1
                )
            } }
        viewPager!!.adapter =  ManagerKt.getInstance()?.customGalleryAdapter
        viewPager!!.currentItem = currentPosition

        viewPager!!.addOnPageChangeListener(object : OnPageChangeListener {
            var callHappened = false
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                if (pageEnd && position == selectedIndex && !callHappened) {
                    pageEnd = false //To avoid multiple calls.
                    callHappened = true
                } else {
                    pageEnd = false
                }
            }

            override fun onPageSelected(position: Int) {
                selectedIndex = position
                isOnce = true
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (isOnce && !pageEnd && selectedIndex ==   ManagerKt.getInstance()?.customGalleryAdapter!!.count - 1) {
                    ManagerKt.getInstance()?.loadNextItems()
                    pageEnd = true
                    isOnce = false
                }
            }
        })

        fabOption = view.findViewById(R.id.fabOption)
        fabOptionLock = view.findViewById(R.id.fabOptionsLockScreen)
        fabOptionHomeLock = view.findViewById(R.id.fabOptionsLockHomeScreen)

        fabOption!!.setOnClickListener(View.OnClickListener {
            val builder = AlertDialog.Builder( ManagerKt.getInstance()?.context!!)
            builder
                .setMessage("Установить картинку в качестве изображения рабочего стола?")
                .setCancelable(false)
                .setNegativeButton(
                    "Да!"
                ) { dialog, _ ->
                    currentPosition = viewPager!!.currentItem
                    Glide.with(view).asBitmap()
                        .load(ManagerKt.getInstance()?.paginationList?.get(currentPosition)?.url)
                        .into(object : SimpleTarget<Bitmap?>() {
                            override fun onResourceReady(
                                resource: Bitmap,
                                transition: Transition<in Bitmap?>?
                            ) {
                                bm = resource
                                val myWallpaperManager =
                                    WallpaperManager.getInstance(ManagerKt.getInstance()?.context)
                                try {
                                    myWallpaperManager.setBitmap(bm)
                                    Toast.makeText(
                                        ManagerKt.getInstance()?.context,
                                        "Обои успешно установлены!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: IOException) {
                                    Toast.makeText(
                                        ManagerKt.getInstance()?.context,
                                        e.toString(),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    e.printStackTrace()
                                }
                            }
                        })
                    dialog.cancel()
                }.setPositiveButton(
                    "Отмена"
                ) { dialog, id -> dialog.cancel() }
            val alert = builder.create()
            alert.show()
        })

        fabOptionLock!!.setOnClickListener(View.OnClickListener {
            val builder = AlertDialog.Builder(ManagerKt.getInstance()?.context!!)
            builder
                .setMessage("Установить картинку в качестве изображения экрана блокировки?")
                .setCancelable(false)
                .setNegativeButton(
                    "Да!"
                ) { dialog, id ->
                    currentPosition = viewPager!!.currentItem
                    Glide.with(view).asBitmap()
                        .load(ManagerKt.getInstance()?.paginationList?.get(currentPosition)?.url)
                        .into(object : SimpleTarget<Bitmap?>() {
                            override fun onResourceReady(
                                resource: Bitmap,
                                transition: Transition<in Bitmap?>?
                            ) {
                                bm = resource
                                val myWallpaperManager =
                                    WallpaperManager.getInstance(ManagerKt.getInstance()?.context)
                                if (Build.VERSION.SDK_INT >= 24) {
                                    if (myWallpaperManager.isSetWallpaperAllowed) {
                                        try {
                                            myWallpaperManager.setBitmap(
                                                bm,
                                                null,
                                                false,
                                                WallpaperManager.FLAG_LOCK
                                            )
                                            Toast.makeText(
                                                ManagerKt.getInstance()?.context,
                                                "Обои успешно установлены!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: IOException) {
                                            Toast.makeText(
                                                ManagerKt.getInstance()?.context,
                                                e.toString(),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            e.printStackTrace()
                                        }
                                    } else {
                                        Toast.makeText(
                                            ManagerKt.getInstance()?.context,
                                            "Ваше устройство не поддерживает изменение изображения экрана блокировки!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        ManagerKt.getInstance()?.context,
                                        "Не поддерживается на вашем устройстве",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        })
                    dialog.cancel()
                }.setPositiveButton(
                    "Отмена"
                ) { dialog, _ -> dialog.cancel() }
            val alert = builder.create()
            alert.show()
        })

        fabOptionHomeLock!!.setOnClickListener(View.OnClickListener {
            val builder = AlertDialog.Builder(ManagerKt.getInstance()?.context!!)
            builder
                .setMessage("Установить картинку в качестве изображения рабочего стола и экрана блокировки?")
                .setCancelable(false)
                .setNegativeButton(
                    "Да!"
                ) { dialog, id ->
                    currentPosition = viewPager!!.currentItem
                    Glide.with(view).asBitmap()
                        .load(ManagerKt.getInstance()?.paginationList?.get(currentPosition)?.url)
                        .into(object : SimpleTarget<Bitmap?>() {
                            override fun onResourceReady(
                                resource: Bitmap,
                                transition: Transition<in Bitmap?>?
                            ) {
                                bm = resource
                                val myWallpaperManager =
                                    WallpaperManager.getInstance(ManagerKt.getInstance()?.context)
                                try {
                                    myWallpaperManager.setBitmap(bm)
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }
                                if (Build.VERSION.SDK_INT >= 24) {
                                    if (myWallpaperManager.isSetWallpaperAllowed) {
                                        try {
                                            myWallpaperManager.setBitmap(
                                                bm,
                                                null,
                                                false,
                                                WallpaperManager.FLAG_LOCK
                                            )
                                            Toast.makeText(
                                                ManagerKt.getInstance()?.context,
                                                "Обои успешно установлены!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: IOException) {
                                            Toast.makeText(
                                                ManagerKt.getInstance()?.context,
                                                e.toString(),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            e.printStackTrace()
                                        }
                                    } else {
                                        Toast.makeText(
                                            ManagerKt.getInstance()?.context,
                                            "Ваше устройство не поддерживает изменение изображения экрана блокировки!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        ManagerKt.getInstance()?.context,
                                        "Не поддерживается на вашем устройстве",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        })
                    dialog.cancel()
                }.setPositiveButton(
                    "Отмена"
                ) { dialog, _ -> dialog.cancel() }
            val alert = builder.create()
            alert.show()
        })
        return view
    }
}