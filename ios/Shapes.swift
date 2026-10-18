import UIKit

/// Masks that reveal the NEW screen (CGPath, y goes down like in UIKit).
enum Shapes {
  /// Vertical columns that drop from the top and bounce on the floor, one after another.
  static func curtain(p: F, w: F, h: F, columns: Int = 10) -> CGPath {
    let path = CGMutablePath()
    let cw = w / F(columns)
    for i in 0..<columns {
      let local = clamp01((p - F(i) * 0.04) / 0.5)
      let bottom = h * bounceOut(local)
      if bottom > 0 {
        path.addRect(CGRect(x: F(i) * cw - 1, y: 0, width: cw + 2, height: bottom))
      }
    }
    return path
  }

  /// Clock-hand sweep around (cx, cy), starting at the top.
  static func sweep(p: F, cx: F, cy: F, w: F, h: F) -> CGPath {
    let pp = clamp01(p)
    if pp >= 0.999 { return CGPath(rect: CGRect(x: 0, y: 0, width: w, height: h), transform: nil) }
    if pp <= 0 { return CGMutablePath() }
    let r = hypot(w, h)
    let center = CGPoint(x: cx, y: cy)
    let path = UIBezierPath()
    path.move(to: center)
    path.addArc(
      withCenter: center, radius: r,
      startAngle: -F.pi / 2, endAngle: -F.pi / 2 + 2 * F.pi * pp, clockwise: true)
    path.close()
    return path.cgPath
  }

  static func hexPoly(cx: F, cy: F, r: F, rotDeg: F) -> CGPath {
    let path = CGMutablePath()
    for k in 0..<6 {
      let a = (rotDeg + 60 * F(k)) * F.pi / 180
      let pt = CGPoint(x: cx + r * cos(a), y: cy + r * sin(a))
      if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
    }
    path.closeSubpath()
    return path
  }

  /// A hexagonal aperture that grows (and spins) from (cx, cy).
  static func hex(p: F, cx: F, cy: F, w: F, h: F) -> CGPath {
    let r = clamp01(p) * hypot(w, h) * 1.2
    return hexPoly(cx: cx, cy: cy, r: r, rotDeg: -60 + 120 * p)
  }

  static func ellipse(cx: F, cy: F, rx: F, ry: F) -> CGPath {
    let a = max(rx, 0.1)
    let b = max(ry, 0.1)
    return CGPath(ellipseIn: CGRect(x: cx - a, y: cy - b, width: 2 * a, height: 2 * b), transform: nil)
  }

  // ───────────── thick liquid pouring down the screen, with drips ─────────────
  static let dripN = 96
  static let dripX: [F] = [0.07, 0.19, 0.33, 0.50, 0.64, 0.79, 0.93]
  private static let dripLen: [F] = [0.13, 0.21, 0.10, 0.26, 0.15, 0.23, 0.11]  // x screen height
  private static let dripW: [F] = [0.040, 0.050, 0.035, 0.055, 0.042, 0.050, 0.038]  // x screen width

  /// 0 -> 1 -> 0 envelope of drip k; drips are staggered so they do not move together.
  static func dripEnv(_ p: F, _ k: Int) -> F {
    let t = clamp01(p * 1.25 - F(k) * 0.045)
    return sin(F.pi * t)
  }

  /// y of the liquid's lower edge at dripN + 1 evenly spaced x positions.
  static func dripEdge(p: F, w: F, h: F) -> [F] {
    var out = [F](repeating: 0, count: dripN + 1)
    let maxL = h * 0.26
    let base = (h + maxL * 1.1) * p
    let waveAmp = w * 0.012 * sin(F.pi * clamp01(p))
    for i in 0...dripN {
      let x = w * F(i) / F(dripN)
      var y = base + waveAmp * sin(x / w * 12 + p * 9)
      for k in 0..<dripX.count {
        let dx = (x - dripX[k] * w) / (dripW[k] * w)
        y += h * dripLen[k] * dripEnv(p, k) * exp(-dx * dx)
      }
      out[i] = y
    }
    return out
  }

  static func drip(p: F, w: F, h: F) -> CGPath {
    let path = CGMutablePath()
    if p <= 0.001 { return path }
    let e = dripEdge(p: p, w: w, h: h)
    path.move(to: CGPoint(x: 0, y: -10))
    path.addLine(to: CGPoint(x: w, y: -10))
    for i in stride(from: dripN, through: 0, by: -1) {
      path.addLine(to: CGPoint(x: w * F(i) / F(dripN), y: e[i]))
    }
    path.closeSubpath()
    return path
  }
}
