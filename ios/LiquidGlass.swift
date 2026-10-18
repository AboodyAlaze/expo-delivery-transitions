import UIKit

/// The "glass" look of the liquid pour: frosted lip behind the edge, a glass rim with thickness,
/// specular streaks and little glass droplets falling off the drip tips.
final class LiquidGlassArt {
  private let d: F

  init(d: F) {
    self.d = d
  }

  func draw(_ c: CGContext, p: F, tint: UIColor, w: F, h: F) {
    if p <= 0.002 || p >= 0.997 { return }
    let n = Shapes.dripN
    let edge = Shapes.dripEdge(p: p, w: w, h: h)
    let colW = w / F(n)
    let lipH = 70 * d

    // frosted lip right behind the edge: stacked translucent bands (strongest at the edge)
    for k in 0..<6 {
      let off = lipH * (1 - F(k) / 6)
      let lip = CGMutablePath()
      lip.move(to: CGPoint(x: 0, y: edge[0] - off))
      for i in 1...n { lip.addLine(to: CGPoint(x: F(i) * colW, y: edge[i] - off)) }
      for i in stride(from: n, through: 0, by: -1) { lip.addLine(to: CGPoint(x: F(i) * colW, y: edge[i])) }
      lip.closeSubpath()
      c.addPath(lip)
      c.setFillColor(UIColor.white.a(0.05).cgColor)
      c.fillPath()
      if k >= 2 {
        c.addPath(lip)
        c.setFillColor(tint.a(0.13).cgColor)
        c.fillPath()
      }
    }

    // glass rim (bright line + a darker line below it = thickness)
    let rim = CGMutablePath()
    rim.move(to: CGPoint(x: 0, y: edge[0]))
    for i in 1...n { rim.addLine(to: CGPoint(x: F(i) * colW, y: edge[i])) }
    c.setLineCap(.round)
    c.setLineJoin(.round)
    c.saveGState()
    c.translateBy(x: 0, y: 3 * d)
    c.setLineWidth(2 * d)
    c.setStrokeColor(UIColor.black.a(0.2).cgColor)
    c.addPath(rim)
    c.strokePath()
    c.restoreGState()
    c.setLineWidth(3 * d)
    c.setStrokeColor(UIColor.white.a(0.95).cgColor)
    c.addPath(rim)
    c.strokePath()

    // specular streaks + falling droplets at every drip
    let space = CGColorSpaceCreateDeviceRGB()
    for k in 0..<Shapes.dripX.count {
      let env = Shapes.dripEnv(p, k)
      if env < 0.12 { continue }
      let tx = Shapes.dripX[k] * w
      let idx = min(max(Int((Shapes.dripX[k] * F(n)).rounded()), 0), n)
      let tipY = edge[idx]

      // streak: a thin rectangle filled with a vertical gradient
      c.saveGState()
      c.clip(to: CGRect(x: tx - 3 * d - 1.25 * d, y: tipY - 90 * d, width: 2.5 * d, height: 86 * d))
      let streakColors = [UIColor.white.a(0).cgColor, UIColor.white.a(0.65).cgColor] as CFArray
      if let g = CGGradient(colorsSpace: space, colors: streakColors, locations: [0, 1]) {
        c.drawLinearGradient(
          g, start: CGPoint(x: 0, y: tipY - 90 * d), end: CGPoint(x: 0, y: tipY - 4 * d), options: [])
      }
      c.restoreGState()

      // droplet
      let rr = 9 * d * (0.6 + 0.4 * env)
      let cy = tipY + rr * 1.6 + 14 * d * env
      c.saveGState()
      c.addEllipse(in: CGRect(x: tx - rr, y: cy - rr, width: 2 * rr, height: 2 * rr))
      c.clip()
      let dropColors = [UIColor.white.a(0.75).cgColor, tint.a(0.75).cgColor, tint.a(0.95).cgColor] as CFArray
      if let g = CGGradient(colorsSpace: space, colors: dropColors, locations: [0, 0.5, 1]) {
        let center = CGPoint(x: tx - rr * 0.3, y: cy - rr * 0.3)
        c.drawRadialGradient(
          g, startCenter: center, startRadius: 0, endCenter: center, endRadius: rr * 1.4,
          options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
      }
      c.restoreGState()
      c.setFillColor(UIColor.white.a(0.9).cgColor)
      c.fillEllipse(
        in: CGRect(
          x: tx - rr * 0.35 - rr * 0.22, y: cy - rr * 0.4 - rr * 0.22, width: rr * 0.44, height: rr * 0.44))
    }
  }
}
