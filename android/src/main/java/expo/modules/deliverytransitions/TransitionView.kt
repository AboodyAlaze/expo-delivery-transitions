package expo.modules.deliverytransitions

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/**
 * The full-screen overlay. A plain Android View (no Compose): it shows the frozen OLD screen, and later plays a
 * [Transition] between the two screenshots. Any tap skips to the end.
 */
internal class TransitionView(context: Context) : View(context) {
  private val d = resources.displayMetrics.density
  private var from: Bitmap? = null
  private var to: Bitmap? = null
  private var transition: Transition? = null
  private var progress = 0f
  private var animator: ValueAnimator? = null
  private var onFinished: (() -> Unit)? = null
  private var done = false

  private val hintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(102, 0, 0, 0) }
  private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(217, 255, 255, 255)
    textSize = 12f * d
    typeface = Typeface.DEFAULT
  }
  private val hintRect = RectF()

  init {
    isClickable = true // swallow touches: nothing underneath can be tapped while the overlay is there
  }

  /** The old screen, frozen. */
  fun freeze(bitmap: Bitmap) {
    from = bitmap
    invalidate()
  }

  fun play(toBitmap: Bitmap, t: Transition, req: TransitionRequest, finished: () -> Unit) {
    val fromBitmap = from
    if (fromBitmap == null || width == 0 || height == 0) {
      finished()
      return
    }
    to = toBitmap
    transition = t
    onFinished = finished
    done = false
    t.prepare(fromBitmap, toBitmap, width.toFloat(), height.toFloat(), req)
    val ms = if (req.durationMs > 0) req.durationMs else t.defaultMs
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = ms.toLong()
      interpolator = t.interpolator()
      addUpdateListener {
        progress = it.animatedValue as Float
        invalidate()
      }
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          finish()
        }
      })
      start()
    }
  }

  /** Jump to the end of the running transition. */
  fun skip() {
    val a = animator
    if (a != null && a.isRunning) a.end() else finish()
  }

  fun cancelNow() {
    animator?.cancel()
    finish()
  }

  private fun finish() {
    if (done) return
    done = true
    progress = 1f
    onFinished?.invoke()
  }

  override fun onDraw(canvas: Canvas) {
    val f = from ?: return
    val t = transition
    val b = to
    if (t == null || b == null) {
      canvas.drawBitmap(f, 0f, 0f, null)
      return
    }
    t.draw(canvas, f, b, progress, width.toFloat(), height.toFloat())
    if (t !is FadeTransition) drawHint(canvas)
  }

  private fun drawHint(c: Canvas) {
    val text = "Tap to skip"
    val tw = hintText.measureText(text)
    val padX = 12f * d
    val padY = 5f * d
    val bh = hintText.textSize + padY * 2f
    val bottom = height - 16f * d
    hintRect.set(width / 2f - tw / 2f - padX, bottom - bh, width / 2f + tw / 2f + padX, bottom)
    c.drawRoundRect(hintRect, bh / 2f, bh / 2f, hintBg)
    c.drawText(text, width / 2f - tw / 2f, bottom - padY - hintText.textSize * 0.2f, hintText)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_UP && transition != null) skip()
    return true
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    animator?.cancel()
  }
}
