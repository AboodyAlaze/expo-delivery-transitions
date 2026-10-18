package expo.modules.deliverytransitions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
) {
  val n = ys.size
  val dwell = if (car) 0.045f else 0f
  val fly = if (car) 0.09f else 0.10f
  val open = if (car) 0.08f else 0.09f
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
  fun throwPulse(p: Float): Float {
    var m = 0f
    for (j in 0 until n) {
      val t = ((p - (toss(j) - 0.012f)) / 0.05f).coerceIn(0f, 1f)
      m = maxOf(m, sin(PI.toFloat() * t))
    }
    return m
  }

  fun braking(p: Float): Float {
    if (!car) return 0f
    for (j in 0 until n) if (p >= arrive[j] - 0.004f && p <= arrive[j] + dwell) return 1f
    return 0f
  }

  fun trunkOpen(p: Float): Float {
    if (!car) return 0f
    var m = 0f
    for (j in 0 until n) {
      val t1 = smoothStep((p - arrive[j]) / 0.010f)
      val t2 = 1f - smoothStep((p - (arrive[j] + dwell - 0.010f)) / 0.010f)
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
internal class DeliveryTransition(private val car: Boolean) : Transition() {
  override val defaultMs = if (car) 1400 else 1200
  override fun interpolator() = LinearInterpolator()

  private lateinit var art: VehicleArt
  private var d = 1f
  private var n = 0
  private var tiles: Array<RectF> = emptyArray() // slices of the new screen; together they are the whole screen
  private var cx = FloatArray(0) // where each parcel lands = centre of the real element
  private var cy = FloatArray(0)
  private var plan: Plan? = null
  private var laneX = 0f
  private var accent = 0
  private var bg = Color.BLACK

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
  private val src = Rect()
  private val dst = RectF()

  // ---- waiting pre-roll: the vehicle slides in right away and waits while the new screen renders ----
  override val supportsWaiting: Boolean get() = true
  private var waited = false
  private var holdY = 0f
  private var roadTop = 0f
  private var lastVy = 0f
  override val startProgress: Float get() = if (waited) RIDE_START else 0f

  private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

  override fun prepareWaiting(from: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    d = req.density
    art = VehicleArt(d)
    accent = req.accent
    laneX = (if (car) 52f else 40f) * d
    roadTop = -90f * d
    holdY = 64f * d
    lastVy = roadTop
    bg = opaque(from.getPixel(2, from.height / 2))
    waited = true
  }

  override fun drawWaiting(c: Canvas, from: Bitmap, ms: Long, w: Float, h: Float) {
    c.drawColor(bg)
    c.drawBitmap(from, 0f, 0f, null) // the old screen stays exactly as it was: the app answered instantly
    val arrive = easeOutCubic(ms / 320f)
    val bob = if (arrive >= 1f) sin(ms / 170f) * 2f * d else 0f
    val vy = lf(roadTop, holdY, arrive) + bob
    lastVy = vy
    if (vy > roadTop + 1f) {
      if (car) art.car(c, laneX, vy, 90f, 0f, 0f) else art.bike(c, laneX, vy, 90f, 0f)
    }
  }

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    d = req.density
    art = VehicleArt(d)
    accent = req.accent
    bg = opaque(to.getPixel(2, to.height / 2))
    laneX = (if (car) 52f else 40f) * d
    roadTop = -90f * d
    val items = elements(req.targets, w, h)
    n = items.size
    tiles = tile(items, w, h)
    cx = FloatArray(n) { items[it].centerX() }
    cy = FloatArray(n) { items[it].centerY() }
    val y1 = h + 90f * d
    val lead = (if (car) 40f else 46f) * d
    // continue from where the vehicle is waiting (never below the first stop, or it would have to drive backwards)
    val y0 = if (waited) minOf(lastVy, cy[0] - lead - 8f * d) else roadTop
    plan = Plan(FloatArray(n) { cy[it] }, car, y0, y1, lead)
  }

  /** The real elements, clamped to the screen, top to bottom. Five equal bands when JavaScript gave none. */
  private fun elements(targets: List<RectF>, w: Float, h: Float): List<RectF> {
    val minSize = 24f * d
    val list = targets
      .map { RectF(it.left.coerceIn(0f, w), it.top.coerceIn(0f, h), it.right.coerceIn(0f, w), it.bottom.coerceIn(0f, h)) }
      .filter { it.width() >= minSize && it.height() >= minSize }
      .sortedBy { it.top }
      .take(BLOCKS)
    if (list.isNotEmpty()) return list
    val band = h / BLOCKS
    return List(BLOCKS) { RectF(0f, it * band, w, (it + 1) * band) }
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

    // the old screen steps aside
    val e = if (waited) smoothStep((p - RIDE_START) / 0.14f) else smoothStep(p / 0.20f)
    if (e < 1f) {
      paint.alpha = ((1f - e) * 255f).toInt()
      c.drawBitmap(from, 0f, 0f, paint)
      paint.alpha = 255
    }

    // every slice of the new screen opens where its parcel landed
    for (j in 0 until n) {
      val t = ((p - pl.toss(j) - pl.fly) / pl.open).coerceIn(0f, 1f)
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

    // at the very end the complete real screen takes over
    val fa = smoothStep((p - 0.93f) / 0.07f)
    if (fa > 0f) {
      paint.alpha = (fa * 255f).toInt()
      c.drawBitmap(to, 0f, 0f, paint)
      paint.alpha = 255
    }

    drawOverlay(c, pl, p)
  }

  private fun drawOverlay(c: Canvas, pl: Plan, p: Float) {
    val vy = pl.yAt(p)
    val active = p >= RIDE_START && p < pl.endT && vy < pl.y1 - 1f && (waited || vy > pl.y0 + 1f)

    // parcels in the air
    for (j in 0 until n) {
      val ts = pl.toss(j)
      val fl = ((p - ts) / pl.fly).coerceIn(0f, 1f)
      val op = ((p - ts - pl.fly) / pl.open).coerceIn(0f, 1f)
      if (fl > 0f && op < 0.4f) {
        val vyT = pl.yAt(ts)
        val sx = if (car) laneX else laneX + 40f * d // out of the trunk / out of the rider's hand
        val sy = if (car) vyT - 56f * d else vyT + 4f * d
        val x = lf(sx, cx[j], fl)
        val y = lf(sy, cy[j], fl) - 70f * d * 4f * fl * (1f - fl)
        val born = 0.5f + 0.5f * smoothStep(fl / 0.2f)
        val size = 50f * d * born * (1f - op * 0.6f)
        // 720 degrees = two full turns, so the logo lands upright
        art.parcel(c, x, y, size, 720f * fl, accent, 1f - op * 2.5f)
      }
    }

    // the vehicle
    if (active) {
      if (car) art.car(c, laneX, vy, 90f, pl.braking(p), pl.trunkOpen(p))
      else art.bike(c, laneX, vy, 90f, pl.throwPulse(p))
    }
  }
}
