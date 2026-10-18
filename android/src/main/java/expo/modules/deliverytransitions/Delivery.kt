package expo.modules.deliverytransitions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
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
 * DELIVERY DROP. The old screen steps aside, the vehicle drives down the left side and throws a parcel (with the logo)
 * to the spot of every card of the new screen; the parcel un-boxes into that card.
 *
 * The cards come from the REAL new screen: JavaScript passes the rectangles of up to 5 elements (<DeliveryTarget>).
 * Without them the screen is simply cut into 5 bands.
 */
internal class DeliveryTransition(private val car: Boolean) : Transition() {
  override val defaultMs = if (car) 3200 else 2600
  override fun interpolator() = LinearInterpolator()

  private lateinit var art: VehicleArt
  private var d = 1f
  private var rects: List<RectF> = emptyList()
  private var plan: Plan? = null
  private var laneX = 0f
  private var accent = 0
  private var bg = Color.WHITE

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val stamp = Paint(Paint.ANTI_ALIAS_FLAG)
  private val src = Rect()
  private val tmp = RectF()

  // ---- waiting pre-roll: the vehicle arrives and waits while the new screen is still rendering ----
  override val supportsWaiting: Boolean get() = true
  private var waited = false
  private var holdY = 0f
  private var roadTop = 0f
  private var lastVy = 0f
  private var lastE = 0f
  private var lastLook = 0f
  private var lastHazard = 0f
  override val startProgress: Float get() = if (waited) RIDE_START else 0f

  override fun prepareWaiting(from: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    d = req.density
    art = VehicleArt(d)
    accent = req.accent
    laneX = (if (car) 52f else 40f) * d
    roadTop = -90f * d
    holdY = 64f * d
    lastVy = roadTop
    bg = if (from.width > 4 && from.height > 4) from.getPixel(2, 2) else Color.BLACK
    waited = true
  }

  override fun drawWaiting(c: Canvas, from: Bitmap, ms: Long, w: Float, h: Float) {
    c.drawColor(bg)

    // the old screen steps aside (same look as the real transition)
    val e = smoothStep(ms / 450f)
    lastE = e
    if (e < 1f) {
      val s = 1f - 0.08f * e
      c.save()
      c.scale(s, s, w / 2f, h / 2f)
      paint.alpha = ((1f - e) * 255f).toInt()
      c.drawBitmap(from, 0f, 0f, paint)
      c.restore()
      paint.alpha = 255
    }

    // the vehicle arrives from the top and waits there, looking around
    val arrive = easeOutCubic((ms - 150f) / 650f)
    val vy = lf(roadTop, holdY, arrive)
    lastVy = vy
    if (vy > roadTop + 1f) {
      if (car) {
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK.withAlpha(0.05f)
        c.drawRect(laneX - 40f * d, roadTop, laneX + 40f * d, vy, paint)
      }
      line.color = Color.BLACK.withAlpha(0.13f)
      line.strokeWidth = 3f * d
      line.strokeCap = Paint.Cap.BUTT
      c.drawLine(laneX, roadTop, laneX, vy, line)
      val idle = smoothStep((ms - 800f) / 300f)
      lastLook = idle * 35f * sin((ms - 800f) / 380f)
      lastHazard = if (car) idle * (0.5f + 0.5f * sin(ms / 140f)) else 0f
      if (car) art.car(c, laneX, vy, 90f, 0f, 0f, 1f, lastHazard)
      else art.bike(c, laneX, vy, 90f, 0f, 1f, lastLook)
    }
  }

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    d = req.density
    art = VehicleArt(d)
    accent = req.accent
    bg = if (to.width > 4 && to.height > 4) to.getPixel(2, 2) else Color.WHITE
    rects = if (req.targets.size >= BLOCKS) req.targets.take(BLOCKS) else fallbackTargets(w, h)
    val y1 = h + 90f * d
    val lead = (if (car) 40f else 46f) * d
    laneX = (if (car) 52f else 40f) * d
    roadTop = -90f * d
    // continue from where the vehicle is waiting (never below the first stop, or it would have to drive backwards)
    val y0 = if (waited) minOf(lastVy, rects[0].centerY() - lead - 8f * d) else roadTop
    plan = Plan(FloatArray(BLOCKS) { rects[it].centerY() }, car, y0, y1, lead)
    line.pathEffect = DashPathEffect(floatArrayOf(14f * d, 12f * d), 0f)
    stamp.typeface = Typeface.DEFAULT_BOLD
    stamp.textSize = 15f * d
  }

  private fun fallbackTargets(w: Float, h: Float): List<RectF> {
    val top = 96f * d
    val bottom = h - 48f * d
    val gap = 14f * d
    val hh = (bottom - top - gap * (BLOCKS - 1)) / BLOCKS
    return List(BLOCKS) { i ->
      val y = top + i * (hh + gap)
      RectF(16f * d, y, w - 16f * d, y + hh)
    }
  }

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    val pl = plan ?: return
    c.drawColor(bg)

    // the old screen steps aside
    val e = if (waited) lf(lastE, 1f, smoothStep((p - RIDE_START) / 0.10f)) else smoothStep(p / 0.22f)
    if (e < 1f) {
      val s = 1f - 0.08f * e
      c.save()
      c.scale(s, s, w / 2f, h / 2f)
      paint.alpha = ((1f - e) * 255f).toInt()
      c.drawBitmap(from, 0f, 0f, paint)
      c.restore()
    }

    // the cards of the new screen, "un-boxed" one by one
    for (j in 0 until BLOCKS) {
      val r = rects[j]
      val t = ((p - pl.toss(j) - pl.fly) / pl.open).coerceIn(0f, 1f)
      if (t <= 0f) continue
      val s = lf(0.08f, 1f, easeOutBack(t))
      val a = (t * 3f).coerceAtMost(1f)
      val l = r.left.toInt().coerceIn(0, to.width - 1)
      val tp = r.top.toInt().coerceIn(0, to.height - 1)
      val rt = r.right.toInt().coerceIn(l + 1, to.width)
      val bt = r.bottom.toInt().coerceIn(tp + 1, to.height)
      src.set(l, tp, rt, bt)
      tmp.set(l.toFloat(), tp.toFloat(), rt.toFloat(), bt.toFloat())
      c.save()
      c.scale(s, s, tmp.centerX(), tmp.centerY())
      paint.alpha = (a * 255f).toInt()
      c.drawBitmap(to, src, tmp, paint)
      c.restore()
    }

    // at the very end the complete real screen (status bar, everything else) takes over
    val fa = smoothStep((p - 0.93f) / 0.07f)
    if (fa > 0f) {
      paint.alpha = (fa * 255f).toInt()
      c.drawBitmap(to, 0f, 0f, paint)
    }
    paint.alpha = 255

    drawOverlay(c, pl, p)
    drawStamp(c, p, w, h)
  }

  private fun drawOverlay(c: Canvas, pl: Plan, p: Float) {
    val vy = pl.yAt(p)
    val active =
      if (waited) p >= RIDE_START && p < pl.endT && vy < pl.y1 - 1f
      else p > RIDE_START && p < pl.endT && vy > pl.y0 + 1f && vy < pl.y1 - 1f
    // what was already playing while we waited fades into the normal drive
    val ramp = if (waited) smoothStep((p - RIDE_START) / 0.05f) else 1f
    val carry = if (waited) 1f - smoothStep((p - RIDE_START) / 0.05f) else 0f

    // the road behind the vehicle
    if (active) {
      if (car) {
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK.withAlpha(0.05f)
        c.drawRect(laneX - 40f * d, roadTop, laneX + 40f * d, vy, paint)
      }
      line.color = Color.BLACK.withAlpha(0.13f)
      line.strokeWidth = 3f * d
      c.drawLine(laneX, roadTop, laneX, vy, line)
    }

    // parcels in the air
    for (j in 0 until pl.n) {
      val r = rects[j]
      val ts = pl.toss(j)
      val fl = ((p - ts) / pl.fly).coerceIn(0f, 1f)
      val op = ((p - ts - pl.fly) / pl.open).coerceIn(0f, 1f)
      if (fl > 0f && op < 0.4f) {
        val vyT = pl.yAt(ts)
        val sx = if (car) laneX else laneX + 40f * d // out of the trunk / out of the rider's hand
        val sy = if (car) vyT - 56f * d else vyT + 4f * d
        val x = lf(sx, r.centerX(), fl)
        val y = lf(sy, r.centerY(), fl) - 70f * d * 4f * fl * (1f - fl)
        val born = 0.5f + 0.5f * smoothStep(fl / 0.2f)
        val size = 50f * d * born * (1f - op * 0.6f)
        // 720 degrees = two full turns, so the logo lands upright
        art.parcel(c, x, y, size, 720f * fl, accent, 1f - op * 2.5f)
      }
    }

    // the vehicle
    if (active) {
      if (car) {
        art.car(c, laneX, vy, 90f, pl.braking(p), pl.trunkOpen(p), 1f, lastHazard * carry)
      } else {
        val rad = Math.toRadians(90.0)
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        line.strokeCap = Paint.Cap.ROUND
        line.strokeWidth = 2.5f * d
        for (k in -1..1) {
          val sx = laneX - dy * k * 12f * d
          val sy = vy + dx * k * 12f * d
          for (m in 0..1) {
            val a = 80f * d + m * 30f * d
            line.color = Color.BLACK.withAlpha((0.14f - m * 0.05f) * ramp)
            c.drawLine(sx - dx * a, sy - dy * a, sx - dx * (a + 22f * d), sy - dy * (a + 22f * d), line)
          }
        }
        line.strokeCap = Paint.Cap.BUTT
        val wob = sin(p * 90f) * 1.5f * d
        art.bike(c, laneX + wob, vy, 90f, pl.throwPulse(p), 1f, lastLook * carry)
      }
    }
  }

  private fun drawStamp(c: Canvas, p: Float, w: Float, h: Float) {
    val t = ((p - 0.88f) / 0.12f).coerceIn(0f, 1f)
    val a = sin(PI.toFloat() * t)
    if (a <= 0.01f) return
    val text = "Delivered  ✓"
    val tw = stamp.measureText(text)
    val padX = 18f * d
    val padY = 10f * d
    val th = stamp.textSize
    val bw = tw + padX * 2f
    val bh = th + padY * 2f
    val cx = w / 2f
    val bottom = h - 56f * d
    c.save()
    val s = 0.9f + 0.1f * a
    c.scale(s, s, cx, bottom - bh / 2f)
    paint.style = Paint.Style.FILL
    paint.color = Logo.NAVY.withAlpha(a)
    tmp.set(cx - bw / 2f, bottom - bh, cx + bw / 2f, bottom)
    c.drawRoundRect(tmp, bh / 2f, bh / 2f, paint)
    stamp.color = Color.WHITE.withAlpha(a)
    c.drawText(text, cx - tw / 2f, bottom - padY - th * 0.2f, stamp)
    c.restore()
  }
}
