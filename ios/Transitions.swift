import UIKit

/// Everything a transition needs. Coordinates are in POINTS.
struct TransitionRequest {
  let name: String
  let origin: CGPoint
  let accent: UIColor
  let durationMs: Int
  let targets: [CGRect]
  /// One flag per target: that element is still loading its data (the vehicle waits there).
  let loading: [Bool]
  /// The solid colour behind everything while the animation runs (black by default).
  let background: UIColor
}

/// One animation between two screenshots. `p` goes 0 -> 1 (it may overshoot for "elastic" easings).
/// A transition moves layers of the host (cheap, on the GPU) and may draw vector extras in `drawOverlay`.
protocol Transition: AnyObject {
  var defaultMs: Int { get }
  var easing: (Double) -> Double { get }
  var wantsOverlay: Bool { get }
  var showsSkipHint: Bool { get }
  func prepare(host: TransitionView, req: TransitionRequest)
  func update(p: F, host: TransitionView)
  func drawOverlay(_ c: CGContext, p: F, size: CGSize)

  /// "Waiting" pre-roll (delivery only): the animation STARTS before the new screen is ready (the vehicle arrives and
  /// waits), so a slow screen never looks like a frozen app. The same instance continues when the screen is ready.
  var supportsWaiting: Bool { get }
  func prepareWaiting(host: TransitionView, req: TransitionRequest)
  func updateWaiting(ms: Double, host: TransitionView)
  /// Where the timeline starts (a transition that already played its first part while waiting skips it).
  var startProgress: F { get }
  /// Progress at which the animation must stop and wait for the data of a loading element (-1 = never).
  func holdProgress() -> F
  /// Called every frame while waiting at the hold point, with the time spent there.
  func setIdle(ms: Double, host: TransitionView)
  /// The data arrived: a fresh picture of the new screen.
  func refresh(host: TransitionView, image: UIImage)
}

extension Transition {
  var wantsOverlay: Bool { return false }
  var showsSkipHint: Bool { return true }
  func drawOverlay(_ c: CGContext, p: F, size: CGSize) {}
  var supportsWaiting: Bool { return false }
  func prepareWaiting(host: TransitionView, req: TransitionRequest) {}
  func updateWaiting(ms: Double, host: TransitionView) {}
  var startProgress: F { return 0 }
  func holdProgress() -> F { return -1 }
  func setIdle(ms: Double, host: TransitionView) {}
  func refresh(host: TransitionView, image: UIImage) {}
}

private let fastOutSlowIn = CubicBezier(x1: 0.4, y1: 0, x2: 0.2, y2: 1)

/// Normal, quick transition: used for BACK.
final class FadeTransition: Transition {
  var defaultMs: Int { return 260 }
  var easing: (Double) -> Double { return { fastOutSlowIn.value($0) } }
  var showsSkipHint: Bool { return false }

  func prepare(host: TransitionView, req: TransitionRequest) {}

  func update(p: F, host: TransitionView) {
    host.toContainer.opacity = Float(clamp01(p))
  }
}

final class CurtainTransition: Transition {
  var defaultMs: Int { return 1300 }
  var easing: (Double) -> Double { return { $0 } }
  private let mask = CAShapeLayer()
  private var size = CGSize.zero

  func prepare(host: TransitionView, req: TransitionRequest) {
    size = host.bounds.size
    mask.frame = host.bounds
    host.toContainer.mask = mask
  }

  func update(p: F, host: TransitionView) {
    mask.path = Shapes.curtain(p: p, w: size.width, h: size.height)
  }
}

final class RadarTransition: Transition {
  var defaultMs: Int { return 1100 }
  var easing: (Double) -> Double { return { fastOutSlowIn.value($0) } }
  var wantsOverlay: Bool { return true }
  private let mask = CAShapeLayer()
  private var size = CGSize.zero
  private var origin = CGPoint.zero
  private var accent = UIColor.systemTeal

  func prepare(host: TransitionView, req: TransitionRequest) {
    size = host.bounds.size
    origin = req.origin
    accent = req.accent
    mask.frame = host.bounds
    host.toContainer.mask = mask
  }

  func update(p: F, host: TransitionView) {
    mask.path = Shapes.sweep(p: p, cx: origin.x, cy: origin.y, w: size.width, h: size.height)
  }

  func drawOverlay(_ c: CGContext, p: F, size: CGSize) {
    if p <= 0.01 || p >= 0.985 { return }
    let r = hypot(size.width, size.height)
    let theta = (-90 + 360 * p) * F.pi / 180

    // glowing trail behind the line: 24 wedges whose alpha grows towards the line
    let span: F = 43 * F.pi / 180
    let slices = 24
    for i in 0..<slices {
      let t0 = F(i) / F(slices)
      let a0 = theta - span + span * t0
      let a1 = a0 + span / F(slices) + 0.003
      let wedge = UIBezierPath()
      wedge.move(to: origin)
      wedge.addArc(withCenter: origin, radius: r, startAngle: a0, endAngle: a1, clockwise: true)
      wedge.close()
      c.setFillColor(accent.a(0.7 * (t0 + 0.5 / F(slices))).cgColor)
      c.addPath(wedge.cgPath)
      c.fillPath()
    }

    // the radar line itself
    c.setStrokeColor(UIColor.white.cgColor)
    c.setLineWidth(3)
    c.setLineCap(.butt)
    c.move(to: origin)
    c.addLine(to: CGPoint(x: origin.x + r * cos(theta), y: origin.y + r * sin(theta)))
    c.strokePath()
    c.setFillColor(UIColor.white.cgColor)
    c.fillEllipse(in: CGRect(x: origin.x - 7, y: origin.y - 7, width: 14, height: 14))
  }
}

final class HexTransition: Transition {
  var defaultMs: Int { return 900 }
  var easing: (Double) -> Double { return { fastOutSlowIn.value($0) } }
  var wantsOverlay: Bool { return true }
  private let mask = CAShapeLayer()
  private var size = CGSize.zero
  private var origin = CGPoint.zero
  private var accent = UIColor.systemOrange

  func prepare(host: TransitionView, req: TransitionRequest) {
    size = host.bounds.size
    origin = req.origin
    accent = req.accent
    mask.frame = host.bounds
    host.toContainer.mask = mask
  }

  func update(p: F, host: TransitionView) {
    mask.path = Shapes.hex(p: p, cx: origin.x, cy: origin.y, w: size.width, h: size.height)
  }

  func drawOverlay(_ c: CGContext, p: F, size: CGSize) {
    // glowing echo outlines behind the aperture
    let maxR = hypot(size.width, size.height) * 1.2
    for k in 0..<3 {
      let t = clamp01(p - F(k) * 0.07)
      if t > 0 && t < 1 {
        c.setStrokeColor(accent.a((1 - t) * 0.9).cgColor)
        c.setLineWidth(F(6 - k * 2))
        c.addPath(Shapes.hexPoly(cx: origin.x, cy: origin.y, r: t * maxR, rotDeg: -60 + 120 * t))
        c.strokePath()
      }
    }
  }
}

final class LiquidPourTransition: Transition {
  var defaultMs: Int { return 1900 }
  var easing: (Double) -> Double { return { CubicBezier(x1: 0.4, y1: 0, x2: 0.15, y2: 1).value($0) } }
  var wantsOverlay: Bool { return true }
  private let mask = CAShapeLayer()
  private let art = LiquidGlassArt(d: 1)
  private var size = CGSize.zero
  private var accent = UIColor.systemTeal

  func prepare(host: TransitionView, req: TransitionRequest) {
    size = host.bounds.size
    accent = req.accent
    mask.frame = host.bounds
    host.toContainer.mask = mask
  }

  func update(p: F, host: TransitionView) {
    mask.path = Shapes.drip(p: p, w: size.width, h: size.height)
  }

  func drawOverlay(_ c: CGContext, p: F, size: CGSize) {
    art.draw(c, p: p, tint: accent, w: size.width, h: size.height)
  }
}

/// BALL ZOOM: no flying around. Where you tap, a glossy rubber ball is born and INFLATES in place: it first squeezes in
/// a little (anticipation), then blows up with a bouncy overshoot until it swallows the whole screen.
final class BallZoomTransition: Transition {
  var defaultMs: Int { return 1000 }
  // easeInOutBack: dips first, overshoots at the end -> elastic ball feeling
  var easing: (Double) -> Double { return { CubicBezier(x1: 0.68, y1: -0.55, x2: 0.27, y2: 1.55).value($0) } }

  private let dim = CALayer()
  private let gradient = CAGradientLayer()
  private let ballMask = CAShapeLayer()
  private let pageMask = CAShapeLayer()
  private var size = CGSize.zero
  private var origin = CGPoint.zero
  private var accent = UIColor.systemPurple

  func prepare(host: TransitionView, req: TransitionRequest) {
    size = host.bounds.size
    origin = CGPoint(x: min(max(req.origin.x, 0), size.width), y: min(max(req.origin.y, 0), size.height))
    accent = req.accent

    // the old screen scales around the tap
    host.fromLayer.anchorPoint = CGPoint(x: origin.x / size.width, y: origin.y / size.height)
    host.fromLayer.position = origin

    // darkening of the old screen
    dim.frame = host.bounds
    dim.backgroundColor = UIColor.black.cgColor
    dim.opacity = 0
    host.stage.addSublayer(dim)

    // the glossy ball (radial gradient, masked to a circle)
    gradient.type = .radial
    gradient.startPoint = CGPoint(x: 0.5, y: 0.5)
    gradient.endPoint = CGPoint(x: 1, y: 1)
    gradient.locations = [0, 0.5, 1]
    gradient.colors = [
      accent.mixed(UIColor.white, 0.55).cgColor, accent.cgColor, accent.mixed(UIColor.black, 0.55).cgColor,
    ]
    gradient.mask = ballMask
    gradient.opacity = 0
    host.stage.addSublayer(gradient)

    // the new screen zooms out of the ball: container = circle mask, inner layer scales around the tap
    pageMask.frame = host.bounds
    host.toContainer.mask = pageMask
    host.toContainer.opacity = 0
    host.toLayer.anchorPoint = host.fromLayer.anchorPoint
    host.toLayer.position = origin
  }

  func update(p: F, host: TransitionView) {
    let pp = clamp01(p)
    let r0: F = 20
    let rFull = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y)) * 1.05
    let r = max(lf(r0, rFull, p), 1)

    // old screen: pulled towards the tap (zoom in) and darkened
    let s = 1 + 0.16 * smoothStep(pp)
    host.fromLayer.transform = CATransform3DMakeScale(s, s, 1)
    dim.opacity = Float(0.5 * smoothStep(pp / 0.6))

    // the ball
    let g = max(r * 1.6, 1)
    let gc = CGPoint(x: origin.x - r * 0.35, y: origin.y - r * 0.4)
    gradient.frame = CGRect(x: gc.x - g, y: gc.y - g, width: 2 * g, height: 2 * g)
    ballMask.frame = gradient.bounds
    ballMask.path = CGPath(
      ellipseIn: CGRect(
        x: origin.x - gradient.frame.minX - r, y: origin.y - gradient.frame.minY - r, width: 2 * r,
        height: 2 * r), transform: nil)
    gradient.opacity = Float(clamp01(p / 0.05))

    // the new screen zooms out of the ball
    pageMask.path = Shapes.ellipse(cx: origin.x, cy: origin.y, rx: r, ry: r)
    host.toContainer.opacity = Float(clamp01((pp - 0.45) / 0.4))
    let s2 = 0.8 + 0.2 * smoothStep((pp - 0.3) / 0.7)
    host.toLayer.transform = CATransform3DMakeScale(s2, s2, 1)
  }
}

func createTransition(_ name: String) -> Transition {
  switch name {
  case "deliveryBike": return DeliveryTransition(car: false)
  case "deliveryCar": return DeliveryTransition(car: true)
  case "liquidPour": return LiquidPourTransition()
  case "ballZoom": return BallZoomTransition()
  case "radarSweep": return RadarTransition()
  case "hexAperture": return HexTransition()
  case "curtainDrop": return CurtainTransition()
  default: return FadeTransition()  // "fade" (used for BACK) and anything unknown
  }
}
