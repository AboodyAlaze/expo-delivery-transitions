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

  init(ys: [F], car: Bool, y0: F, y1: F, lead: F, flyLen: F? = nil, openLen: F? = nil) {
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
    self.fly = flyLen ?? (car ? 0.09 : 0.10)
    self.unbox = openLen ?? (car ? 0.08 : 0.09)
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
  func throwPulse(_ p: F, lag: F = 0, split: F = -1) -> F {
    var m: F = 0
    for j in 0..<n {
      let pj = (split >= 0 && toss(j) < split) ? p + lag : p
      let t = clamp01((pj - (toss(j) - 0.012)) / 0.05)
      m = max(m, sin(F.pi * t))
    }
    return m
  }

  func braking(_ p: F) -> F {
    if !car { return 0 }
    for j in 0..<n where p >= arrive[j] - 0.004 && p <= arrive[j] + dwell { return 1 }
    return 0
  }

  func trunkOpen(_ p: F, lag: F = 0, split: F = -1) -> F {
    if !car { return 0 }
    var m: F = 0
    for j in 0..<n {
      let pj = (split >= 0 && toss(j) < split) ? p + lag : p
      let t1 = smoothStep((pj - arrive[j]) / 0.010)
      let t2 = 1 - smoothStep((pj - (arrive[j] + dwell - 0.010)) / 0.010)
      m = max(m, min(t1, t2))
    }
    return m
  }
}

/// A small view that draws the vehicle (only this small area is redrawn each frame, never the whole screen).
final class VehicleView: UIView {
  var paint: ((CGContext) -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = UIColor.clear
    isOpaque = false
    isUserInteractionEnabled = false
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func draw(_ rect: CGRect) {
    guard let c = UIGraphicsGetCurrentContext() else { return }
    paint?(c)
  }
}

/// DELIVERY DROP. The vehicle starts driving the moment the user taps (while the new screen is still rendering), then
/// drives down the left side and throws a parcel (with the logo) to every element of the new screen.
///
/// The new screen is cut into horizontal slices that together cover the WHOLE screen (no gaps, no lines): the
/// boundaries come from the real elements (DeliveryTarget, or five equal bands when there are none), and every slice
/// opens where its parcel lands. Everything is moved with Core Animation layers (GPU); only the small vehicle view is
/// redrawn each frame.
final class DeliveryTransition: Transition {
  private let car: Bool
  private var art = VehicleArt(d: 1)
  private var plan: Plan?
  private var items: [CGRect] = []
  private var tileLayers: [CALayer] = []
  private var tiles: [CGRect] = []
  private var parcelLayers: [CALayer] = []
  private var bubbles: [CATextLayer] = []
  private var vehicle: VehicleView?
  private var cx: [F] = []
  private var cy: [F] = []
  private var loadIdx = -1  // the first element that is still loading: the vehicle stops there
  private var laneX: F = 0
  private var accent = UIColor.systemOrange
  private let d: F = 1
  private var dur: Double = 1.2
  private var scale: CGFloat = 2

  // waiting pre-roll
  private var waited = false
  private var roadTop: F = 0
  private var holdY: F = 0
  private var lastVy: F = 0
  private var waitAlpha: F = 1
  private var idleMs: Double = 0
  private var lastIdleMs: Double = 0

  // what the vehicle view draws this frame
  private var armOut: F = 0
  private var brake: F = 0
  private var trunk: F = 0

  /// bag = ONE parcel to the middle of the screen that grows into the new page (the other mode: five parcels, five slices)
  private let bag: Bool
  private let sheetLayer = CALayer()

  init(car: Bool, bag: Bool = false) {
    self.car = car
    self.bag = bag
  }

  var defaultMs: Int { return bag ? (car ? 1500 : 1400) : (car ? 1400 : 1200) }
  var easing: (Double) -> Double { return { $0 } }
  var wantsOverlay: Bool { return false }
  var showsSkipHint: Bool { return false }
  var supportsWaiting: Bool { return true }
  var startProgress: F { return waited ? RIDE_START : 0 }

  // MARK: actors (vehicle, bubbles)

  private func ensureActors(host: TransitionView, req: TransitionRequest) {
    scale = host.fromImage.scale
    accent = req.accent
    host.backgroundColor = req.background
    laneX = (car ? 52 : 40) * d
    roadTop = -90 * d
    if vehicle == nil {
      art = VehicleArt(d: d)
      let side: CGFloat = 280
      let v = VehicleView(frame: CGRect(x: 0, y: 0, width: side, height: side))
      v.contentScaleFactor = scale
      v.isHidden = true
      v.paint = { [weak self] c in
        guard let s = self else { return }
        let m: F = 140
        if s.car {
          s.art.car(c, cx: m, cy: m, heading: 90, braking: s.brake, trunk: s.trunk)
        } else {
          s.art.bike(c, cx: m, cy: m, heading: 90, armOut: s.armOut)
        }
      }
      host.addSubview(v)
      vehicle = v
      for _ in 0..<3 {
        let t = CATextLayer()
        t.string = "?"
        t.fontSize = 20
        t.alignmentMode = .center
        t.foregroundColor = accent.cgColor
        t.contentsScale = scale
        t.bounds = CGRect(x: 0, y: 0, width: 24, height: 26)
        t.opacity = 0
        host.stage.addSublayer(t)
        bubbles.append(t)
      }
    }
  }

  private func placeVehicle(vy: F, visible: Bool) {
    guard let v = vehicle else { return }
    v.isHidden = !visible
    if visible {
      v.center = CGPoint(x: laneX, y: vy)
      v.setNeedsDisplay()
    }
  }

  /// "?" bubbles float up from the vehicle while it waits (for the new screen, or for the data of a loading element).
  private func showBubbles(vy: F, ms: Double) {
    for (k, b) in bubbles.enumerated() {
      let t = (F(ms) / 1100 + F(k) / 3).truncatingRemainder(dividingBy: 1)
      let x = laneX + 34 * d + sin(t * 6.2832 + F(k)) * 9 * d
      let y = vy - 34 * d - t * 64 * d
      b.position = CGPoint(x: x, y: y)
      b.opacity = Float(max(0, min(1, sin(F.pi * t))))
    }
  }

  private func hideBubbles() {
    for b in bubbles { b.opacity = 0 }
  }

  // MARK: waiting

  func prepareWaiting(host: TransitionView, req: TransitionRequest) {
    ensureActors(host: host, req: req)
    holdY = 64 * d
    lastVy = roadTop
    waited = true
  }

  func updateWaiting(ms: Double, host: TransitionView) {
    // the old screen fades to solid black: the tap was understood and something is happening
    waitAlpha = 1 - smoothStep(F(ms) / 240)
    host.fromLayer.opacity = Float(waitAlpha)
    let arrive = easeOutCubic(F(ms) / 300)
    let bob: F = arrive >= 1 ? sin(F(ms) / 170) * 2 * d : 0
    let vy = lf(roadTop, holdY, arrive) + bob
    lastVy = vy
    armOut = 0
    brake = 0
    trunk = 0
    placeVehicle(vy: vy, visible: vy > roadTop + 1)
    if arrive >= 1 { showBubbles(vy: vy, ms: ms - 300) } else { hideBubbles() }
  }

  // MARK: run

  /// The real elements, clamped to the screen, top to bottom. Five equal bands when JavaScript gave none.
  private func elements(_ targets: [CGRect], _ loading: [Bool], _ size: CGSize) -> [(rect: CGRect, loading: Bool)] {
    let minSize = 24 * d
    var list: [(rect: CGRect, loading: Bool)] = []
    for (i, t) in targets.enumerated() {
      let r = t.intersection(CGRect(origin: .zero, size: size))
      if r.isNull || r.width < minSize || r.height < minSize { continue }
      list.append((rect: r, loading: i < loading.count ? loading[i] : false))
    }
    list.sort { $0.rect.minY < $1.rect.minY }
    if !list.isEmpty { return Array(list.prefix(BLOCKS)) }
    let band = size.height / F(BLOCKS)
    return (0..<BLOCKS).map { (rect: CGRect(x: 0, y: F($0) * band, width: size.width, height: band), loading: false) }
  }

  /// Full-width slices from the top edge to the bottom edge; each boundary is halfway between two neighbouring
  /// elements (rounded to whole pixels, so neighbouring slices never leave a hairline).
  private func tile(_ elems: [CGRect], _ size: CGSize) -> [CGRect] {
    var out: [CGRect] = []
    var top: F = 0
    for i in 0..<elems.count {
      var bottom: F = size.height
      if i < elems.count - 1 {
        let mid = (elems[i].maxY + elems[i + 1].minY) / 2
        bottom = min(max((mid * scale).rounded() / scale, top + 1 / scale), size.height)
      }
      out.append(CGRect(x: 0, y: top, width: size.width, height: bottom - top))
      top = bottom
    }
    return out
  }

  private func crop(_ cg: CGImage, _ r: CGRect) -> CGImage? {
    return cg.cropping(
      to: CGRect(x: r.minX * scale, y: r.minY * scale, width: r.width * scale, height: r.height * scale))
  }

  private func makeParcelSprite(_ side: CGFloat) -> CGImage? {
    let format = UIGraphicsImageRendererFormat.default()
    format.scale = scale
    let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format)
    let image = renderer.image { ctx in
      self.art.parcel(ctx.cgContext, cx: side / 2, cy: side / 2, size: 50 * self.d, rotDeg: 0, color: self.accent, alpha: 1)
    }
    return image.cgImage
  }

  func prepare(host: TransitionView, req: TransitionRequest) {
    let size = host.bounds.size
    ensureActors(host: host, req: req)
    dur = Double(req.durationMs > 0 ? req.durationMs : defaultMs) / 1000

    let found = elements(req.targets, req.loading, size)
    if bag {
      // one parcel, to the middle of the screen; the vehicle waits for the data of the first loading element, if any
      items = [CGRect(x: size.width / 2 - 1, y: size.height / 2 - 1, width: 2, height: 2)]
      loadIdx = req.loading.contains(true) ? 0 : -1
      tiles = []
    } else {
      items = found.map { $0.rect }
      loadIdx = found.firstIndex { $0.loading } ?? -1
      tiles = tile(items, size)
    }
    cx = items.map { $0.midX }
    cy = items.map { $0.midY }

    let y1 = size.height + 90 * d
    let lead = (car ? 40 : 46) * d
    // continue from where the vehicle is waiting (never below the first stop, or it would have to drive backwards)
    let y0: F = waited ? min(lastVy, cy[0] - lead - 8 * d) : roadTop
    plan =
      bag
      ? Plan(ys: cy, car: car, y0: y0, y1: y1, lead: lead, flyLen: 0.16, openLen: 0.34)
      : Plan(ys: cy, car: car, y0: y0, y1: y1, lead: lead)

    // slices of the new screen: each one scales around the spot where its parcel lands
    for l in tileLayers { l.removeFromSuperlayer() }
    tileLayers = []
    if let image = host.toImage, let cg = image.cgImage {
      for (j, r) in tiles.enumerated() {
        let layer = CALayer()
        layer.bounds = CGRect(x: 0, y: 0, width: r.width, height: r.height)
        layer.anchorPoint = CGPoint(x: (cx[j] - r.minX) / r.width, y: (cy[j] - r.minY) / r.height)
        layer.position = CGPoint(x: cx[j], y: cy[j])
        layer.contentsGravity = .resize
        layer.contentsScale = scale
        layer.opacity = 0
        layer.contents = crop(cg, r)
        host.stage.addSublayer(layer)
        tileLayers.append(layer)
      }
    }

    // the bag grows into the new page: one layer whose picture covers it (centred, never squashed)
    sheetLayer.removeFromSuperlayer()
    if bag, let cg = host.toImage?.cgImage {
      sheetLayer.contents = cg
      sheetLayer.contentsGravity = .resizeAspectFill
      sheetLayer.contentsScale = scale
      sheetLayer.masksToBounds = true
      sheetLayer.opacity = 0
      host.stage.addSublayer(sheetLayer)
    }

    // parcels: one small sprite layer each, moved and rotated by the GPU
    for l in parcelLayers { l.removeFromSuperlayer() }
    parcelLayers = []
    let side: CGFloat = 90
    let sprite = makeParcelSprite(side)
    for _ in 0..<items.count {
      let l = CALayer()
      l.bounds = CGRect(x: 0, y: 0, width: side, height: side)
      l.contentsScale = scale
      l.contents = sprite
      l.opacity = 0
      host.stage.addSublayer(l)
      parcelLayers.append(l)
    }
    for b in bubbles { host.stage.addSublayer(b) }  // keep the bubbles above the parcels
    host.toContainer.opacity = 0
  }

  func holdProgress() -> F {
    guard let pl = plan, loadIdx >= 0, loadIdx < pl.n else { return -1 }
    return pl.arrive[loadIdx]
  }

  func setIdle(ms: Double, host: TransitionView) {
    idleMs = ms
    if ms > lastIdleMs { lastIdleMs = ms }
  }

  func refresh(host: TransitionView, image: UIImage) {
    guard let cg = image.cgImage else { return }
    for (j, layer) in tileLayers.enumerated() where j >= max(loadIdx, 0) {
      layer.contents = crop(cg, tiles[j])
    }
  }

  /// The one parcel: thrown to the middle of the screen, then it grows (a box that opens) into the new page.
  private func updateBag(p: F, pl: Plan, host: TransitionView) {
    guard let parcel = parcelLayers.first, !cx.isEmpty else { return }
    let ts = pl.toss(0)
    let fl = clamp01((p - ts) / pl.fly)
    let ex = clamp01((p - ts - pl.fly) / pl.unbox)
    if fl > 0 && ex <= 0 {
      let vyT = pl.yAt(ts)
      let sx = car ? laneX : laneX + 40 * d  // out of the trunk / out of the rider's hand
      let sy = car ? vyT - 56 * d : vyT + 4 * d
      let x = lf(sx, cx[0], fl)
      let y = lf(sy, cy[0], fl) - 90 * d * 4 * fl * (1 - fl)
      let born = 0.5 + 0.5 * smoothStep(fl / 0.2)
      let rot = CATransform3DMakeRotation(720 * fl * F.pi / 180, 0, 0, 1)  // two full turns: the logo lands upright
      parcel.position = CGPoint(x: x, y: y)
      parcel.transform = CATransform3DConcat(rot, CATransform3DMakeScale(born, born, 1))
      parcel.opacity = 1
      sheetLayer.opacity = 0
    } else if ex > 0 {
      let size = host.bounds.size
      let g = smoothStep(ex)
      let half: F = 25 * d
      let l = lf(cx[0] - half, 0, g)
      let t = lf(cy[0] - half, 0, g)
      let r = lf(cx[0] + half, size.width, g)
      let b = lf(cy[0] + half, size.height, g)
      sheetLayer.bounds = CGRect(x: 0, y: 0, width: r - l, height: b - t)
      sheetLayer.position = CGPoint(x: (l + r) / 2, y: (t + b) / 2)
      sheetLayer.cornerRadius = lf(12 * d, 0, g)
      sheetLayer.opacity = Float(smoothStep(ex / 0.35))
      let k = min((r - l) / (50 * d), 1.8)
      parcel.position = CGPoint(x: (l + r) / 2, y: (t + b) / 2)
      parcel.transform = CATransform3DMakeScale(k, k, 1)
      parcel.opacity = Float(1 - smoothStep(ex / 0.4))
    } else {
      parcel.opacity = 0
      sheetLayer.opacity = 0
    }
  }

  func update(p: F, host: TransitionView) {
    guard let pl = plan else { return }
    let lag: F = dur > 0 ? F(lastIdleMs / 1000 / dur) : 0
    let split = holdProgress()

    // the old screen steps aside
    let e: F = waited ? smoothStep((p - RIDE_START) / 0.14) : smoothStep(p / 0.20)
    host.fromLayer.opacity = Float((1 - e) * (waited ? waitAlpha : 1))

    // every slice of the new screen opens where its parcel landed
    for j in 0..<tileLayers.count {
      let pj = (split >= 0 && pl.toss(j) < split) ? p + lag : p
      let t = clamp01((pj - pl.toss(j) - pl.fly) / pl.unbox)
      let layer = tileLayers[j]
      if t <= 0 {
        layer.opacity = 0
      } else if t >= 1 {
        layer.transform = CATransform3DIdentity
        layer.opacity = 1
      } else {
        let sc = lf(0.08, 1, easeOutCubic(t))
        layer.transform = CATransform3DMakeScale(sc, sc, 1)
        layer.opacity = Float(min(t * 4, 1))
      }
    }

    if bag { updateBag(p: p, pl: pl, host: host) }

    // parcels in the air
    for j in 0..<(bag ? 0 : parcelLayers.count) {
      let ts = pl.toss(j)
      let pj = (split >= 0 && ts < split) ? p + lag : p
      let fl = clamp01((pj - ts) / pl.fly)
      let op = clamp01((pj - ts - pl.fly) / pl.unbox)
      let layer = parcelLayers[j]
      if fl > 0 && op < 0.4 {
        let vyT = pl.yAt(ts)
        // out of the trunk / out of the rider's hand
        let sx = car ? laneX : laneX + 40 * d
        let sy = car ? vyT - 56 * d : vyT + 4 * d
        let x = lf(sx, cx[j], fl)
        let y = lf(sy, cy[j], fl) - 70 * d * 4 * fl * (1 - fl)
        let born = 0.5 + 0.5 * smoothStep(fl / 0.2)
        let k = born * (1 - op * 0.6)  // 1.0 = full size (50 pt)
        let rot = CATransform3DMakeRotation(720 * fl * F.pi / 180, 0, 0, 1)  // two full turns: the logo lands upright
        layer.position = CGPoint(x: x, y: y)
        layer.transform = CATransform3DConcat(rot, CATransform3DMakeScale(k, k, 1))
        layer.opacity = Float(max(0, min(1, 1 - op * 2.5)))
      } else {
        layer.opacity = 0
      }
    }

    // the vehicle
    let vy = pl.yAt(p)
    let active = p >= RIDE_START && p < pl.endT && vy < pl.y1 - 1 && (waited || vy > pl.y0 + 1)
    armOut = pl.throwPulse(p, lag: lag, split: split)
    brake = pl.braking(p)
    trunk = pl.trunkOpen(p, lag: lag, split: split)
    placeVehicle(vy: vy, visible: active)
    if active && idleMs > 0 { showBubbles(vy: vy, ms: idleMs) } else { hideBubbles() }

    // at the very end the complete real screen takes over
    host.toContainer.opacity = Float(smoothStep((p - 0.93) / 0.07))
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
