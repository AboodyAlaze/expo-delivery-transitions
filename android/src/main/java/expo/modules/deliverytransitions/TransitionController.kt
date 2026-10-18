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
  private const val PROBE_W = 45
  private const val PROBE_H = 100
  private val main = Handler(Looper.getMainLooper())

  private var host: ViewGroup? = null
  private var rnRoot: View? = null
  private var overlay: TransitionView? = null
  private var pendingDone: (() -> Unit)? = null
  private var watchdog: Runnable? = null
  private var waitingTransition: Transition? = null
  private var waitingName: String? = null
  private var fromCount = 0
  private var fromProbe: IntArray? = null

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
    fromCount = countViews(root)
    fromProbe = probe(activity, root)
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
   * Calls [done] when the NEW screen is really on screen: its view tree has many more (or fewer) views than the old
   * screen, or the tiny picture of the screen is clearly another screen. A pressed button or a fading tab bar is not
   * enough. Two frames later (so it is laid out), or after [timeoutMs], whichever comes first.
   * Cheap on purpose: counting views costs about a millisecond and the tiny picture is taken only every 100 ms, so this
   * never competes with the work that is mounting the new screen.
   */
  fun waitForScreen(activity: Activity, timeoutMs: Int, done: () -> Unit) {
    val view = overlay
    val root = rnRoot
    if (!isRunning || view == null || root == null) {
      done()
      return
    }
    armWatchdog(timeoutMs + 5000L)
    val t0 = SystemClock.uptimeMillis()
    var nextProbeAt = t0 + 200L // the view count catches almost every screen; the picture is only the second opinion
    val step = object : Runnable {
      override fun run() {
        if (!isRunning || overlay !== view) {
          done()
          return
        }
        val now = SystemClock.uptimeMillis()
        var changed = Math.abs(countViews(root) - fromCount) >= 25
        if (!changed && now >= nextProbeAt) {
          val s = SystemClock.uptimeMillis()
          changed = meanDiff(probe(activity, root), fromProbe) >= 6.0
          // never let the watching cost the app more than about 4% of one core: slow screens are probed less often
          nextProbeAt = SystemClock.uptimeMillis() + maxOf(250L, (SystemClock.uptimeMillis() - s) * 25L)
        }
        if (changed || now - t0 >= timeoutMs) {
          main.postDelayed({ done() }, 17L) // one frame: the new views are laid out
        } else {
          main.postDelayed(this, 33L)
        }
      }
    }
    step.run()
  }

  /** Step 2: the new screen is rendered, play. [onDone] is called exactly once, when the animation ends or is skipped. */
  fun play(activity: Activity, json: String, onDone: () -> Unit) {
    val view = overlay
    val root = rnRoot
    if (!isRunning || view == null || root == null) {
      onDone()
      return
    }
    val req = parse(json, activity.resources.displayMetrics.density)
    pendingDone = onDone
    val toBmp = capture(activity, root)
    if (toBmp == null) {
      finish() // also reports "done" to JavaScript
      return
    }
    val waiting = waitingTransition
    val t = if (waiting != null && waitingName == req.name) waiting else createTransition(req.name)
    val ms = if (req.durationMs > 0) req.durationMs else t.defaultMs
    armWatchdog(ms + 2500L)
    view.play(toBmp, t, req) { finish() }
  }

  private fun countViews(v: View): Int {
    var n = 1
    if (v is ViewGroup) for (i in 0 until v.childCount) n += countViews(v.getChildAt(i))
    return n
  }

  /** A tiny grey picture of the screen (45 x 100), to tell "another screen" from "the same screen, pressed". */
  private fun probe(activity: Activity, v: View): IntArray? {
    val w = v.width
    val h = v.height
    if (w <= 0 || h <= 0) return null
    return try {
      val bmp = Bitmap.createBitmap(PROBE_W, PROBE_H, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.scale(PROBE_W / w.toFloat(), PROBE_H / h.toFloat())
      activity.window.decorView.background?.let {
        it.setBounds(0, 0, w, h)
        it.draw(canvas)
      }
      v.draw(canvas)
      val px = IntArray(PROBE_W * PROBE_H)
      bmp.getPixels(px, 0, PROBE_W, 0, 0, PROBE_W, PROBE_H)
      bmp.recycle()
      IntArray(px.size) { i ->
        val p = px[i]
        (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
      }
    } catch (t: Throwable) {
      null
    }
  }

  private fun meanDiff(a: IntArray?, b: IntArray?): Double {
    if (a == null || b == null || a.size != b.size) return 0.0
    var sum = 0L
    for (i in a.indices) sum += Math.abs(a[i] - b[i])
    return sum.toDouble() / a.size
  }

  /** The data of a loading element arrived: take a fresh picture of the new screen and let the vehicle go on. */
  fun refresh(activity: Activity) {
    val view = overlay ?: return
    val root = rnRoot ?: return
    if (!isRunning) return
    val bmp = capture(activity, root) ?: return
    view.refresh(bmp)
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
    view?.let { v ->
      (v.parent as? ViewGroup)?.removeView(v)
      // give the pictures back a moment later, when no frame can still be using them
      main.postDelayed({ v.recycleBitmaps() }, 200L)
    }
    fromProbe = null
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
    val flags = ArrayList<Boolean>()
    val arr = o.optJSONArray("targets")
    if (arr != null) {
      for (i in 0 until arr.length()) {
        val t = arr.getJSONObject(i)
        val x = t.optDouble("x", 0.0).toFloat() * d
        val y = t.optDouble("y", 0.0).toFloat() * d
        val w = t.optDouble("width", 0.0).toFloat() * d
        val h = t.optDouble("height", 0.0).toFloat() * d
        if (w > 0f && h > 0f) {
          targets.add(RectF(x, y, x + w, y + h))
          flags.add(t.optBoolean("loading", false))
        }
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
      loading = flags,
      background = o.optLong("background", 0xFF000000L).toInt() or 0xFF000000.toInt(), // always opaque
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
