import UIKit

let BLOCKS = 5
let RIDE_START: F = 0.08
let RIDE_SPAN: F = 0.82

/// Where the vehicle is at every moment, and when each parcel is thrown.
final class Plan {
  let n: Int
  let car: Bool
  let y0: F
  let y1: F
  let dwell: F
  let fly: F
  let unbox: F
  let arrive: [F]
  let endT: F
  private let stopY: [F]

  init(ys: [F], car: Bool, y0: F, y1: F, lead: F) {
    let count = ys.count
    let dw: F = car ? 0.045 : 0
    let stop = ys.map { $0 - lead }
    let speed = (y1 - y0) / (RIDE_SPAN - F(count) * dw)
    var arr = [F](repeating: 0, count: count)
    for j in 0..<count {
      arr[j] = RIDE_START + (stop[j] - y0) / speed + F(j) * dw
    }
    let end = arr[count - 1] + dw + (y1 - stop[count - 1]) / speed

    self.n = count
    self.car = car
    self.y0 = y0
    self.y1 = y1
    self.dwell = dw
    self.fly = car ? 0.09 : 0.10
    self.unbox = car ? 0.08 : 0.09
    self.stopY = stop
    self.arrive = arr
    self.endT = end
  }

  func toss(_ j: Int) -> F {
    return min(arrive[j] + dwell * 0.45, 0.99 - fly - unbox)
  }

  func yAt(_ p: F) -> F {
    if p <= RIDE_START { return y0 }
    var segT = RIDE_START
    var segY = y0
    for j in 0..<n {
      if p < arrive[j] {
        let t = clamp01((p - segT) / max(arrive[j] - segT, 1e-4))
        let e: F = !car ? t : (j == 0 ? easeOutCubic(t) : smoothStep(t))
        return lf(segY, stopY[j], e)
      }
      if p < arrive[j] + dwell { return stopY[j] }
      segT = arrive[j] + dwell
      segY = stopY[j]
    }
    let t = clamp01((p - segT) / max(endT - segT, 1e-4))
    return lf(segY, y1, car ? t * t : t)
  }

  /// 0..1, > 0 while the courier swings his arm to throw.
  func throwPulse(_ p: F) -> F {
    var m: F = 0
    for j in 0..<n {
      let t = clamp01((p - (toss(j) - 0.012)) / 0.05)
      m = max(m, sin(F.pi * t))
    }
    return m
  }

  func braking(_ p: F) -> F {
    if !car { return 0 }
    for j in 0..<n where p >= arrive[j] - 0.004 && p <= arrive[j] + dwell { return 1 }
    return 0
  }

  func trunkOpen(_ p: F) -> F {
    if !car { return 0 }
    var m: F = 0
    for j in 0..<n {
      let t1 = smoothStep((p - arrive[j]) / 0.010)
      let t2 = 1 - smoothStep((p - (arrive[j] + dwell - 0.010)) / 0.010)
      m = max(m, min(t1, t2))
    }
    return m
  }
}

/// DELIVERY DROP. The old screen steps aside, the vehicle drives down the left side and throws a parcel (with the logo)
/// to the spot of every card of the new screen; the parcel un-boxes into that card.
/// The cards come from the REAL new screen (JavaScript passes the rectangles of up to 5 elements); without them the
/// screen is simply cut into 5 bands.
final class DeliveryTransition: Transition {
  private let car: Bool
  private var art = VehicleArt(d: 1)
  private var plan: Plan?
  private var rects: [CGRect] = []
  private var cardLayers: [CALayer] = []
  private var laneX: F = 0
  private var accent = UIColor.systemOrange
  private let d: F = 1

  init(car: Bool) {
    self.car = car
  }

  var defaultMs: Int { return car ? 3200 : 2600 }
  var easing: (Double) -> Double { return { $0 } }
  var wantsOverlay: Bool { return true }
  var showsSkipHint: Bool { return true }

  func prepare(host: TransitionView, req: TransitionRequest) {
    let size = host.bounds.size
    art = VehicleArt(d: d)
    accent = req.accent
    rects = req.targets.count >= BLOCKS ? Array(req.targets.prefix(BLOCKS)) : fallbackTargets(size)
    let y0 = -90 * d
    let y1 = size.height + 90 * d
    let lead = (car ? 40 : 46) * d
    laneX = (car ? 52 : 40) * d
    plan = Plan(ys: rects.map { $0.midY }, car: car, y0: y0, y1: y1, lead: lead)

    if let image = host.toImage, let cg = image.cgImage {
      host.backgroundColor = pixelColor(cg, x: 2, y: 2) ?? UIColor.white
      let sc = image.scale
      for r in rects {
        let layer = CALayer()
        layer.frame = r
        layer.contentsGravity = .resize
        layer.contentsScale = sc
        layer.opacity = 0
        let crop = CGRect(x: r.minX * sc, y: r.minY * sc, width: r.width * sc, height: r.height * sc)
        layer.contents = cg.cropping(to: crop)
        host.stage.addSublayer(layer)
        cardLayers.append(layer)
      }
    }
    host.toContainer.opacity = 0
  }

  func update(p: F, host: TransitionView) {
    guard let pl = plan else { return }
    // the old screen steps aside
    let e = smoothStep(p / 0.22)
    host.fromLayer.opacity = Float(1 - e)
    let s = 1 - 0.08 * e
    host.fromLayer.transform = CATransform3DMakeScale(s, s, 1)

    // the cards of the new screen, "un-boxed" one by one
    for j in 0..<min(BLOCKS, cardLayers.count) {
      let t = clamp01((p - pl.toss(j) - pl.fly) / pl.unbox)
      let layer = cardLayers[j]
      if t <= 0 {
        layer.opacity = 0
      } else {
        let sc = lf(0.08, 1, easeOutBack(t))
        layer.transform = CATransform3DMakeScale(sc, sc, 1)
        layer.opacity = Float(min(t * 3, 1))
      }
    }

    // at the very end the complete real screen (status bar, everything else) takes over
    host.toContainer.opacity = Float(smoothStep((p - 0.93) / 0.07))
  }

  func drawOverlay(_ c: CGContext, p: F, size: CGSize) {
    guard let pl = plan else { return }
    drawVehicles(c, pl, p)
    drawStamp(c, p, size)
  }

  private func fallbackTargets(_ size: CGSize) -> [CGRect] {
    let top = 96 * d
    let bottom = size.height - 48 * d
    let gap = 14 * d
    let hh = (bottom - top - gap * F(BLOCKS - 1)) / F(BLOCKS)
    var out: [CGRect] = []
    for i in 0..<BLOCKS {
      let y = top + F(i) * (hh + gap)
      out.append(CGRect(x: 16 * d, y: y, width: size.width - 32 * d, height: hh))
    }
    return out
  }

  private func drawVehicles(_ c: CGContext, _ pl: Plan, _ p: F) {
    let vy = pl.yAt(p)
    let active = p > RIDE_START && p < pl.endT && vy > pl.y0 + 1 && vy < pl.y1 - 1

    // the road behind the vehicle
    if active {
      if car {
        c.setFillColor(UIColor.black.a(0.05).cgColor)
        c.fill(CGRect(x: laneX - 40 * d, y: pl.y0, width: 80 * d, height: vy - pl.y0))
      }
      c.saveGState()
      c.setStrokeColor(UIColor.black.a(0.13).cgColor)
      c.setLineWidth(3 * d)
      c.setLineDash(phase: 0, lengths: [14 * d, 12 * d])
      c.move(to: CGPoint(x: laneX, y: pl.y0))
      c.addLine(to: CGPoint(x: laneX, y: vy))
      c.strokePath()
      c.restoreGState()
    }

    // parcels in the air
    for j in 0..<min(pl.n, rects.count) {
      let r = rects[j]
      let ts = pl.toss(j)
      let fl = clamp01((p - ts) / pl.fly)
      let op = clamp01((p - ts - pl.fly) / pl.unbox)
      if fl > 0 && op < 0.4 {
        let vyT = pl.yAt(ts)
        // out of the trunk / out of the rider's hand
        let sx = car ? laneX : laneX + 40 * d
        let sy = car ? vyT - 56 * d : vyT + 4 * d
        let x = lf(sx, r.midX, fl)
        let y = lf(sy, r.midY, fl) - 70 * d * 4 * fl * (1 - fl)
        let born = 0.5 + 0.5 * smoothStep(fl / 0.2)
        let size = 50 * d * born * (1 - op * 0.6)
        // 720 degrees = two full turns, so the logo lands upright
        art.parcel(c, cx: x, cy: y, size: size, rotDeg: 720 * fl, color: accent, alpha: 1 - op * 2.5)
      }
    }

    // the vehicle
    if active {
      if car {
        art.car(c, cx: laneX, cy: vy, heading: 90, braking: pl.braking(p), trunk: pl.trunkOpen(p))
      } else {
        // speed lines behind the rider (he drives down, so "behind" = up)
        c.saveGState()
        c.setLineCap(.round)
        c.setLineWidth(2.5 * d)
        for k in -1...1 {
          let sx = laneX - 1 * F(k) * 12 * d
          for m in 0...1 {
            let a = 80 * d + F(m) * 30 * d
            c.setStrokeColor(UIColor.black.a(0.14 - F(m) * 0.05).cgColor)
            c.move(to: CGPoint(x: sx, y: vy - a))
            c.addLine(to: CGPoint(x: sx, y: vy - (a + 22 * d)))
            c.strokePath()
          }
        }
        c.restoreGState()
        let wob = sin(p * 90) * 1.5 * d
        art.bike(c, cx: laneX + wob, cy: vy, heading: 90, armOut: pl.throwPulse(p))
      }
    }
  }

  private func drawStamp(_ c: CGContext, _ p: F, _ size: CGSize) {
    let t = clamp01((p - 0.88) / 0.12)
    let a = sin(F.pi * t)
    if a <= 0.01 { return }
    let text = "Delivered  ✓" as NSString
    let attrs: [NSAttributedString.Key: Any] = [
      .font: UIFont.boldSystemFont(ofSize: 15 * d),
      .foregroundColor: UIColor.white.a(a),
    ]
    let ts = text.size(withAttributes: attrs)
    let padX = 18 * d
    let padY = 10 * d
    let bw = ts.width + padX * 2
    let bh = ts.height + padY * 2
    let cx = size.width / 2
    let bottom = size.height - 56 * d
    let pill = CGRect(x: cx - bw / 2, y: bottom - bh, width: bw, height: bh)
    c.saveGState()
    let s = 0.9 + 0.1 * a
    c.translateBy(x: cx, y: pill.midY)
    c.scaleBy(x: s, y: s)
    c.translateBy(x: -cx, y: -pill.midY)
    c.setFillColor(Logo.navy.a(a).cgColor)
    c.addPath(UIBezierPath(roundedRect: pill, cornerRadius: bh / 2).cgPath)
    c.fillPath()
    text.draw(at: CGPoint(x: pill.minX + padX, y: pill.minY + padY), withAttributes: attrs)
    c.restoreGState()
  }
}

/// Colour of one pixel of an image (used for the page background).
func pixelColor(_ image: CGImage, x: Int, y: Int) -> UIColor? {
  if x < 0 || y < 0 || x >= image.width || y >= image.height { return nil }
  guard let one = image.cropping(to: CGRect(x: x, y: y, width: 1, height: 1)) else { return nil }
  var px = [UInt8](repeating: 0, count: 4)
  let space = CGColorSpaceCreateDeviceRGB()
  let info = CGImageAlphaInfo.premultipliedLast.rawValue
  let ok: Bool = px.withUnsafeMutableBytes { (buf: UnsafeMutableRawBufferPointer) -> Bool in
    guard let base = buf.baseAddress,
      let ctx = CGContext(
        data: base, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: space, bitmapInfo: info)
    else { return false }
    ctx.draw(one, in: CGRect(x: 0, y: 0, width: 1, height: 1))
    return true
  }
  if !ok { return nil }
  let a = F(px[3]) / 255
  if a <= 0 { return nil }
  return UIColor(red: F(px[0]) / 255 / a, green: F(px[1]) / 255 / a, blue: F(px[2]) / 255 / a, alpha: 1)
}
