package expo.modules.deliverytransitions

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Masks that reveal the NEW screen. All of them write into a reusable [Path]. */
internal object Shapes {
  private val oval = RectF()

  /** Vertical columns that drop from the top and bounce on the floor, one after another. */
  fun curtain(out: Path, p: Float, w: Float, h: Float, columns: Int = 10) {
    out.rewind()
    val cw = w / columns
    for (i in 0 until columns) {
      val local = ((p - i * 0.04f) / 0.5f).coerceIn(0f, 1f)
      val bottom = h * bounceOut(local)
      if (bottom > 0f) out.addRect(i * cw - 1f, 0f, (i + 1) * cw + 1f, bottom, Path.Direction.CW)
    }
  }

  /** Clock-hand sweep around (cx, cy), starting at the top. */
  fun sweep(out: Path, p: Float, cx: Float, cy: Float, w: Float, h: Float) {
    out.rewind()
    val pp = p.coerceIn(0f, 1f)
    if (pp >= 0.999f) {
      out.addRect(0f, 0f, w, h, Path.Direction.CW)
      return
    }
    if (pp <= 0f) return
    val r = hypot(w, h)
    oval.set(cx - r, cy - r, cx + r, cy + r)
    out.moveTo(cx, cy)
    out.arcTo(oval, -90f, 360f * pp)
    out.close()
  }

  fun hexPoly(out: Path, cx: Float, cy: Float, r: Float, rotDeg: Float) {
    out.rewind()
    for (k in 0..5) {
      val a = Math.toRadians((rotDeg + 60f * k).toDouble())
      val x = cx + r * cos(a).toFloat()
      val y = cy + r * sin(a).toFloat()
      if (k == 0) out.moveTo(x, y) else out.lineTo(x, y)
    }
    out.close()
  }

  /** A hexagonal aperture that grows (and spins) from (cx, cy). */
  fun hex(out: Path, p: Float, cx: Float, cy: Float, w: Float, h: Float) {
    val r = p.coerceIn(0f, 1f) * hypot(w, h) * 1.2f
    hexPoly(out, cx, cy, r, -60f + 120f * p)
  }

  fun ellipse(out: Path, cx: Float, cy: Float, rx: Float, ry: Float) {
    out.rewind()
    val a = max(rx, 0.1f)
    val b = max(ry, 0.1f)
    out.addOval(cx - a, cy - b, cx + a, cy + b, Path.Direction.CW)
  }

  // ───────────── thick liquid pouring down the screen, with drips ─────────────
  const val DRIP_N = 96
  val dripX = floatArrayOf(0.07f, 0.19f, 0.33f, 0.50f, 0.64f, 0.79f, 0.93f)
  private val dripLen = floatArrayOf(0.13f, 0.21f, 0.10f, 0.26f, 0.15f, 0.23f, 0.11f) // x screen height
  private val dripW = floatArrayOf(0.040f, 0.050f, 0.035f, 0.055f, 0.042f, 0.050f, 0.038f) // x screen width

  /** 0 -> 1 -> 0 envelope of drip [k]; drips are staggered so they do not move together. */
  fun dripEnv(p: Float, k: Int): Float {
    val t = (p * 1.25f - k * 0.045f).coerceIn(0f, 1f)
    return sin(PI.toFloat() * t)
  }

  /** y of the liquid's lower edge at DRIP_N + 1 evenly spaced x positions. */
  fun dripEdge(p: Float, w: Float, h: Float, out: FloatArray) {
    val maxL = h * 0.26f
    val base = (h + maxL * 1.1f) * p
    val waveAmp = w * 0.012f * sin(PI.toFloat() * p.coerceIn(0f, 1f))
    for (i in 0..DRIP_N) {
      val x = w * i / DRIP_N
      var y = base + waveAmp * sin(x / w * 12f + p * 9f)
      for (k in dripX.indices) {
        val dx = (x - dripX[k] * w) / (dripW[k] * w)
        y += h * dripLen[k] * dripEnv(p, k) * exp(-dx * dx)
      }
      out[i] = y
    }
  }

  fun drip(out: Path, p: Float, w: Float, h: Float, edge: FloatArray) {
    out.rewind()
    if (p <= 0.001f) return
    dripEdge(p, w, h, edge)
    out.moveTo(0f, -10f)
    out.lineTo(w, -10f)
    for (i in DRIP_N downTo 0) out.lineTo(w * i / DRIP_N, edge[i])
    out.close()
  }
}
