package expo.modules.deliverytransitions

import android.graphics.Color

internal fun lf(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun smoothStep(t: Float): Float {
  val x = t.coerceIn(0f, 1f)
  return x * x * (3f - 2f * x)
}

internal fun easeOutCubic(t: Float): Float {
  val x = t.coerceIn(0f, 1f)
  return 1f - (1f - x) * (1f - x) * (1f - x)
}

internal fun easeInCubic(t: Float): Float {
  val x = t.coerceIn(0f, 1f)
  return x * x * x
}

internal fun easeOutBack(t: Float): Float {
  val x = t.coerceIn(0f, 1f)
  val c1 = 1.70158f
  val c3 = c1 + 1f
  val y = x - 1f
  return 1f + c3 * y * y * y + c1 * y * y
}

internal fun bounceOut(t: Float): Float {
  var x = t.coerceIn(0f, 1f)
  return when {
    x < 1f / 2.75f -> 7.5625f * x * x
    x < 2f / 2.75f -> { x -= 1.5f / 2.75f; 7.5625f * x * x + 0.75f }
    x < 2.5f / 2.75f -> { x -= 2.25f / 2.75f; 7.5625f * x * x + 0.9375f }
    else -> { x -= 2.625f / 2.75f; 7.5625f * x * x + 0.984375f }
  }
}

/** Same RGB, new alpha (0..1). */
internal fun Int.withAlpha(a: Float): Int =
  (this and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

/** Linear mix of two colours (alpha is taken from [a]). */
internal fun mix(a: Int, b: Int, t: Float): Int {
  val k = t.coerceIn(0f, 1f)
  return Color.argb(
    Color.alpha(a),
    (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
  )
}
