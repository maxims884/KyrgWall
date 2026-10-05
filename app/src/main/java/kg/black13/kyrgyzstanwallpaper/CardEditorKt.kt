package kg.black13.kyrgyzstanwallpaper

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Открытка с именем: пользователь пишет текст поверх картинки, двигает его пальцем,
 * выбирает цвет и размер и отправляет готовую картинку.
 */
class CardEditorKt : Fragment() {
    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TYPE = "type"

        fun newInstance(picture: PictureKt): CardEditorKt {
            val fragment = CardEditorKt()
            val args = Bundle()
            args.putString(ARG_URL, picture.url)
            args.putString(ARG_TYPE, picture.type)
            fragment.arguments = args
            return fragment
        }
    }

    private val scope = MainScope()
    private lateinit var frame: FrameLayout
    private lateinit var text: TextView
    private var imageWidth = 0
    private var imageHeight = 0
    // Текст пока не двигали — держим его внизу по центру при любом размере
    private var moved = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.card_editor, container, false)
        val url = requireArguments().getString(ARG_URL)
        frame = view.findViewById(R.id.cardFrame)
        text = view.findViewById(R.id.cardText)
        val image = view.findViewById<ImageView>(R.id.cardImage)
        val previewArea = view.findViewById<View>(R.id.previewArea)
        val progress = view.findViewById<View>(R.id.editorProgress)

        view.findViewById<MaterialToolbar>(R.id.editorToolbar)
            .setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        Glide.with(this).load(url).into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                progress.visibility = View.GONE
                imageWidth = resource.intrinsicWidth
                imageHeight = resource.intrinsicHeight
                image.setImageDrawable(resource)
                previewArea.post { fitFrame(previewArea) }
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), R.string.image_load_failed, Toast.LENGTH_SHORT).show()
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
        })

        setupText(view)
        setupColors(view)
        view.findViewById<View>(R.id.editorShare).setOnClickListener { send(false) }
        view.findViewById<View>(R.id.editorWhatsApp).setOnClickListener { send(true) }
        return view
    }

    // Рамка открытки повторяет пропорции картинки и вписывается в свободное место
    private fun fitFrame(area: View) {
        if (imageWidth <= 0 || imageHeight <= 0 || !isAdded) return
        val availableWidth = area.width - area.paddingLeft - area.paddingRight
        val availableHeight = area.height - area.paddingTop - area.paddingBottom
        val scale = minOf(availableWidth / imageWidth.toFloat(), availableHeight / imageHeight.toFloat())
        val params = frame.layoutParams
        params.width = (imageWidth * scale).toInt()
        params.height = (imageHeight * scale).toInt()
        frame.layoutParams = params
        text.maxWidth = (params.width * 0.9f).toInt()
        frame.post { placeText() }
    }

    private fun placeText() {
        if (moved) {
            clampText()
            return
        }
        text.x = (frame.width - text.width) / 2f
        text.y = frame.height * 0.85f - text.height / 2f
    }

    // Текст не должен уезжать за края открытки
    private fun clampText() {
        text.x = text.x.coerceIn(0f, maxOf(0f, (frame.width - text.width).toFloat()))
        text.y = text.y.coerceIn(0f, maxOf(0f, (frame.height - text.height).toFloat()))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupText(view: View) {
        text.hint = getString(R.string.card_text_placeholder)
        text.setHintTextColor(Color.argb(170, 255, 255, 255))
        val input = view.findViewById<TextView>(R.id.nameInput)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                text.text = s
                text.post { placeText() }
            }
        })

        val slider = view.findViewById<Slider>(R.id.sizeSlider)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, slider.value)
        slider.addOnChangeListener { _, value, _ ->
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
            text.post { placeText() }
        }

        var dx = 0f
        var dy = 0f
        text.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = v.x - event.rawX
                    dy = v.y - event.rawY
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    moved = true
                    v.x = event.rawX + dx
                    v.y = event.rawY + dy
                    clampText()
                }
            }
            true
        }
    }

    private fun setupColors(view: View) {
        val colors = mapOf(
            R.id.colorWhite to Color.WHITE,
            R.id.colorYellow to Color.rgb(255, 213, 79),
            R.id.colorRed to Color.rgb(229, 57, 53),
            R.id.colorDark to Color.rgb(33, 33, 33)
        )
        for ((id, color) in colors) {
            val swatch = view.findViewById<View>(id)
            swatch.background.mutate().setTint(color)
            swatch.setOnClickListener {
                text.setTextColor(color)
                // Тёмный текст лучше читается со светлой тенью
                text.setShadowLayer(6f, 0f, 2f, if (color == colors[R.id.colorDark]) 0xCCFFFFFF.toInt() else 0xCC000000.toInt())
            }
        }
    }

    private fun send(toWhatsApp: Boolean) {
        val url = requireArguments().getString(ARG_URL) ?: return
        val activity = activity ?: return
        val context = activity.applicationContext
        if (frame.width == 0) return
        // Запоминаем положение текста относительно рамки, чтобы перенести его на полноразмерную картинку
        val snapshot = TextSnapshot(
            text.text.toString(), text.currentTextColor, text.textSize, text.typeface,
            text.x + text.paddingLeft, text.y + text.paddingTop,
            (text.width - text.paddingLeft - text.paddingRight).toFloat(),
            text.shadowRadius, text.shadowDy, text.shadowColor, frame.width.toFloat()
        )
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val source = Glide.with(context).asBitmap().load(url).submit().get()
                    render(source, snapshot, File(context.cacheDir, "card.jpg"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            val intent = file?.let { withContext(Dispatchers.IO) { PictureActionsKt.shareIntent(context, it, "card.jpg") } }
            if (intent == null) {
                Toast.makeText(context, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            ManagerKt.getInstance()?.logEvent(if (snapshot.text.isBlank()) "share_card" else "share_card_with_name")
            AdsKt.afterAction(activity) { PictureActionsKt.share(activity, intent, toWhatsApp) }
        }
    }

    private class TextSnapshot(
        val text: String, val color: Int, val size: Float, val typeface: android.graphics.Typeface?,
        val x: Float, val y: Float, val width: Float,
        val shadowRadius: Float, val shadowDy: Float, val shadowColor: Int, val frameWidth: Float
    )

    // Рисует текст на картинке в том же месте и того же размера относительно открытки, что и в превью
    private fun render(source: Bitmap, s: TextSnapshot, target: File): File {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        if (s.text.isNotBlank()) {
            val scale = bitmap.width / s.frameWidth
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
            paint.color = s.color
            paint.textSize = s.size * scale
            paint.typeface = s.typeface
            paint.setShadowLayer(s.shadowRadius * scale, 0f, s.shadowDy * scale, s.shadowColor)
            val width = maxOf(1, (s.width * scale).toInt())
            val layout = StaticLayout.Builder.obtain(s.text, 0, s.text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            val canvas = Canvas(bitmap)
            canvas.translate(s.x * scale, s.y * scale)
            layout.draw(canvas)
        }
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return target
    }
}
