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
  private var holdAt = -1f
  private var holding = false
  private var released = false
  private var pendingRefresh: Bitmap? = null
  private var idleAnimator: ValueAnimator? = null
  private val releaseRunnable = Runnable { release() }

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

  /** Gives the two full-screen pictures back to the system right away (about 10 MB each on a modern phone). */
  fun recycleBitmaps() {
    val f = from
    val t = to
    from = null
    to = null
    pendingRefresh = null
    if (f != null && !f.isRecycled) f.recycle()
    if (t != null && !t.isRecycled) t.recycle()
  }

  /** The frozen old screen (used to notice when the new one is really on screen). */
  fun frozen(): Bitmap? = from

  /** The old screen, frozen. */
  fun freeze(bitmap: Bitmap) {
    from = bitmap
    invalidate()
  }

  /** The new screen is not ready yet: let the transition start its waiting pre-roll (the vehicle arrives and waits). */
  fun startWaiting(t: Transition, req: TransitionRequest, retries: Int = 20) {
    val f = from ?: return
    if (!t.supportsWaiting) return
    if (width == 0 || height == 0) {
      // the overlay has not been laid out yet (it was added a moment ago): try again next frame
      if (retries > 0) postOnAnimation { startWaiting(t, req, retries - 1) }
      return
    }
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

  /** The data of a loading element arrived (fresh picture of the new screen). */
  fun refresh(bitmap: Bitmap) {
    if (transition == null) {
      pendingRefresh = bitmap // the animation has not started yet: it starts with this picture
      return
    }
    to = bitmap
    released = true
    if (holding) release()
    invalidate()
  }

  private fun hold() {
    holding = true
    val t = transition ?: return
    animator?.pause()
    idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 60_000L
      interpolator = LinearInterpolator()
      addUpdateListener {
        t.idleMs = it.currentPlayTime.coerceAtLeast(1L)
        invalidate()
      }
      start()
    }
    postDelayed(releaseRunnable, 3500L) // the data never came: go on anyway
  }

  private fun release() {
    removeCallbacks(releaseRunnable)
    if (!holding) return
    holding = false
    released = true
    stopIdle()
    animator?.resume()
  }

  private fun stopIdle() {
    idleAnimator?.cancel()
    idleAnimator = null
    transition?.idleMs = 0L
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
    released = pendingRefresh != null
    to = pendingRefresh ?: toBitmap
    pendingRefresh = null
    transition = t
    onFinished = finished
    done = false
    t.prepare(fromBitmap, toBitmap, width.toFloat(), height.toFloat(), req)
    val ms = if (req.durationMs > 0) req.durationMs else t.defaultMs
    val sp = t.startProgress
    progress = sp
    holding = false
    holdAt = if (released) -1f else t.holdProgress()
    animator = ValueAnimator.ofFloat(sp, 1f).apply {
      duration = (ms * (1f - sp)).toLong().coerceAtLeast(1L)
      interpolator = t.interpolator()
      addUpdateListener {
        progress = it.animatedValue as Float
        if (holdAt >= 0f && !released && !holding && progress >= holdAt) {
          progress = holdAt
          hold() // the vehicle stands where the data is still loading
        }
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
    removeCallbacks(releaseRunnable)
    stopIdle()
    stopWaiting()
    animator?.cancel()
    finish()
  }

  private fun finish() {
    if (done) return
    done = true
    removeCallbacks(releaseRunnable)
    stopIdle()
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
    if (t !is FadeTransition && t !is DismissTransition && t !is CardExpandTransition && t !is DeliveryTransition) drawHint(canvas)
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
    removeCallbacks(releaseRunnable)
    stopIdle()
    stopWaiting()
    animator?.cancel()
  }
}
