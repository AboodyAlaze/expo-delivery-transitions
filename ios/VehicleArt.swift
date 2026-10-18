import UIKit

/// Top-down delivery scooter, delivery car and the parcel. Coordinates are in "units" (1 unit = 1 pt * scale),
/// the vehicle faces +x at 0 degrees, `heading` rotates it (90 = driving DOWN the screen).
/// The logo is counter-rotated so it always reads upright.
final class VehicleArt {
  private let d: F

  init(d: F) {
    self.d = d
  }

  private static let ink = UIColor(hex: 0x15151B)
  private static let graphite = UIColor(hex: 0x2E2E38)
  private static let skin = UIColor(hex: 0xF2C29B)
  private static let grey = UIColor(hex: 0x9AA0AA)
  private static let helmetGlass = UIColor(hex: 0x1D3F56)
  private static let lamp = UIColor(hex: 0xFFF3B0)
  private static let lampGlow = UIColor(hex: 0xFFF3B0, alpha: 0.33)
  private static let lampWarm = UIColor(hex: 0xFFF6C2)
  private static let red = UIColor(hex: 0xE53935)
  private static let porcelain = UIColor(hex: 0xF1F3F6)
  private static let deep = UIColor(hex: 0xE0B400)
  private static let glass = UIColor(hex: 0x173F58)
  private static let trunkInside = UIColor(hex: 0x1A1A20)
  private static let shade = UIColor(white: 0, alpha: 0.2)
  private static let boxShade = UIColor(white: 0, alpha: 0.12)
  private static let beam = UIColor(hex: 0xFFF3B0, alpha: 0.18)
  private static let glassShine = UIColor(white: 1, alpha: 0.2)

  private func rr(_ c: CGContext, _ color: UIColor, _ x: F, _ y: F, _ w: F, _ h: F, _ rad: F, _ u: F) {
    c.setFillColor(color.cgColor)
    let rect = CGRect(x: x * u, y: y * u, width: w * u, height: h * u)
    c.addPath(UIBezierPath(roundedRect: rect, cornerRadius: rad * u).cgPath)
    c.fillPath()
  }

  private func dot(_ c: CGContext, _ color: UIColor, _ rad: F, _ x: F, _ y: F, _ u: F) {
    c.setFillColor(color.cgColor)
    c.fillEllipse(in: CGRect(x: (x - rad) * u, y: (y - rad) * u, width: 2 * rad * u, height: 2 * rad * u))
  }

  private func oval(_ c: CGContext, _ color: UIColor, _ x: F, _ y: F, _ w: F, _ h: F, _ u: F) {
    c.setFillColor(color.cgColor)
    c.fillEllipse(in: CGRect(x: x * u, y: y * u, width: w * u, height: h * u))
  }

  private func line(
    _ c: CGContext, _ color: UIColor, _ x1: F, _ y1: F, _ x2: F, _ y2: F, _ width: F, _ u: F
  ) {
    c.setStrokeColor(color.cgColor)
    c.setLineWidth(width * u)
    c.setLineCap(.round)
    c.move(to: CGPoint(x: x1 * u, y: y1 * u))
    c.addLine(to: CGPoint(x: x2 * u, y: y2 * u))
    c.strokePath()
  }

  func bike(_ c: CGContext, cx: F, cy: F, heading: F, armOut: F, scale: F = 1, look: F = 0) {
    let u = d * scale
    c.saveGState()
    c.translateBy(x: cx, y: cy)
    c.rotate(by: heading * F.pi / 180)

    // contact shadows (per part, so the shape stays readable)
    rr(c, VehicleArt.shade, -33, -1, 26, 10, 5, u)
    rr(c, VehicleArt.shade, 47, -1, 26, 10, 5, u)
    rr(c, VehicleArt.shade, -27, -8, 70, 24, 12, u)
    rr(c, VehicleArt.shade, -63, -18, 42, 44, 8, u)

    // wheels + fork
    rr(c, VehicleArt.ink, -36, -5, 26, 10, 5, u)
    rr(c, VehicleArt.ink, 44, -5, 26, 10, 5, u)
    line(c, VehicleArt.graphite, 46, 0, 22, 0, 5, u)

    // body with a brand-yellow stripe
    rr(c, VehicleArt.graphite, -30, -12, 70, 24, 12, u)
    rr(c, Logo.yellow, -22, -3, 52, 6, 3, u)

    // headlight
    dot(c, VehicleArt.lampGlow, 11, 56, 0, u)
    dot(c, VehicleArt.lamp, 4.5, 54, 0, u)

    // handlebar + mirrors
    line(c, VehicleArt.ink, 24, -25, 24, 25, 5, u)
    dot(c, VehicleArt.grey, 3.5, 19, -28, u)
    dot(c, VehicleArt.grey, 3.5, 19, 28, u)

    // arms: the left one holds the bar, the right one swings out when he throws
    let handLx: F = 24
    let handLy: F = 23
    let handRx = lf(24, 8, armOut)
    let handRy = lf(-23, -50, armOut)
    line(c, Logo.navy, 2, 12, handLx, handLy, 9, u)
    line(c, Logo.navy, 2, -12, handRx, handRy, 9, u)
    dot(c, VehicleArt.skin, 4.5, handLx, handLy, u)
    dot(c, VehicleArt.skin, 4.5, handRx, handRy, u)

    // shoulders (navy jacket + reflective stripe) and helmet
    oval(c, Logo.navy, -9, -19, 27, 38, u)
    rr(c, Logo.yellow, -6, -14, 3.5, 28, 1.7, u)
    dot(c, UIColor.white, 11, 6, 0, u)
    let lr = look * F.pi / 180
    dot(c, VehicleArt.helmetGlass, 5.5, 6 + 6 * cos(lr), 6 * sin(lr), u)
    rr(c, Logo.yellow, -5, -1.5, 12, 3, 1.5, u)

    // delivery box with the logo (kept upright)
    rr(c, VehicleArt.porcelain, -66, -22, 42, 44, 8, u)
    rr(c, VehicleArt.boxShade, -66, 15, 42, 7, 6, u)
    rr(c, Logo.yellow, -66, -22, 42, 4, 2, u)
    c.saveGState()
    c.translateBy(x: -45 * u, y: 0)
    c.rotate(by: -heading * F.pi / 180)
    Logo.draw(c, cx: 0, cy: 1 * u, height: 30 * u)
    c.restoreGState()
    rr(c, VehicleArt.red, -69, -8, 3, 16, 1.5, u)

    c.restoreGState()
  }

  func car(
    _ c: CGContext, cx: F, cy: F, heading: F, braking: F, trunk: F, scale: F = 1, hazard: F = 0
  ) {
    let u = d * scale
    c.saveGState()
    c.translateBy(x: cx, y: cy)
    c.rotate(by: heading * F.pi / 180)

    // headlight beams
    let beamPath = CGMutablePath()
    beamPath.move(to: CGPoint(x: 62 * u, y: -18 * u))
    beamPath.addLine(to: CGPoint(x: 120 * u, y: -36 * u))
    beamPath.addLine(to: CGPoint(x: 120 * u, y: -4 * u))
    beamPath.closeSubpath()
    beamPath.move(to: CGPoint(x: 62 * u, y: 18 * u))
    beamPath.addLine(to: CGPoint(x: 120 * u, y: 4 * u))
    beamPath.addLine(to: CGPoint(x: 120 * u, y: 36 * u))
    beamPath.closeSubpath()
    c.setFillColor(VehicleArt.beam.cgColor)
    c.addPath(beamPath)
    c.fillPath()

    // shadow, wheels, body
    rr(c, VehicleArt.shade, -60, -22, 128, 56, 20, u)
    rr(c, VehicleArt.ink, 30, -34, 24, 8, 3, u)
    rr(c, VehicleArt.ink, 30, 26, 24, 8, 3, u)
    rr(c, VehicleArt.ink, -52, -34, 24, 8, 3, u)
    rr(c, VehicleArt.ink, -52, 26, 24, 8, 3, u)
    rr(c, Logo.yellow, -64, -28, 128, 56, 20, u)
    c.setStrokeColor(UIColor(white: 0, alpha: 0.2).cgColor)
    c.setLineWidth(1.5 * u)
    c.addPath(
      UIBezierPath(
        roundedRect: CGRect(x: -64 * u, y: -28 * u, width: 128 * u, height: 56 * u),
        cornerRadius: 20 * u
      ).cgPath)
    c.strokePath()

    // hood, windshield, roof, rear window
    rr(c, VehicleArt.deep, 32, -22, 26, 44, 12, u)
    rr(c, VehicleArt.glass, 6, -23, 22, 46, 9, u)
    rr(c, VehicleArt.glassShine, 8, -21, 6, 42, 3, u)
    rr(c, VehicleArt.porcelain, -30, -24, 38, 48, 12, u)
    rr(c, VehicleArt.glass, -43, -21, 12, 42, 6, u)

    // logo on the roof (kept upright)
    c.saveGState()
    c.translateBy(x: -11 * u, y: 0)
    c.rotate(by: -heading * F.pi / 180)
    Logo.draw(c, cx: 0, cy: 0, height: 31 * u)
    c.restoreGState()

    // mirrors
    rr(c, VehicleArt.deep, 14, -34, 9, 7, 3, u)
    rr(c, VehicleArt.deep, 14, 27, 9, 7, 3, u)

    // lights (+ brake glow)
    rr(c, VehicleArt.lampWarm, 58, -23, 5, 13, 2.5, u)
    rr(c, VehicleArt.lampWarm, 58, 10, 5, 13, 2.5, u)
    rr(c, VehicleArt.red, -66, -23, 4, 13, 2, u)
    rr(c, VehicleArt.red, -66, 10, 4, 13, 2, u)
    if braking > 0 {
      let glow = UIColor(hex: 0xFF3B30).a(0.35 * braking)
      dot(c, glow, 15, -69, -16.5, u)
      dot(c, glow, 15, -69, 16.5, u)
    }

    // hazard lights (blinking)
    if hazard > 0 {
      let glow = UIColor(hex: 0xFFA000).a(0.55 * hazard)
      let core = UIColor(hex: 0xFFC107).a(hazard)
      let xs: [F] = [63, 63, -67, -67]
      let ys: [F] = [-27, 27, -27, 27]
      for i in 0..<xs.count {
        dot(c, glow, 12, xs[i], ys[i], u)
        dot(c, core, 3.5, xs[i], ys[i], u)
      }
    }

    // trunk: the lid folds towards its hinge and shows the dark inside
    if trunk > 0 { rr(c, VehicleArt.trunkInside, -62, -21, 18, 42, 5, u) }
    let lidW = lf(20, 7, trunk)
    rr(c, VehicleArt.deep, -44 - lidW, -22, lidW, 44, 6, u)

    c.restoreGState()
  }

  /// The parcel: a light box with a tape in the colour of the card it becomes, the logo printed on it. Sizes in POINTS.
  func parcel(_ c: CGContext, cx: F, cy: F, size: F, rotDeg: F, color: UIColor, alpha: F) {
    if alpha <= 0 { return }
    c.saveGState()
    c.translateBy(x: cx, y: cy)
    c.rotate(by: rotDeg * F.pi / 180)
    let half = size / 2
    let rad = size * 0.16
    let box = CGRect(x: -half, y: -half, width: size, height: size)

    c.setFillColor(UIColor.black.a(0.15 * alpha).cgColor)
    c.addPath(UIBezierPath(roundedRect: box.offsetBy(dx: 3, dy: 4), cornerRadius: rad).cgPath)
    c.fillPath()

    c.setFillColor(VehicleArt.porcelain.a(alpha).cgColor)
    c.addPath(UIBezierPath(roundedRect: box, cornerRadius: rad).cgPath)
    c.fillPath()

    c.setStrokeColor(UIColor.black.a(0.12 * alpha).cgColor)
    c.setLineWidth(size * 0.03)
    c.addPath(UIBezierPath(roundedRect: box, cornerRadius: rad).cgPath)
    c.strokePath()

    c.setFillColor(color.a(alpha).cgColor)
    c.fill(CGRect(x: -half, y: -half + size * 0.14, width: size, height: size * 0.16))

    Logo.draw(c, cx: 0, cy: size * 0.12, height: size * 0.44, alpha: alpha)
    c.restoreGState()
  }
}
