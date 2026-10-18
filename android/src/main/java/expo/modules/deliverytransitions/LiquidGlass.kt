package expo.modules.deliverytransitions

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.roundToInt

/**
 * The "glass" look of the liquid pour: frosted lip behind the edge, a glass rim with thickness,
 * specular streaks and little glass droplets falling off the drip tips.
 */
internal class LiquidGlassArt(private val d: Float) {
  private val edge = FloatArray(Shapes.DRIP_N + 1)
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val rim = Path()
  private val lip = Path()

  fun draw(c: Canvas, p: Float, tint: Int, w: Float, h: Float) {
    if (p <= 0.002f || p >= 0.997f) return
    Shapes.dripEdge(p, w, h, edge)
    val n = Shapes.DRIP_N
    val colW = w / n
    val lipH = 70f * d

    // frosted lip right behind the edge: stacked translucent bands (strongest at the edge)
    paint.style = Paint.Style.FILL
    paint.shader = null
    for (k in 0 until 6) {
      val off = lipH * (1f - k / 6f)
      lip.rewind()
      lip.moveTo(0f, edge[0] - off)
      for (i in 1..n) lip.lineTo(i * colW, edge[i] - off)
      for (i in n downTo 0) lip.lineTo(i * colW, edge[i])
      lip.close()
      paint.color = Color.WHITE.withAlpha(0.05f)
      c.drawPath(lip, paint)
      if (k >= 2) {
        paint.color = tint.withAlpha(0.13f)
        c.drawPath(lip, paint)
      }
    }

    // glass rim (bright line + a darker line below it = thickness)
    rim.rewind()
    rim.moveTo(0f, edge[0])
    for (i in 1..n) rim.lineTo(i * colW, edge[i])
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND
    c.save()
    c.translate(0f, 3f * d)
    paint.strokeWidth = 2f * d
    paint.color = Color.BLACK.withAlpha(0.2f)
    c.drawPath(rim, paint)
    c.restore()
    paint.strokeWidth = 3f * d
    paint.color = Color.WHITE.withAlpha(0.95f)
    c.drawPath(rim, paint)

    // specular streaks + falling droplets at every drip
    for (k in Shapes.dripX.indices) {
      val env = Shapes.dripEnv(p, k)
      if (env < 0.12f) continue
      val tx = Shapes.dripX[k] * w
      val idx = (Shapes.dripX[k] * n).roundToInt().coerceIn(0, n)
      val tipY = edge[idx]

      paint.style = Paint.Style.STROKE
      paint.strokeCap = Paint.Cap.ROUND
      paint.strokeWidth = 2.5f * d
      paint.shader = LinearGradient(
        0f, tipY - 90f * d, 0f, tipY - 4f * d,
        Color.WHITE.withAlpha(0f), Color.WHITE.withAlpha(0.65f), Shader.TileMode.CLAMP,
      )
      c.drawLine(tx - 3f * d, tipY - 90f * d, tx - 3f * d, tipY - 4f * d, paint)

      val rr = 9f * d * (0.6f + 0.4f * env)
      val cy = tipY + rr * 1.6f + 14f * d * env
      paint.style = Paint.Style.FILL
      paint.shader = RadialGradient(
        tx - rr * 0.3f, cy - rr * 0.3f, rr * 1.4f,
        intArrayOf(Color.WHITE.withAlpha(0.75f), tint.withAlpha(0.75f), tint.withAlpha(0.95f)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
      )
      c.drawCircle(tx, cy, rr, paint)
      paint.shader = null
      paint.color = Color.WHITE.withAlpha(0.9f)
      c.drawCircle(tx - rr * 0.35f, cy - rr * 0.4f, rr * 0.22f, paint)
    }
    paint.shader = null
  }
}
