package expo.modules.deliverytransitions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

internal const val BLOCKS = 5
internal const val RIDE_START = 0.08f
internal const val RIDE_SPAN = 0.82f

/** Where the vehicle is at every moment, and when each parcel is thrown. */
internal class Plan(
  ys: FloatArray,
  val car: Boolean,
  val y0: Float,
  val y1: Float,
  lead: Float,
  flyLen: Float = if (car) 0.09f else 0.10f,
  openLen: Float = if (car) 0.08f else 0.09f,
) {
  val n = ys.size
  val dwell = if (car) 0.045f else 0f
  val fly = flyLen
  val open = openLen
  private val stopY = FloatArray(n) { ys[it] - lead }
  private val speed = (y1 - y0) / (RIDE_SPAN - n * dwell)
  val arrive = FloatArray(n)
  val endT: Float

  init {
    for (j in 0 until n) arrive[j] = RIDE_START + (stopY[j] - y0) / speed + j * dwell
    endT = arrive[n - 1] + dwell + (y1 - stopY[n - 1]) / speed
  }

  fun toss(j: Int): Float = (arrive[j] + dwell * 0.45f).coerceAtMost(0.99f - fly - open)

  fun yAt(p: Float): Float {
    if (p <= RIDE_START) return y0
    var segT = RIDE_START
    var segY = y0
    for (j in 0 until n) {
      if (p < arrive[j]) {
        val t = ((p - segT) / (arrive[j] - segT).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
        val e = if (!car) t else if (j == 0) easeOutCubic(t) else smoothStep(t)
        return lf(segY, stopY[j], e)
      }
      if (p < arrive[j] + dwell) return stopY[j]
      segT = arrive[j] + dwell
      segY = stopY[j]
    }
    val t = ((p - segT) / (endT - segT).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
    return lf(segY, y1, if (car) t * t else t)
  }

  /** 0..1, > 0 while the courier swings his arm to throw. */
  fun throwPulse(p: Float, lag: Float = 0f, split: Float = -1f): Float {
    var m = 0f
    for (j in 0 until n) {
      val pj = if (split >= 0f && toss(j) < split) p + lag else p
      val t = ((pj - (toss(j) - 0.012f)) / 0.05f).coerceIn(0f, 1f)
      m = maxOf(m, sin(PI.toFloat() * t))
    }
    return m
  }

  fun braking(p: Float): Float {
    if (!car) return 0f
    for (j in 0 until n) if (p >= arrive[j] - 0.004f && p <= arrive[j] + dwell) return 1f
    return 0f
  }

  fun trunkOpen(p: Float, lag: Float = 0f, split: Float = -1f): Float {
    if (!car) return 0f
    var m = 0f
    for (j in 0 until n) {
      val pj = if (split >= 0f && toss(j) < split) p + lag else p
      val t1 = smoothStep((pj - arrive[j]) / 0.010f)
      val t2 = 1f - smoothStep((pj - (arrive[j] + dwell - 0.010f)) / 0.010f)
      m = maxOf(m, minOf(t1, t2))
    }
    return m
  }
}

/**
 * DELIVERY DROP. The vehicle starts driving the moment the user taps (while the new screen is still rendering), then
 * drives down the left side and throws a parcel (with the logo) to every element of the new screen.
 *
 * The new screen is cut into horizontal slices that together cover the WHOLE screen (no gaps, no lines): the
 * boundaries come from the real elements (<DeliveryTarget>, or five equal bands when there are none), and every
 * slice opens where its parcel lands.
 */
internal class DeliveryTransition(private val car: Boolean, private val bag: Boolean = false) : Transition() {
  // bag = ONE parcel to the middle of the screen that grows into the new page (the other mode: five parcels, five slices)
  override val defaultMs = if (bag) (if (car) 1500 else 1400) else (if (car) 1400 else 1200)
  override fun interpolator() = LinearInterpolator()

  private lateinit var art: VehicleArt
  private var d = 1f
  private var n = 0
  private var tiles: Array<RectF> = emptyArray() // slices of the new screen; together they are the whole screen
  private var cx = FloatArray(0) // where each parcel lands = centre of the real element
  private var cy = FloatArray(0)
  private var plan: Plan? = null
  private var loadIdx = -1 // the first element that is still loading: the vehicle stops there
  private var laneX = 0f
  private var accent = 0
  private var bg = Color.BLACK
  private var dur = 1200 // total duration in ms
  private var lastIdleMs = 0L // how long the vehicle waited at a loading element (parcels already thrown keep going)

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
  private val src = Rect()
  private val dst = RectF()
  private val sheet = Sheet() // the bag, growing into the new page
  private var sprite: Bitmap? = null // the parcel (box + tape + logo), drawn ONCE; per frame it is only moved
  private var spriteSize = 0
  private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.DEFAULT_BOLD
    textAlign = Paint.Align.CENTER
  }

  // ---- waiting pre-roll: the vehicle slides in right away and waits while the new screen renders ----
  override val supportsWaiting: Boolean get() = true
  private var waited = false
  private var holdY = 0f
  private var roadTop = 0f
  private var lastVy = 0f
  private var waitAlpha = 1f // how bright the old screen was when the new one arrived (continuity)
  override val startProgress: Float get() = if (waited) RIDE_START else 0f

  override fun holdProgress(): Float {
    val pl = plan ?: return -1f
    return if (loadIdx in 0 until pl.n) pl.arrive[loadIdx] else -1f
  }

  /** "?" bubbles float up from the vehicle while it waits (for the new screen, or for the data of a loading element). */
  private fun makeSprite() {
    sprite?.recycle()
    val s = (50f * d * 1.4f).toInt().coerceAtLeast(16)
    val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    art.parcel(Canvas(b), s / 2f, s / 2f, 50f * d, 0f, accent, 1f)
    sprite = b
    spriteSize = s
  }

  private fun drawBubbles(c: Canvas, vy: Float, ms: Long) {
    mark.color = accent
    mark.textSize = 20f * d
    for (k in 0 until 3) {
      val t = (ms / 1100f + k / 3f) % 1f
      val x = laneX + 34f * d + sin(t * 6.2832f + k) * 9f * d
      val y = vy + 34f * d - t * 64f * d
      mark.alpha = (sin(PI.toFloat() * t) * 230f).toInt().coerceIn(0, 255)
      c.drawText("?", x, y, mark)
    }
  }

  override fun prepareWaiting(from: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    d = req.density
    art = VehicleArt(d)
    accent = req.accent
    laneX = (if (car) 52f else 40f) * d
    roadTop = -90f * d
    holdY = 64f * d
    lastVy = roadTop
    bg = req.background
    waited = true
  }

  override fun drawWaiting(c: Canvas, from: Bitmap, ms: Long, w: Float, h: Float) {
    c.drawColor(bg)
    // the old screen fades to solid black: the tap was understood and something is happening
    waitAlpha = 1f - smoothStep(ms / 240f)
    if (waitAlpha > 0f) {
      paint.alpha = (waitAlpha * 255f).toInt()
      c.drawBitmap(from, 0f, 0f, paint)
      paint.alpha = 255
    }

    val arrive = easeOutCubic(ms / 300f)
    val bob = if (arrive >= 1f) sin(ms / 170f) * 2f * d else 0f
    val vy = lf(roadTop, holdY, arrive) + bob
    lastVy = vy
    if (vy <= roadTop + 1f) return
    if (car) art.car(c, laneX, vy, 90f, 0f, 0f) else art.bike(c, laneX, vy, 90f, 0f)

    if (arrive >= 1f) drawBubbles(c, vy, ms - 300L)
  }

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    d = req.density
    art = VehicleArt(d)
    accent = req.accent
    bg = req.background
    dur = if (req.durationMs > 0) req.durationMs else defaultMs
    laneX = (if (car) 52f else 40f) * d
    roadTop = -90f * d
    val found = elements(req.targets, req.loading, w, h)
    if (bag) {
      // one parcel, to the middle of the screen; the vehicle waits for the data of the first loading element, if any
      n = 1
      loadIdx = if (req.loading.any { it }) 0 else -1
      tiles = emptyArray()
      cx = floatArrayOf(w / 2f)
      cy = floatArrayOf(h / 2f)
    } else {
      val items = found.map { it.rect }
      loadIdx = found.indexOfFirst { it.loading }
      n = items.size
      tiles = tile(items, w, h)
      cx = FloatArray(n) { items[it].centerX() }
      cy = FloatArray(n) { items[it].centerY() }
    }
    val y1 = h + 90f * d
    val lead = (if (car) 40f else 46f) * d
    // continue from where the vehicle is waiting (never below the first stop, or it would have to drive backwards)
    val y0 = if (waited) minOf(lastVy, cy[0] - lead - 8f * d) else roadTop
    plan =
      if (bag) Plan(FloatArray(n) { cy[it] }, car, y0, y1, lead, flyLen = 0.16f, openLen = 0.34f)
      else Plan(FloatArray(n) { cy[it] }, car, y0, y1, lead)
    makeSprite()
  }

  private class Item(val rect: RectF, val loading: Boolean)

  /** The real elements, clamped to the screen, top to bottom. Five equal bands when JavaScript gave none. */
  private fun elements(targets: List<RectF>, loading: List<Boolean>, w: Float, h: Float): List<Item> {
    val minSize = 24f * d
    val list = targets
      .mapIndexed { i, it ->
        Item(
          RectF(it.left.coerceIn(0f, w), it.top.coerceIn(0f, h), it.right.coerceIn(0f, w), it.bottom.coerceIn(0f, h)),
          loading.getOrElse(i) { false },
        )
      }
      .filter { it.rect.width() >= minSize && it.rect.height() >= minSize }
      .sortedBy { it.rect.top }
      .take(BLOCKS)
    if (list.isNotEmpty()) return list
    val band = h / BLOCKS
    return List(BLOCKS) { Item(RectF(0f, it * band, w, (it + 1) * band), false) }
  }

  /** Full-width slices from the top edge to the bottom edge; each boundary is halfway between two neighbouring elements. */
  private fun tile(items: List<RectF>, w: Float, h: Float): Array<RectF> {
    var top = 0f
    return Array(items.size) { i ->
      val bottom =
        if (i == items.size - 1) h else ((items[i].bottom + items[i + 1].top) / 2f).coerceIn(top + 1f, h)
      RectF(0f, top, w, bottom).also { top = bottom }
    }
  }

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    val pl = plan ?: return
    c.drawColor(bg)
    if (idleMs > lastIdleMs) lastIdleMs = idleMs
    val lag = if (dur > 0) lastIdleMs / dur.toFloat() else 0f
    val split = holdProgress()

    // the old screen steps aside
    val e = if (waited) smoothStep((p - RIDE_START) / 0.14f) else smoothStep(p / 0.20f)
    if (e < 1f) {
      paint.alpha = ((1f - e) * (if (waited) waitAlpha else 1f) * 255f).toInt()
      c.drawBitmap(from, 0f, 0f, paint)
      paint.alpha = 255
    }

    // every slice of the new screen opens where its parcel landed
    for (j in 0 until (if (bag) 0 else n)) {
      val pj = if (split >= 0f && pl.toss(j) < split) p + lag else p
      val t = ((pj - pl.toss(j) - pl.fly) / pl.open).coerceIn(0f, 1f)
      if (t <= 0f) continue
      val r = tiles[j]
      val l = r.left.toInt().coerceIn(0, to.width - 1)
      val tp = r.top.toInt().coerceIn(0, to.height - 1)
      val rt = r.right.toInt().coerceIn(l + 1, to.width)
      val bt = r.bottom.toInt().coerceIn(tp + 1, to.height)
      src.set(l, tp, rt, bt)
      dst.set(l.toFloat(), tp.toFloat(), rt.toFloat(), bt.toFloat())
      if (t >= 1f) {
        c.drawBitmap(to, src, dst, null) // same integer edges as the neighbours: no seams
      } else {
        c.save()
        val s = lf(0.08f, 1f, easeOutCubic(t))
        c.scale(s, s, cx[j], cy[j])
        paint.alpha = (minOf(1f, t * 4f) * 255f).toInt()
        c.drawBitmap(to, src, dst, paint)
        paint.alpha = 255
        c.restore()
      }
    }

    if (bag) drawBag(c, to, pl, p, w, h)

    // at the very end the complete real screen takes over
    val fa = smoothStep((p - 0.93f) / 0.07f)
    if (fa > 0f) {
      paint.alpha = (fa * 255f).toInt()
      c.drawBitmap(to, 0f, 0f, paint)
      paint.alpha = 255
    }

    drawOverlay(c, pl, p, lag, split)
  }

  private fun drawSprite(c: Canvas, sp: Bitmap, x: Float, y: Float, k: Float, rot: Float, alpha: Float) {
    c.save()
    c.translate(x, y)
    c.rotate(rot)
    c.scale(k, k)
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    c.drawBitmap(sp, -spriteSize / 2f, -spriteSize / 2f, paint)
    paint.alpha = 255
    c.restore()
  }

  /** The one parcel: thrown to the middle of the screen, then it grows (a box that opens) into the new page. */
  private fun drawBag(c: Canvas, to: Bitmap, pl: Plan, p: Float, w: Float, h: Float) {
    val sp = sprite ?: return
    val ts = pl.toss(0)
    val fl = ((p - ts) / pl.fly).coerceIn(0f, 1f)
    val ex = ((p - ts - pl.fly) / pl.open).coerceIn(0f, 1f)
    if (fl > 0f && ex <= 0f) {
      val vyT = pl.yAt(ts)
      val sx = if (car) laneX else laneX + 40f * d // out of the trunk / out of the rider's hand
      val sy = if (car) vyT - 56f * d else vyT + 4f * d
      val x = lf(sx, cx[0], fl)
      val y = lf(sy, cy[0], fl) - 90f * d * 4f * fl * (1f - fl)
      val born = 0.5f + 0.5f * smoothStep(fl / 0.2f)
      drawSprite(c, sp, x, y, born, 720f * fl, 1f) // two full turns: the logo lands upright
    } else if (ex > 0f) {
      val g = smoothStep(ex)
      val half = 25f * d
      val l = lf(cx[0] - half, 0f, g)
      val t = lf(cy[0] - half, 0f, g)
      val r = lf(cx[0] + half, w, g)
      val b = lf(cy[0] + half, h, g)
      sheet.draw(c, to, l, t, r, b, lf(12f * d, 0f, g), smoothStep(ex / 0.35f))
      val k = minOf((r - l) / (50f * d), 1.8f)
      drawSprite(c, sp, (l + r) / 2f, (t + b) / 2f, k, 0f, 1f - smoothStep(ex / 0.4f))
    }
  }

  private fun drawOverlay(c: Canvas, pl: Plan, p: Float, lag: Float, split: Float) {
    val vy = pl.yAt(p)
    val active = p >= RIDE_START && p < pl.endT && vy < pl.y1 - 1f && (waited || vy > pl.y0 + 1f)

    // parcels in the air
    for (j in 0 until (if (bag) 0 else n)) {
      val ts = pl.toss(j)
      val pj = if (split >= 0f && ts < split) p + lag else p
      val fl = ((pj - ts) / pl.fly).coerceIn(0f, 1f)
      val op = ((pj - ts - pl.fly) / pl.open).coerceIn(0f, 1f)
      if (fl > 0f && op < 0.4f) {
        val vyT = pl.yAt(ts)
        val sx = if (car) laneX else laneX + 40f * d // out of the trunk / out of the rider's hand
        val sy = if (car) vyT - 56f * d else vyT + 4f * d
        val x = lf(sx, cx[j], fl)
        val y = lf(sy, cy[j], fl) - 70f * d * 4f * fl * (1f - fl)
        val born = 0.5f + 0.5f * smoothStep(fl / 0.2f)
        val k = born * (1f - op * 0.6f) // 1.0 = full size (50 dp)
        val sp = sprite
        if (sp != null) {
          // 720 degrees = two full turns, so the logo lands upright
          c.save()
          c.translate(x, y)
          c.rotate(720f * fl)
          c.scale(k, k)
          paint.alpha = ((1f - op * 2.5f).coerceIn(0f, 1f) * 255f).toInt()
          c.drawBitmap(sp, -spriteSize / 2f, -spriteSize / 2f, paint)
          paint.alpha = 255
          c.restore()
        }
      }
    }

    // the vehicle
    if (active) {
      if (car) art.car(c, laneX, vy, 90f, pl.braking(p), pl.trunkOpen(p, lag, split))
      else art.bike(c, laneX, vy, 90f, pl.throwPulse(p, lag, split))
      if (idleMs > 0L) drawBubbles(c, vy, idleMs) // waiting for the data of a loading element
    }
  }
}
