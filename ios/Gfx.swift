import UIKit

typealias F = CGFloat

@inline(__always) func lf(_ a: F, _ b: F, _ t: F) -> F { return a + (b - a) * t }
@inline(__always) func clamp01(_ x: F) -> F { return min(max(x, 0), 1) }

func smoothStep(_ t: F) -> F {
  let x = clamp01(t)
  return x * x * (3 - 2 * x)
}

func easeOutCubic(_ t: F) -> F {
  let x = clamp01(t)
  return 1 - (1 - x) * (1 - x) * (1 - x)
}

func easeInCubic(_ t: F) -> F {
  let x = clamp01(t)
  return x * x * x
}

func easeOutBack(_ t: F) -> F {
  let x = clamp01(t)
  let c1: F = 1.70158
  let c3 = c1 + 1
  let y = x - 1
  return 1 + c3 * y * y * y + c1 * y * y
}

func bounceOut(_ t: F) -> F {
  var x = clamp01(t)
  if x < 1 / 2.75 {
    return 7.5625 * x * x
  } else if x < 2 / 2.75 {
    x -= 1.5 / 2.75
    return 7.5625 * x * x + 0.75
  } else if x < 2.5 / 2.75 {
    x -= 2.25 / 2.75
    return 7.5625 * x * x + 0.9375
  } else {
    x -= 2.625 / 2.75
    return 7.5625 * x * x + 0.984375
  }
}

extension UIColor {
  convenience init(hex: UInt32, alpha: F = 1) {
    self.init(
      red: F((hex >> 16) & 0xFF) / 255,
      green: F((hex >> 8) & 0xFF) / 255,
      blue: F(hex & 0xFF) / 255,
      alpha: alpha
    )
  }

  /// Same colour, new alpha.
  func a(_ alpha: F) -> UIColor { return withAlphaComponent(clamp01(alpha)) }

  /// Linear mix of two colours (alpha is taken from `self`).
  func mixed(_ other: UIColor, _ t: F) -> UIColor {
    var r1: F = 0, g1: F = 0, b1: F = 0, a1: F = 0
    var r2: F = 0, g2: F = 0, b2: F = 0, a2: F = 0
    getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
    other.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
    let k = clamp01(t)
    return UIColor(red: lf(r1, r2, k), green: lf(g1, g2, k), blue: lf(b1, b2, k), alpha: a1)
  }
}

/// CSS-like cubic-bezier easing. The y control points may leave 0...1 (anticipation / overshoot).
struct CubicBezier {
  let x1: Double
  let y1: Double
  let x2: Double
  let y2: Double

  private func bx(_ t: Double) -> Double {
    let u = 1 - t
    return 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t
  }

  private func by(_ t: Double) -> Double {
    let u = 1 - t
    return 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t
  }

  private func dbx(_ t: Double) -> Double {
    let u = 1 - t
    return 3 * u * u * x1 + 6 * u * t * (x2 - x1) + 3 * t * t * (1 - x2)
  }

  func value(_ x: Double) -> Double {
    if x <= 0 { return 0 }
    if x >= 1 { return 1 }
    var t = x
    for _ in 0..<8 {
      let e = bx(t) - x
      if abs(e) < 1e-6 { return by(t) }
      let d = dbx(t)
      if abs(d) < 1e-6 { break }
      t -= e / d
    }
    var lo = 0.0
    var hi = 1.0
    t = x
    for _ in 0..<40 {
      let e = bx(t) - x
      if abs(e) < 1e-6 { break }
      if e > 0 { hi = t } else { lo = t }
      t = (lo + hi) / 2
    }
    return by(t)
  }
}
