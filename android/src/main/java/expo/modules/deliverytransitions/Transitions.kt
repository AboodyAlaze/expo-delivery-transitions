package expo.modules.deliverytransitions

import android.animation.TimeInterpolator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Everything a transition needs. Coordinates are in PIXELS. */
internal class TransitionRequest(
  val name: String,
  val originX: Float,
  val originY: Float,
  val accent: Int,
  val durationMs: Int,
  val targets: List<RectF>,
  val density: Float,
  /** One flag per target: that element is still loading its data (the vehicle waits there). */
  val loading: List<Boolean> = emptyList(),
  /** The solid colour behind everything while the animation runs (black by default). */
  val background: Int = 0xFF000000.toInt(),
  /** The element that was tapped (cardExpand): the card grows from this rectangle. */
  val originRect: RectF? = null,
)

/** One animation between two screenshots. [p] goes 0 -> 1 (it may overshoot for "elastic" easings). */
internal abstract class Transition {
  abstract val defaultMs: Int
  abstract fun interpolator(): TimeInterpolator
  open fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {}
  abstract fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float)

  /**
   * "Waiting" pre-roll (delivery only): the animation can START before the new screen is ready (the vehicle arrives and
   * waits), so a slow screen never looks like a frozen app. When the new screen is ready the same instance continues.
   */
  open val supportsWaiting: Boolean get() = false
  open fun prepareWaiting(from: Bitmap, w: Float, h: Float, req: TransitionRequest) {}
  open fun drawWaiting(c: Canvas, from: Bitmap, ms: Long, w: Float, h: Float) {}

  /** Progress at which the animation must stop and wait for the data of a loading element (-1 = never). */
  open fun holdProgress(): Float = -1f

  /** Milliseconds spent waiting at the hold point (0 when not waiting): lets the transition draw an idle state. */
  var idleMs: Long = 0L

  /** Where the timeline starts (a transition that already played its first part while waiting skips it). */
  open val startProgress: Float get() = 0f

  protected val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
  private var shader: BitmapShader? = null
  private var shaderBitmap: Bitmap? = null

  /** Draws the NEW screen only where [mask] is (anti-aliased edge, no clipping). */
  protected fun drawThrough(c: Canvas, to: Bitmap, mask: Path, alpha: Int = 255, matrix: Matrix? = null) {
    var s = shader
    if (s == null || shaderBitmap !== to) {
      s = BitmapShader(to, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      shader = s
      shaderBitmap = to
    }
    s.setLocalMatrix(matrix)
    fill.style = Paint.Style.FILL
    fill.shader = s
    fill.alpha = alpha
    c.drawPath(mask, fill)
    fill.shader = null
    fill.alpha = 255
  }
}

private val FAST_OUT_SLOW_IN = PathInterpolator(0.4f, 0f, 0.2f, 1f)

/** Normal, quick transition: used for BACK. */
internal class FadeTransition : Transition() {
  override val defaultMs = 260
  override fun interpolator(): TimeInterpolator = FAST_OUT_SLOW_IN

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    c.drawBitmap(from, 0f, 0f, null)
    fill.alpha = (p.coerceIn(0f, 1f) * 255f).toInt()
    c.drawBitmap(to, 0f, 0f, fill)
    fill.alpha = 255
  }
}

/**
 * BACK. The screen you leave slides down a little, shrinks and fades away, uncovering the screen you return to (which
 * starts slightly dimmed and brightens). Quick (300 ms): nobody is made to wait.
 */
internal class DismissTransition : Transition() {
  override val defaultMs = 300
  override fun interpolator(): TimeInterpolator = FAST_OUT_SLOW_IN

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    val e = p.coerceIn(0f, 1f)
    c.drawBitmap(to, 0f, 0f, null)
    if (e < 1f) {
      c.drawColor(Color.argb(((1f - e) * 70f).toInt(), 0, 0, 0)) // the screen below brightens as the top one leaves
      val s = 1f - 0.06f * e
      c.save()
      c.translate(0f, h * 0.14f * e)
      c.scale(s, s, w / 2f, h / 2f)
      fill.alpha = ((1f - e) * 255f).toInt()
      c.drawBitmap(from, 0f, 0f, fill)
      fill.alpha = 255
      c.restore()
    }
  }
}

/** A rounded rectangle filled with a picture: "aspect fill" (the picture covers the rectangle, centred, never squashed). */
internal class Sheet {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
  private val matrix = Matrix()
  private var shader: BitmapShader? = null
  private var shaderFor: Bitmap? = null

  /** [src] = the part of the picture that fills the rectangle (default: all of it). */
  fun draw(c: Canvas, bmp: Bitmap, l: Float, t: Float, r: Float, b: Float, radius: Float, alpha: Float, src: RectF? = null) {
    if (alpha <= 0f || r - l < 1f || b - t < 1f || bmp.isRecycled) return
    var sh = shader
    if (shaderFor !== bmp || sh == null) {
      bmp.setHasMipMap(true) // the picture is shown very small at first: mipmaps keep it smooth
      sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      shader = sh
      shaderFor = bmp
    }
    val sw = src?.width() ?: bmp.width.toFloat()
    val sh2 = src?.height() ?: bmp.height.toFloat()
    val scx = src?.centerX() ?: (bmp.width / 2f)
    val scy = src?.centerY() ?: (bmp.height / 2f)
    val s = max((r - l) / sw, (b - t) / sh2)
    matrix.setScale(s, s)
    matrix.postTranslate((l + r) / 2f - scx * s, (t + b) / 2f - scy * s)
    sh.setLocalMatrix(matrix)
    paint.shader = sh
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    c.drawRoundRect(l, t, r, b, radius, radius, paint)
  }
}

/**
 * APP STORE CARD. The tapped card grows to fill the screen and becomes the new page, while the old screen steps back
 * and dims behind it (like the App Store "Today" cards). Needs the rectangle of the tapped element (`originRect`).
 */
internal class CardExpandTransition : Transition() {
  override val defaultMs = 480
  override fun interpolator(): TimeInterpolator = FAST_OUT_SLOW_IN

  private val card = RectF()
  private val toSheet = Sheet()
  private val fromSheet = Sheet()
  private var d = 1f
  private var bg = Color.BLACK

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    d = req.density
    bg = req.background
    val o = req.originRect
    if (o != null && o.width() > 8f && o.height() > 8f) {
      card.set(o)
    } else {
      // no rectangle: a card around the touch point (or the middle of the screen)
      val cx = if (req.originX > 0f) req.originX else w / 2f
      val cy = if (req.originY > 0f) req.originY else h / 2f
      card.set(cx - 90f * d, cy - 60f * d, cx + 90f * d, cy + 60f * d)
    }
    if (!card.intersect(0f, 0f, w, h) || card.width() < 8f || card.height() < 8f) {
      card.set(w / 2f - 90f * d, h / 2f - 60f * d, w / 2f + 90f * d, h / 2f + 60f * d)
    }
  }

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    val e = p.coerceIn(0f, 1f)
    c.drawColor(bg)
    // the old screen steps back and dims
    val s = 1f - 0.06f * e
    c.save()
    c.scale(s, s, w / 2f, h / 2f)
    c.drawBitmap(from, 0f, 0f, null)
    c.restore()
    c.drawColor(Color.argb((e * 150f).toInt(), 0, 0, 0))
    // the card grows to the whole screen: its own picture turns into the new page
    val l = lf(card.left, 0f, e)
    val t = lf(card.top, 0f, e)
    val r = lf(card.right, w, e)
    val b = lf(card.bottom, h, e)
    val radius = lf(18f * d, 0f, e)
    fromSheet.draw(c, from, l, t, r, b, radius, 1f, card)
    toSheet.draw(c, to, l, t, r, b, radius, smoothStep((e - 0.05f) / 0.40f))
  }
}

internal class CurtainTransition : Transition() {
  override val defaultMs = 1300
  override fun interpolator(): TimeInterpolator = LinearInterpolator()
  private val path = Path()

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    c.drawBitmap(from, 0f, 0f, null)
    Shapes.curtain(path, p, w, h)
    drawThrough(c, to, path)
  }
}

internal class RadarTransition : Transition() {
  override val defaultMs = 1100
  override fun interpolator(): TimeInterpolator = FAST_OUT_SLOW_IN
  private val path = Path()
  private lateinit var req: TransitionRequest
  private val line = Paint(Paint.ANTI_ALIAS_FLAG)

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    this.req = req
  }

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    val ox = req.originX
    val oy = req.originY
    c.drawBitmap(from, 0f, 0f, null)
    Shapes.sweep(path, p, ox, oy, w, h)
    drawThrough(c, to, path)

    if (p > 0.01f && p < 0.985f) {
      val r = hypot(w, h)
      val theta = -90f + 360f * p
      val rad = Math.toRadians(theta.toDouble())
      // glowing trail behind the line
      c.save()
      c.rotate(theta, ox, oy)
      line.style = Paint.Style.FILL
      line.shader = SweepGradient(
        ox, oy,
        intArrayOf(req.accent.withAlpha(0f), req.accent.withAlpha(0f), req.accent.withAlpha(0.7f)),
        floatArrayOf(0f, 0.88f, 1f),
      )
      c.drawCircle(ox, oy, r, line)
      line.shader = null
      c.restore()
      // the radar line itself
      line.style = Paint.Style.STROKE
      line.color = Color.WHITE
      line.strokeWidth = 3f * req.density
      c.drawLine(ox, oy, ox + r * cos(rad).toFloat(), oy + r * sin(rad).toFloat(), line)
      line.style = Paint.Style.FILL
      c.drawCircle(ox, oy, 7f * req.density, line)
    }
  }
}

internal class HexTransition : Transition() {
  override val defaultMs = 900
  override fun interpolator(): TimeInterpolator = FAST_OUT_SLOW_IN
  private val path = Path()
  private val echo = Path()
  private lateinit var req: TransitionRequest
  private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    this.req = req
  }

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    val ox = req.originX
    val oy = req.originY
    c.drawBitmap(from, 0f, 0f, null)
    Shapes.hex(path, p, ox, oy, w, h)
    drawThrough(c, to, path)

    // glowing echo outlines behind the aperture
    val maxR = hypot(w, h) * 1.2f
    for (k in 0..2) {
      val t = (p - k * 0.07f).coerceIn(0f, 1f)
      if (t > 0f && t < 1f) {
        Shapes.hexPoly(echo, ox, oy, t * maxR, -60f + 120f * t)
        line.color = req.accent.withAlpha((1f - t) * 0.9f)
        line.strokeWidth = (6 - k * 2) * req.density
        c.drawPath(echo, line)
      }
    }
  }
}

internal class LiquidPourTransition : Transition() {
  override val defaultMs = 1900
  override fun interpolator(): TimeInterpolator = PathInterpolator(0.4f, 0f, 0.15f, 1f)
  private val path = Path()
  private val edge = FloatArray(Shapes.DRIP_N + 1)
  private var art: LiquidGlassArt? = null
  private var accent = 0

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    art = LiquidGlassArt(req.density)
    accent = req.accent
  }

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    c.drawBitmap(from, 0f, 0f, null)
    Shapes.drip(path, p, w, h, edge)
    drawThrough(c, to, path)
    art?.draw(c, p, accent, w, h)
  }
}

/**
 * BALL ZOOM: no flying around. Where you tap, a glossy rubber ball is born and INFLATES in place: it first squeezes in
 * a little (anticipation), then blows up with a bouncy overshoot until it swallows the whole screen.
 */
internal class BallZoomTransition : Transition() {
  override val defaultMs = 1000
  // easeInOutBack: dips first, overshoots at the end -> elastic ball feeling
  override fun interpolator(): TimeInterpolator = PathInterpolator(0.68f, -0.55f, 0.27f, 1.55f)
  private val path = Path()
  private val matrix = Matrix()
  private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val dim = Paint()
  private lateinit var req: TransitionRequest

  override fun prepare(from: Bitmap, to: Bitmap, w: Float, h: Float, req: TransitionRequest) {
    this.req = req
  }

  override fun draw(c: Canvas, from: Bitmap, to: Bitmap, p: Float, w: Float, h: Float) {
    val ox = req.originX.coerceIn(0f, w)
    val oy = req.originY.coerceIn(0f, h)
    val pp = p.coerceIn(0f, 1f)
    val r0 = 20f * req.density
    val rFull = hypot(max(ox, w - ox), max(oy, h - oy)) * 1.05f
    val r = lf(r0, rFull, p).coerceAtLeast(1f)

    // old screen: pulled towards the tap (zoom in) and darkened
    c.save()
    val s = 1f + 0.16f * smoothStep(pp)
    c.scale(s, s, ox, oy)
    c.drawBitmap(from, 0f, 0f, null)
    c.restore()
    dim.color = Color.argb((0.5f * smoothStep(pp / 0.6f) * 255f).toInt(), 0, 0, 0)
    c.drawRect(0f, 0f, w, h, dim)

    // the ball
    val a = (p / 0.05f).coerceIn(0f, 1f)
    if (a <= 0f) return
    Shapes.ellipse(path, ox, oy, r, r)
    ballPaint.style = Paint.Style.FILL
    ballPaint.shader = RadialGradient(
      ox - r * 0.35f, oy - r * 0.4f, (r * 1.6f).coerceAtLeast(1f), // never <= 0
      intArrayOf(mix(req.accent, Color.WHITE, 0.55f), req.accent, mix(req.accent, Color.BLACK, 0.55f)),
      floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    ballPaint.alpha = (a * 255f).toInt()
    c.drawPath(path, ballPaint)
    ballPaint.shader = null

    // the new screen zooms out of the ball
    val fa = ((pp - 0.45f) / 0.4f).coerceIn(0f, 1f)
    if (fa > 0f) {
      val s2 = 0.8f + 0.2f * smoothStep((pp - 0.3f) / 0.7f)
      matrix.reset()
      matrix.postScale(s2, s2, ox, oy)
      drawThrough(c, to, path, (fa * 255f).toInt(), matrix)
    }
  }
}

internal fun createTransition(name: String): Transition = when (name) {
  "deliveryBike" -> DeliveryTransition(car = false)
  "deliveryCar" -> DeliveryTransition(car = true)
  "liquidPour" -> LiquidPourTransition()
  "dismiss" -> DismissTransition()
  "cardExpand" -> CardExpandTransition()
  "deliveryBikeBag" -> DeliveryTransition(car = false, bag = true)
  "deliveryCarBag" -> DeliveryTransition(car = true, bag = true)
  "ballZoom" -> BallZoomTransition()
  "radarSweep" -> RadarTransition()
  "hexAperture" -> HexTransition()
  "curtainDrop" -> CurtainTransition()
  else -> FadeTransition() // "fade" (used for BACK) and anything unknown
}
