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
import android.view.animation.LinearInterpolator

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
  private var waitingT: Transition? = null
  private var waitAnimator: ValueAnimator? = null
  private var waitMs = 0L

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

  /** The frozen old screen (used to notice when the new one is really on screen). */
  fun frozen(): Bitmap? = from

  /** The old screen, frozen. */
  fun freeze(bitmap: Bitmap) {
    from = bitmap
    invalidate()
  }

  /** The new screen is not ready yet: let the transition start its waiting pre-roll (the vehicle arrives and waits). */
  fun startWaiting(t: Transition, req: TransitionRequest) {
    val f = from ?: return
    if (width == 0 || height == 0 || !t.supportsWaiting) return
    t.prepareWaiting(f, width.toFloat(), height.toFloat(), req)
    waitingT = t
    waitMs = 0L
    waitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 60_000L
      interpolator = LinearInterpolator()
      addUpdateListener {
        waitMs = it.currentPlayTime
        invalidate()
      }
      start()
    }
  }

  private fun stopWaiting() {
    waitAnimator?.cancel()
    waitAnimator = null
    waitingT = null
  }

  fun play(toBitmap: Bitmap, t: Transition, req: TransitionRequest, finished: () -> Unit) {
    val fromBitmap = from
    if (fromBitmap == null || width == 0 || height == 0) {
      finished()
      return
    }
    stopWaiting()
    to = toBitmap
    transition = t
    onFinished = finished
    done = false
    t.prepare(fromBitmap, toBitmap, width.toFloat(), height.toFloat(), req)
    val ms = if (req.durationMs > 0) req.durationMs else t.defaultMs
    val sp = t.startProgress
    progress = sp
    animator = ValueAnimator.ofFloat(sp, 1f).apply {
      duration = (ms * (1f - sp)).toLong().coerceAtLeast(1L)
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
    stopWaiting()
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
    val wt = waitingT
    if (t == null && wt != null) {
      wt.drawWaiting(canvas, f, waitMs, width.toFloat(), height.toFloat())
      return
    }
    if (t == null || b == null) {
      canvas.drawBitmap(f, 0f, 0f, null)
      return
    }
    t.draw(canvas, f, b, progress, width.toFloat(), height.toFloat())
    if (t !is FadeTransition && t !is DeliveryTransition) drawHint(canvas)
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
    stopWaiting()
    animator?.cancel()
  }
}
