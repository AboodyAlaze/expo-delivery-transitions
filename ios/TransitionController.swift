import UIKit

/// Owns the full-screen overlay.
///
///   begin()  -> screenshot of the React Native screen + an overlay that shows it (the screen looks frozen)
///   ...JavaScript navigates with Expo Router underneath...
///   play()   -> screenshot of the NEW screen, then the animation runs on the overlay
///   finish() -> overlay removed; the real new screen is already there
///
/// Safety: a watchdog removes the overlay no matter what, so a bug can never leave the app blocked.
/// Everything here runs on the main thread.
final class TransitionController {
  static let shared = TransitionController()

  private var view: TransitionView?
  private weak var window: UIWindow?
  private weak var rootView: UIView?
  private var pendingDone: (() -> Void)?
  private var watchdog: DispatchWorkItem?
  private var waitingTransition: Transition?
  private var waitingName: String?
  private var fromCount = 0
  private var fromProbe: [UInt8] = []

  private(set) var isRunning = false

  /// Step 1: freeze the current screen.
  func begin() {
    finish()  // a previous one that was still running
    guard let win = TransitionController.keyWindow(), let root = win.rootViewController?.view else { return }
    guard let image = TransitionController.capture(root, window: win, afterUpdates: false) else { return }
    let v = TransitionView(frame: win.bounds, from: image)
    v.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    win.addSubview(v)
    view = v
    window = win
    rootView = root
    fromCount = TransitionController.viewCount(root)
    fromProbe = TransitionController.probe(root, window: win)
    isRunning = true
    arm(seconds: 8)  // nothing can keep the app blocked for ever
  }

  /// Optional, right after begin(): the animation starts NOW (vehicle arrives and waits) while JavaScript renders.
  func preroll(json: String) {
    guard isRunning, let v = view else { return }
    let req = TransitionController.parse(json)
    let t = createTransition(req.name)
    if !t.supportsWaiting { return }
    waitingTransition = t
    waitingName = req.name
    v.startWaiting(t, req: req)
  }

  /// Calls `done` when the NEW screen is really on screen (the view tree changed, or the picture is clearly another
  /// screen), two frames later so it is laid out, or after `timeoutMs`. Cheap: no full screenshots while waiting.
  func waitForScreen(timeoutMs: Int, done: @escaping () -> Void) {
    guard isRunning, let root = rootView, let win = window else {
      done()
      return
    }
    let started = CACurrentMediaTime()
    var nextProbeAt = started + 0.2  // the view count catches almost every screen; the picture is only the second opinion
    func poll() {
      if !self.isRunning { done(); return }
      let now = CACurrentMediaTime()
      var changed = abs(TransitionController.viewCount(root) - self.fromCount) >= 25
      if !changed && now >= nextProbeAt {
        let s = CACurrentMediaTime()
        let probeNow = TransitionController.probe(root, window: win)
        changed = TransitionController.meanDiff(probeNow, self.fromProbe) >= 6
        // never let the watching cost the app more than about 4% of one core: slow screens are probed less often
        let cost = CACurrentMediaTime() - s
        nextProbeAt = CACurrentMediaTime() + max(0.25, cost * 25)
      }
      if changed || (now - started) * 1000 >= Double(timeoutMs) {
        // one frame, so the new screen is laid out before it is photographed
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.017) { done() }
      } else {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.033) { poll() }
      }
    }
    poll()
  }

  /// Step 2: the new screen is ready, play. `done` is called exactly once, when the animation ends or is skipped.
  func play(json: String, done: @escaping () -> Void) {
    guard isRunning, let v = view, let root = rootView, let win = window else {
      done()
      return
    }
    let req = TransitionController.parse(json)
    guard let toImage = TransitionController.capture(root, window: win, afterUpdates: true) else {
      finish()
      done()
      return
    }
    pendingDone = done
    let t: Transition
    if let w = waitingTransition, waitingName == req.name { t = w } else { t = createTransition(req.name) }
    let ms = req.durationMs > 0 ? req.durationMs : t.defaultMs
    arm(seconds: Double(ms) / 1000 + 2.5)
    v.play(to: toImage, transition: t, req: req) { [weak self] in
      self?.finish()
    }
  }

  /// The data of a loading element arrived: take a fresh picture of the new screen and let the vehicle go on.
  func refresh() {
    guard isRunning, let v = view, let root = rootView, let win = window else { return }
    guard let image = TransitionController.capture(root, window: win, afterUpdates: true) else { return }
    v.refresh(image: image)
  }

  /// Jump to the end of the running transition.
  func skip() {
    if !isRunning { return }
    if pendingDone == nil || view == nil {
      finish()
    } else {
      view?.skip()
    }
  }

  func cancel() {
    finish()
  }

  func finish() {
    watchdog?.cancel()
    watchdog = nil
    let v = view
    let done = pendingDone
    view = nil
    pendingDone = nil
    window = nil
    rootView = nil
    waitingTransition = nil
    waitingName = nil
    fromProbe = []
    isRunning = false
    v?.cancelNow()
    v?.release(images: true)
    v?.removeFromSuperview()
    done?()
  }

  private func arm(seconds: Double) {
    watchdog?.cancel()
    let item = DispatchWorkItem { [weak self] in
      self?.finish()
    }
    watchdog = item
    DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: item)
  }

  // MARK: helpers

  private static func keyWindow() -> UIWindow? {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    for scene in scenes where scene.activationState == .foregroundActive {
      if let w = scene.windows.first(where: { $0.isKeyWindow }) { return w }
    }
    for scene in scenes {
      if let w = scene.windows.first(where: { $0.isKeyWindow }) { return w }
    }
    return scenes.first?.windows.first
  }

  /// A screenshot of `view` (the React root can be transparent: paint what is behind it first).
  private static func capture(_ view: UIView, window: UIWindow, afterUpdates: Bool) -> UIImage? {
    let bounds = view.bounds
    if bounds.width <= 0 || bounds.height <= 0 { return nil }
    let format = UIGraphicsImageRendererFormat.default()
    format.scale = window.screen.scale
    let renderer = UIGraphicsImageRenderer(bounds: bounds, format: format)
    return renderer.image { ctx in
      (window.backgroundColor ?? UIColor.white).setFill()
      ctx.fill(bounds)
      view.drawHierarchy(in: bounds, afterScreenUpdates: afterUpdates)
    }
  }

  /// How many views the screen has (a new screen adds dozens: the cheapest sign that it is there).
  private static func viewCount(_ v: UIView) -> Int {
    var n = 1
    for s in v.subviews { n += viewCount(s) }
    return n
  }

  private static let probeW = 45
  private static let probeH = 100

  /// A tiny grey picture of the screen (45 x 100) used to tell "another screen" from "the same screen, pressed".
  private static func probe(_ view: UIView, window: UIWindow) -> [UInt8] {
    let format = UIGraphicsImageRendererFormat.default()
    format.scale = 1
    let small = CGSize(width: probeW, height: probeH)
    let renderer = UIGraphicsImageRenderer(size: small, format: format)
    let image = renderer.image { ctx in
      (window.backgroundColor ?? UIColor.white).setFill()
      ctx.fill(CGRect(origin: .zero, size: small))
      _ = view.drawHierarchy(in: CGRect(origin: .zero, size: small), afterScreenUpdates: false)
    }
    guard let cg = image.cgImage else { return [] }
    var px = [UInt8](repeating: 0, count: probeW * probeH)
    let space = CGColorSpaceCreateDeviceGray()
    let ok: Bool = px.withUnsafeMutableBytes { (buf: UnsafeMutableRawBufferPointer) -> Bool in
      guard let base = buf.baseAddress,
        let ctx = CGContext(
          data: base, width: probeW, height: probeH, bitsPerComponent: 8, bytesPerRow: probeW, space: space,
          bitmapInfo: CGImageAlphaInfo.none.rawValue)
      else { return false }
      ctx.draw(cg, in: CGRect(x: 0, y: 0, width: probeW, height: probeH))
      return true
    }
    return ok ? px : []
  }

  private static func meanDiff(_ a: [UInt8], _ b: [UInt8]) -> Double {
    if a.isEmpty || a.count != b.count { return 0 }
    var sum = 0
    for i in 0..<a.count { sum += abs(Int(a[i]) - Int(b[i])) }
    return Double(sum) / Double(a.count)
  }

  private static func parse(_ json: String) -> TransitionRequest {
    let obj = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any] ?? [:]

    func num(_ dict: [String: Any], _ key: String) -> Double {
      return (dict[key] as? NSNumber)?.doubleValue ?? 0
    }

    // processColor() number: ARGB (possibly unsigned or negative depending on the platform)
    let raw = (obj["accent"] as? NSNumber)?.int64Value ?? 0xFF7C_5CFF
    let argb = UInt32(truncatingIfNeeded: raw)
    let color = UIColor(
      red: F((argb >> 16) & 0xFF) / 255,
      green: F((argb >> 8) & 0xFF) / 255,
      blue: F(argb & 0xFF) / 255,
      alpha: F((argb >> 24) & 0xFF) / 255
    )

    var targets: [CGRect] = []
    var loading: [Bool] = []
    if let arr = obj["targets"] as? [[String: Any]] {
      for t in arr {
        let w = num(t, "width")
        let h = num(t, "height")
        if w > 0 && h > 0 {
          targets.append(CGRect(x: num(t, "x"), y: num(t, "y"), width: w, height: h))
          loading.append((t["loading"] as? Bool) ?? false)
        }
      }
    }

    // the solid colour behind the animation: always opaque, black by default
    let bgRaw = (obj["background"] as? NSNumber)?.int64Value ?? 0xFF00_0000
    let bg = UInt32(truncatingIfNeeded: bgRaw)
    let background = UIColor(
      red: F((bg >> 16) & 0xFF) / 255, green: F((bg >> 8) & 0xFF) / 255, blue: F(bg & 0xFF) / 255, alpha: 1)

    return TransitionRequest(
      name: (obj["name"] as? String) ?? "fade",
      origin: CGPoint(x: num(obj, "originX"), y: num(obj, "originY")),
      accent: color,
      durationMs: Int(num(obj, "durationMs")),
      targets: targets,
      loading: loading,
      background: background
    )
  }
}
