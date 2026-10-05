package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * Контейнер, который можно смахнуть вниз, как просмотр фото в соцсетях.
 * Горизонтальные жесты и масштабирование картинки он не трогает.
 */
class SwipeDismissLayoutKt(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    /** Можно ли сейчас начинать свайп (например, картинка не увеличена) */
    var canDismiss: () -> Boolean = { true }
    /** Доля пройденного пути от 0 до 1, чтобы затемнять фон и прятать кнопки */
    var onDrag: (Float) -> Unit = {}
    var onDismiss: () -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 20
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                // Только явное движение вниз одним пальцем
                if (ev.pointerCount == 1 && dy > touchSlop && dy > abs(dx) * 2 && canDismiss()) {
                    dragging = true
                    downY = ev.rawY
                    velocityTracker = VelocityTracker.obtain()
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return false
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> moveTo(max(0f, ev.rawY - downY))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val velocity = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                dragging = false
                val far = translationY > height * 0.2f
                if (ev.actionMasked == MotionEvent.ACTION_UP && (far || velocity > minFlingVelocity)) {
                    settle(height.toFloat()) { onDismiss() }
                } else {
                    settle(0f) {}
                }
            }
        }
        return true
    }

    private fun moveTo(y: Float) {
        translationY = y
        val progress = if (height == 0) 0f else (y / height).coerceIn(0f, 1f)
        // Картинка слегка уменьшается, пока её тянут
        scaleX = 1f - progress * 0.3f
        scaleY = scaleX
        onDrag(progress)
    }

    private fun settle(target: Float, onEnd: () -> Unit) {
        animate().translationY(target).setDuration(180)
            .setUpdateListener { moveTo(translationY) }
            .withEndAction(onEnd)
            .start()
    }
}
