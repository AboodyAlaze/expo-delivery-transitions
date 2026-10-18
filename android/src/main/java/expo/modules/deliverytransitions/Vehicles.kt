package expo.modules.deliverytransitions

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Top-down delivery scooter, delivery car and the parcel. Coordinates are in "dp units" (1 unit = 1 dp * scale),
 * the vehicle faces +x at 0 degrees, [headingDeg] rotates it (90 = driving DOWN the screen).
 * The logo is counter-rotated so it always reads upright.
 */
internal class VehicleArt(private val d: Float) {
  private val p = Paint(Paint.ANTI_ALIAS_FLAG)
  private val r = RectF()
  private val path = Path()

  private companion object {
    val INK = 0xFF15151B.toInt()
    val GRAPHITE = 0xFF2E2E38.toInt()
    val SKIN = 0xFFF2C29B.toInt()
    val GREY = 0xFF9AA0AA.toInt()
    val HELMET_GLASS = 0xFF1D3F56.toInt()
    val LAMP = 0xFFFFF3B0.toInt()
    val LAMP_GLOW = 0x55FFF3B0
    val LAMP_WARM = 0xFFFFF6C2.toInt()
    val RED = 0xFFE53935.toInt()
    val PORCELAIN = 0xFFF1F3F6.toInt()
    val DEEP = 0xFFE0B400.toInt()
    val GLASS = 0xFF173F58.toInt()
    val TRUNK_INSIDE = 0xFF1A1A20.toInt()
    val SHADE = 0x33000000
    val BOX_SHADE = 0x1F000000
    val BEAM = 0x2EFFF3B0
  }

  private fun fill(color: Int) {
    p.style = Paint.Style.FILL
    p.shader = null
    p.color = color
  }

  private fun rr(c: Canvas, color: Int, x: Float, y: Float, w: Float, h: Float, rad: Float, u: Float) {
    fill(color)
    r.set(x * u, y * u, (x + w) * u, (y + h) * u)
    c.drawRoundRect(r, rad * u, rad * u, p)
  }

  private fun dot(c: Canvas, color: Int, rad: Float, x: Float, y: Float, u: Float) {
    fill(color)
    c.drawCircle(x * u, y * u, rad * u, p)
  }

  private fun oval(c: Canvas, color: Int, x: Float, y: Float, w: Float, h: Float, u: Float) {
    fill(color)
    r.set(x * u, y * u, (x + w) * u, (y + h) * u)
    c.drawOval(r, p)
  }

  private fun line(c: Canvas, color: Int, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, u: Float) {
    p.style = Paint.Style.STROKE
    p.shader = null
    p.color = color
    p.strokeWidth = width * u
    p.strokeCap = Paint.Cap.ROUND
    c.drawLine(x1 * u, y1 * u, x2 * u, y2 * u, p)
    p.strokeCap = Paint.Cap.BUTT
    p.style = Paint.Style.FILL
  }

  fun bike(c: Canvas, cx: Float, cy: Float, headingDeg: Float, armOut: Float, scale: Float = 1f, lookDeg: Float = 0f) {
    val u = d * scale
    c.save()
    c.translate(cx, cy)
    c.rotate(headingDeg)

    // contact shadows (per part, so the shape stays readable)
    rr(c, SHADE, -33f, -1f, 26f, 10f, 5f, u)
    rr(c, SHADE, 47f, -1f, 26f, 10f, 5f, u)
    rr(c, SHADE, -27f, -8f, 70f, 24f, 12f, u)
    rr(c, SHADE, -63f, -18f, 42f, 44f, 8f, u)

    // wheels + fork
    rr(c, INK, -36f, -5f, 26f, 10f, 5f, u)
    rr(c, INK, 44f, -5f, 26f, 10f, 5f, u)
    line(c, GRAPHITE, 46f, 0f, 22f, 0f, 5f, u)

    // body with a brand-yellow stripe
    rr(c, GRAPHITE, -30f, -12f, 70f, 24f, 12f, u)
    rr(c, Logo.YELLOW, -22f, -3f, 52f, 6f, 3f, u)

    // headlight
    dot(c, LAMP_GLOW, 11f, 56f, 0f, u)
    dot(c, LAMP, 4.5f, 54f, 0f, u)

    // handlebar + mirrors
    line(c, INK, 24f, -25f, 24f, 25f, 5f, u)
    dot(c, GREY, 3.5f, 19f, -28f, u)
    dot(c, GREY, 3.5f, 19f, 28f, u)

    // arms: the left one holds the bar, the right one swings out when he throws
    val handLx = 24f
    val handLy = 23f
    val handRx = lf(24f, 8f, armOut)
    val handRy = lf(-23f, -50f, armOut)
    line(c, Logo.NAVY, 2f, 12f, handLx, handLy, 9f, u)
    line(c, Logo.NAVY, 2f, -12f, handRx, handRy, 9f, u)
    dot(c, SKIN, 4.5f, handLx, handLy, u)
    dot(c, SKIN, 4.5f, handRx, handRy, u)

    // shoulders (navy jacket + reflective stripe) and helmet
    oval(c, Logo.NAVY, -9f, -19f, 27f, 38f, u)
    rr(c, Logo.YELLOW, -6f, -14f, 3.5f, 28f, 1.7f, u)
    dot(c, Color.WHITE, 11f, 6f, 0f, u)
    val lr = Math.toRadians(lookDeg.toDouble())
    dot(c, HELMET_GLASS, 5.5f, 6f + 6f * cos(lr).toFloat(), 6f * sin(lr).toFloat(), u)
    rr(c, Logo.YELLOW, -5f, -1.5f, 12f, 3f, 1.5f, u)

    // delivery box with the logo (kept upright)
    rr(c, PORCELAIN, -66f, -22f, 42f, 44f, 8f, u)
    rr(c, BOX_SHADE, -66f, 15f, 42f, 7f, 6f, u)
    rr(c, Logo.YELLOW, -66f, -22f, 42f, 4f, 2f, u)
    c.save()
    c.rotate(-headingDeg, -45f * u, 0f)
    Logo.draw(c, -45f * u, 1f * u, 30f * u)
    c.restore()
    rr(c, RED, -69f, -8f, 3f, 16f, 1.5f, u)

    c.restore()
  }

  fun car(c: Canvas, cx: Float, cy: Float, headingDeg: Float, braking: Float, trunk: Float, scale: Float = 1f, hazard: Float = 0f) {
    val u = d * scale
    c.save()
    c.translate(cx, cy)
    c.rotate(headingDeg)

    // headlight beams
    path.rewind()
    path.moveTo(62f * u, -18f * u); path.lineTo(120f * u, -36f * u); path.lineTo(120f * u, -4f * u); path.close()
    path.moveTo(62f * u, 18f * u); path.lineTo(120f * u, 4f * u); path.lineTo(120f * u, 36f * u); path.close()
    fill(BEAM)
    c.drawPath(path, p)

    // shadow, wheels, body
    rr(c, SHADE, -60f, -22f, 128f, 56f, 20f, u)
    rr(c, INK, 30f, -34f, 24f, 8f, 3f, u)
    rr(c, INK, 30f, 26f, 24f, 8f, 3f, u)
    rr(c, INK, -52f, -34f, 24f, 8f, 3f, u)
    rr(c, INK, -52f, 26f, 24f, 8f, 3f, u)
    rr(c, Logo.YELLOW, -64f, -28f, 128f, 56f, 20f, u)
    p.style = Paint.Style.STROKE
    p.color = SHADE
    p.strokeWidth = 1.5f * u
    r.set(-64f * u, -28f * u, 64f * u, 28f * u)
    c.drawRoundRect(r, 20f * u, 20f * u, p)
    p.style = Paint.Style.FILL

    // hood, windshield, roof, rear window
    rr(c, DEEP, 32f, -22f, 26f, 44f, 12f, u)
    rr(c, GLASS, 6f, -23f, 22f, 46f, 9f, u)
    rr(c, 0x33FFFFFF, 8f, -21f, 6f, 42f, 3f, u)
    rr(c, PORCELAIN, -30f, -24f, 38f, 48f, 12f, u)
    rr(c, GLASS, -43f, -21f, 12f, 42f, 6f, u)

    // logo on the roof (kept upright)
    c.save()
    c.rotate(-headingDeg, -11f * u, 0f)
    Logo.draw(c, -11f * u, 0f, 31f * u)
    c.restore()

    // mirrors
    rr(c, DEEP, 14f, -34f, 9f, 7f, 3f, u)
    rr(c, DEEP, 14f, 27f, 9f, 7f, 3f, u)

    // lights (+ brake glow)
    rr(c, LAMP_WARM, 58f, -23f, 5f, 13f, 2.5f, u)
    rr(c, LAMP_WARM, 58f, 10f, 5f, 13f, 2.5f, u)
    rr(c, RED, -66f, -23f, 4f, 13f, 2f, u)
    rr(c, RED, -66f, 10f, 4f, 13f, 2f, u)
    if (braking > 0f) {
      val glow = 0xFFFF3B30.toInt().withAlpha(0.35f * braking)
      dot(c, glow, 15f, -69f, -16.5f, u)
      dot(c, glow, 15f, -69f, 16.5f, u)
    }

    // hazard lights (blinking)
    if (hazard > 0f) {
      val glow = 0xFFFFA000.toInt().withAlpha(0.55f * hazard)
      val core = 0xFFFFC107.toInt().withAlpha(hazard)
      val xs = floatArrayOf(63f, 63f, -67f, -67f)
      val ys = floatArrayOf(-27f, 27f, -27f, 27f)
      for (i in xs.indices) {
        dot(c, glow, 12f, xs[i], ys[i], u)
        dot(c, core, 3.5f, xs[i], ys[i], u)
      }
    }

    // trunk: the lid folds towards its hinge and shows the dark inside
    if (trunk > 0f) rr(c, TRUNK_INSIDE, -62f, -21f, 18f, 42f, 5f, u)
    val lidW = lf(20f, 7f, trunk)
    rr(c, DEEP, -44f - lidW, -22f, lidW, 44f, 6f, u)

    c.restore()
  }

  /** The parcel: a light box with a tape in the colour of the card it becomes, the logo printed on it. Sizes in PIXELS. */
  fun parcel(c: Canvas, cx: Float, cy: Float, size: Float, rotDeg: Float, color: Int, alpha: Float) {
    if (alpha <= 0f) return
    c.save()
    c.rotate(rotDeg, cx, cy)
    val half = size / 2f
    val rad = size * 0.16f
    val left = cx - half
    val top = cy - half

    fill(Color.BLACK.withAlpha(0.15f * alpha))
    r.set(left + 3f, top + 4f, left + 3f + size, top + 4f + size)
    c.drawRoundRect(r, rad, rad, p)

    fill(PORCELAIN.withAlpha(alpha))
    r.set(left, top, left + size, top + size)
    c.drawRoundRect(r, rad, rad, p)

    p.style = Paint.Style.STROKE
    p.color = Color.BLACK.withAlpha(0.12f * alpha)
    p.strokeWidth = size * 0.03f
    c.drawRoundRect(r, rad, rad, p)
    p.style = Paint.Style.FILL

    fill(color.withAlpha(alpha))
    c.drawRect(left, top + size * 0.14f, left + size, top + size * 0.30f, p)

    Logo.draw(c, cx, cy + size * 0.12f, size * 0.44f, alpha)
    c.restore()
  }
}
