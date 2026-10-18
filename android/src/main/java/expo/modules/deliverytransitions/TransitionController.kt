package expo.modules.deliverytransitions

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import org.json.JSONObject

/**
 * Owns the full-screen overlay.
 *
 *   begin()  -> screenshot of the React Native screen + an overlay that shows it (the screen looks frozen)
 *   ...JavaScript navigates with Expo Router underneath...
 *   play()   -> screenshot of the NEW screen, then the animation runs on the overlay
 *   finish() -> overlay removed; the real new screen is already there
 *
 * Safety: a watchdog removes the overlay no matter what, so a bug can never leave the app blocked.
 * Everything here runs on the main thread.
 */
internal object TransitionController {
  private const val TAG = "DeliveryTransitions"
  private val main = Handler(Looper.getMainLooper())

  private var host: ViewGroup? = null
  private var rnRoot: View? = null
  private var overlay: TransitionView? = null
  private var pendingDone: (() -> Unit)? = null
  private var watchdog: Runnable? = null
  private var waitingTransition: Transition? = null
  private var waitingName: String? = null

  @Volatile
  var isRunning: Boolean = false
    private set

  /** Step 1: freeze the current screen. */
  fun begin(activity: Activity) {
    finish() // a previous one that was still running
    val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    val root = if (content.childCount > 0) content.getChildAt(0) else content
    val bmp = capture(activity, root) ?: return
    val view = TransitionView(activity)
    view.freeze(bmp)
    content.addView(
      view,
      ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
    host = content
    rnRoot = root
    overlay = view
    isRunning = true
    armWatchdog(8000L) // nothing can keep the app blocked for ever
  }

  /** Optional, right after begin(): the animation starts NOW (vehicle arrives and waits) while JavaScript renders. */
  fun preroll(activity: Activity, json: String) {
    val view = overlay ?: return
    if (!isRunning) return
    val req = parse(json, activity.resources.displayMetrics.density)
    val t = createTransition(req.name)
    if (!t.supportsWaiting) return
    waitingTransition = t
    waitingName = req.name
    view.startWaiting(t, req)
  }

  /**
   * Step 2: the new screen is rendered, play. [onDone] is called exactly once, when the animation ends or is skipped.
   * If the screenshot still equals the frozen old screen (the new one is not on screen yet) it is retaken a few
   * milliseconds later, so the animation never starts on a stale picture.
   */
  fun play(activity: Activity, json: String, onDone: () -> Unit) {
    val view = overlay
    val root = rnRoot
    if (!isRunning || view == null || root == null) {
      onDone()
      return
    }
    val req = parse(json, activity.resources.displayMetrics.density)
    pendingDone = onDone
    attempt(activity, view, root, req, SystemClock.uptimeMillis())
  }

  private fun attempt(activity: Activity, view: TransitionView, root: View, req: TransitionRequest, t0: Long) {
    if (!isRunning || overlay !== view) return
    val toBmp = capture(activity, root)
    if (toBmp == null) {
      finish() // also reports "done" to JavaScript
      return
    }
    val old = view.frozen()
    val stale = old != null && sameScreen(old, toBmp)
    if (stale && SystemClock.uptimeMillis() - t0 < 700L) {
      armWatchdog(4000L)
      main.postDelayed({ attempt(activity, view, root, req, t0) }, 24L)
      return
    }
    val waiting = waitingTransition
    val t = if (waiting != null && waitingName == req.name) waiting else createTransition(req.name)
    val ms = if (req.durationMs > 0) req.durationMs else t.defaultMs
    armWatchdog(ms + 2500L)
    view.play(toBmp, t, req) { finish() }
  }

  /** Cheap check (800 sample points): do the two screenshots show the same screen? */
  private fun sameScreen(a: Bitmap, b: Bitmap): Boolean {
    if (a.width != b.width || a.height != b.height) return false
    var different = 0
    for (iy in 1..40) {
      val y = (a.height * iy / 41).coerceIn(0, a.height - 1)
      for (ix in 1..20) {
        val x = (a.width * ix / 21).coerceIn(0, a.width - 1)
        if (a.getPixel(x, y) != b.getPixel(x, y) && ++different >= 8) return false
      }
    }
    return true
  }

  /** Jump to the end of the running transition. */
  fun skip() {
    main.post {
      if (!isRunning) return@post
      val v = overlay
      if (pendingDone == null || v == null) finish() else v.skip()
    }
  }

  fun cancel() {
    main.post { finish() }
  }

  fun finish() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post { finish() }
      return
    }
    watchdog?.let { main.removeCallbacks(it) }
    watchdog = null
    val view = overlay
    val done = pendingDone
    overlay = null
    pendingDone = null
    waitingTransition = null
    waitingName = null
    host = null
    rnRoot = null
    isRunning = false
    view?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
    done?.invoke()
  }

  private fun armWatchdog(ms: Long) {
    watchdog?.let { main.removeCallbacks(it) }
    val r = Runnable { finish() }
    watchdog = r
    main.postDelayed(r, ms)
  }

  private fun parse(json: String, d: Float): TransitionRequest {
    val o = JSONObject(json)
    val targets = ArrayList<RectF>()
    val arr = o.optJSONArray("targets")
    if (arr != null) {
      for (i in 0 until arr.length()) {
        val t = arr.getJSONObject(i)
        val x = t.optDouble("x", 0.0).toFloat() * d
        val y = t.optDouble("y", 0.0).toFloat() * d
        val w = t.optDouble("width", 0.0).toFloat() * d
        val h = t.optDouble("height", 0.0).toFloat() * d
        if (w > 0f && h > 0f) targets.add(RectF(x, y, x + w, y + h))
      }
    }
    return TransitionRequest(
      name = o.optString("name", "fade"),
      originX = o.optDouble("originX", 0.0).toFloat() * d,
      originY = o.optDouble("originY", 0.0).toFloat() * d,
      accent = o.optLong("accent", 0xFF7C5CFFL).toInt(), // processColor() number (ARGB, possibly unsigned)
      durationMs = o.optInt("durationMs", 0),
      targets = targets,
      density = d,
    )
  }

  /** A screenshot of [v] (the React root is usually transparent: paint what is behind it first). */
  private fun capture(activity: Activity, v: View): Bitmap? {
    val w = v.width
    val h = v.height
    if (w <= 0 || h <= 0) return null
    return try {
      val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      activity.window.decorView.background?.let {
        it.setBounds(0, 0, w, h)
        it.draw(canvas)
      }
      v.draw(canvas)
      bmp
    } catch (t: Throwable) {
      Log.w(TAG, "screenshot failed", t)
      null
    }
  }
}
