package kg.black13.kyrgyzstanwallpaper

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Открытка с именем: пользователь пишет текст поверх картинки, двигает и растягивает
 * блок текста, выбирает шрифт и цвет и отправляет готовую картинку.
 */
class CardEditorKt : Fragment() {
    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TYPE = "type"
        private const val START_SIZE_SP = 32f
        private const val MIN_SIZE_SP = 12f
        private const val MAX_SIZE_SP = 110f

        // Шрифты из res/font; во всех есть кыргызские буквы ң, ө, ү. Первым идёт системный
        private val FONTS = listOf(
            R.font.montserrat_bold, R.font.lobster, R.font.pacifico,
            R.font.caveat_bold, R.font.playfair_bold, R.font.comfortaa_bold
        )
        private val COLORS = listOf(
            0xFFFFFFFF, 0xFF212121, 0xFFFFD54F, 0xFFFF9800, 0xFFE53935, 0xFFF06292,
            0xFFAB47BC, 0xFF1E88E5, 0xFF4FC3F7, 0xFF26A69A, 0xFF66BB6A, 0xFF8D6E63
        ).map { it.toInt() }

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
    private lateinit var box: View
    private lateinit var text: TextView
    private var imageWidth = 0
    private var imageHeight = 0
    // Блок пока не двигали — держим его внизу по центру при любом размере
    private var moved = false
    // Ширину блока задали ручкой; до этого он подстраивается под текст
    private var fixedWidth = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.card_editor, container, false)
        val url = requireArguments().getString(ARG_URL)
        frame = view.findViewById(R.id.cardFrame)
        box = view.findViewById(R.id.textBox)
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
        setupHandles(view)
        setupFonts(view)
        setupColors(view)
        view.findViewById<View>(R.id.editorShare).setOnClickListener { send(false) }
        view.findViewById<View>(R.id.editorWhatsApp).setOnClickListener { send(true) }
        return view
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
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
        if (!fixedWidth) text.maxWidth = (params.width * 0.9f).toInt()
        frame.post { placeBox() }
    }

    private fun placeBox() {
        if (!moved) {
            box.x = (frame.width - box.width) / 2f
            box.y = frame.height * 0.82f - box.height / 2f
        }
        clampBox()
    }

    // Текст не должен уезжать за края открытки. У блока вокруг текста есть поле под ручки,
    // поэтому сам блок может немного выходить за край
    private fun clampBox() {
        val margin = dp(16f)
        box.x = box.x.coerceIn(-margin, maxOf(-margin, frame.width - box.width + margin))
        box.y = box.y.coerceIn(-margin, maxOf(-margin, frame.height - box.height + margin))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupText(view: View) {
        text.hint = getString(R.string.card_text_placeholder)
        text.setHintTextColor(Color.argb(170, 255, 255, 255))
        text.typeface = Typeface.DEFAULT_BOLD
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, START_SIZE_SP)
        val input = view.findViewById<TextView>(R.id.nameInput)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                text.text = s
                box.post { placeBox() }
            }
        })

        // Блок двигается за сам текст
        var dx = 0f
        var dy = 0f
        text.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = box.x - event.rawX
                    dy = box.y - event.rawY
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    moved = true
                    box.x = event.rawX + dx
                    box.y = event.rawY + dy
                    clampBox()
                }
            }
            true
        }
    }

    private fun setTextWidth(width: Int) {
        fixedWidth = true
        text.maxWidth = Int.MAX_VALUE
        val params = text.layoutParams
        params.width = width.coerceIn(dp(60f).toInt(), frame.width)
        text.layoutParams = params
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHandles(view: View) {
        // Правая ручка меняет ширину блока: текст переносится по строкам
        var startX = 0f
        var startWidth = 0
        view.findViewById<View>(R.id.handleWidth).setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startWidth = text.width
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    setTextWidth((startWidth + event.rawX - startX).toInt())
                    box.post { clampBox() }
                }
            }
            true
        }

        // Угловая ручка растягивает блок целиком: меняется размер шрифта
        var startY = 0f
        var startSize = 0f
        view.findViewById<View>(R.id.handleScale).setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startSize = text.textSize
                    startWidth = maxOf(text.width, 1)
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    // Блок растёт вслед за пальцем: на сколько увеличилась бы ширина, во столько и шрифт
                    val delta = ((event.rawX - startX) + (event.rawY - startY)) / 2f
                    val factor = (startWidth + delta * 2f) / startWidth
                    val size = (startSize * factor).coerceIn(sp(MIN_SIZE_SP), sp(MAX_SIZE_SP))
                    text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
                    if (fixedWidth) setTextWidth((startWidth * size / startSize).toInt())
                    box.post { clampBox() }
                }
            }
            true
        }
    }

    // Обводка у выбранного шрифта и цвета
    private fun chipBackground(fill: Int, selected: Boolean, oval: Boolean): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        if (!oval) drawable.cornerRadius = dp(12f)
        drawable.setColor(fill)
        val context = requireContext()
        drawable.setStroke(
            dp(if (selected) 3f else 1.5f).toInt(),
            ContextCompat.getColor(context, if (selected) R.color.colorPrimary else R.color.colorOutline)
        )
        return drawable
    }

    private fun setupFonts(view: View) {
        val context = requireContext()
        val row = view.findViewById<LinearLayout>(R.id.fontRow)
        val typefaces = listOf(Typeface.DEFAULT_BOLD) + FONTS.mapNotNull { ResourcesCompat.getFont(context, it) }
        val fill = ContextCompat.getColor(context, R.color.colorSurfaceHigh)
        val chips = ArrayList<TextView>()
        for (typeface in typefaces) {
            val chip = TextView(context)
            chip.text = "Аа"
            chip.typeface = typeface
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            chip.gravity = Gravity.CENTER
            chip.setTextColor(ContextCompat.getColor(context, R.color.colorOnSurface))
            val params = LinearLayout.LayoutParams(dp(60f).toInt(), dp(44f).toInt())
            params.marginEnd = dp(8f).toInt()
            row.addView(chip, params)
            chips.add(chip)
            chip.setOnClickListener {
                text.typeface = typeface
                for (other in chips) other.background = chipBackground(fill, other === chip, false)
                box.post { placeBox() }
            }
        }
        for (i in chips.indices) chips[i].background = chipBackground(fill, i == 0, false)
    }

    private fun setupColors(view: View) {
        val context = requireContext()
        val row = view.findViewById<LinearLayout>(R.id.colorRow)
        val swatches = ArrayList<View>()
        for (color in COLORS) {
            val swatch = View(context)
            val params = LinearLayout.LayoutParams(dp(38f).toInt(), dp(38f).toInt())
            params.marginEnd = dp(10f).toInt()
            row.addView(swatch, params)
            swatches.add(swatch)
            swatch.setOnClickListener {
                text.setTextColor(color)
                // Тёмный текст лучше читается со светлой тенью, светлый — с тёмной
                val dark = ColorUtils.calculateLuminance(color) < 0.25
                text.setShadowLayer(6f, 0f, 2f, if (dark) 0xCCFFFFFF.toInt() else 0xCC000000.toInt())
                for (i in swatches.indices) {
                    swatches[i].background = chipBackground(COLORS[i], swatches[i] === swatch, true)
                }
            }
        }
        for (i in swatches.indices) swatches[i].background = chipBackground(COLORS[i], i == 0, true)
    }

    private fun send(toWhatsApp: Boolean) {
        val url = requireArguments().getString(ARG_URL) ?: return
        val activity = activity ?: return
        val context = activity.applicationContext
        if (frame.width == 0) return
        // Запоминаем положение текста относительно рамки, чтобы перенести его на полноразмерную картинку
        val snapshot = TextSnapshot(
            text.text.toString(), text.currentTextColor, text.textSize, text.typeface,
            box.x + text.left + text.paddingLeft, box.y + text.top + text.paddingTop,
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
        val text: String, val color: Int, val size: Float, val typeface: Typeface?,
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
