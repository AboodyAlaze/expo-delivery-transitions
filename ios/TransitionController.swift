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
    isRunning = true
    arm(seconds: 8)  // nothing can keep the app blocked for ever
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
    let t = createTransition(req.name)
    let ms = req.durationMs > 0 ? req.durationMs : t.defaultMs
    arm(seconds: Double(ms) / 1000 + 2.5)
    v.play(to: toImage, transition: t, req: req) { [weak self] in
      self?.finish()
    }
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
    isRunning = false
    v?.cancelNow()
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
    if let arr = obj["targets"] as? [[String: Any]] {
      for t in arr {
        let w = num(t, "width")
        let h = num(t, "height")
        if w > 0 && h > 0 {
          targets.append(CGRect(x: num(t, "x"), y: num(t, "y"), width: w, height: h))
        }
      }
    }

    return TransitionRequest(
      name: (obj["name"] as? String) ?? "fade",
      origin: CGPoint(x: num(obj, "originX"), y: num(obj, "originY")),
      accent: color,
      durationMs: Int(num(obj, "durationMs")),
      targets: targets
    )
  }
}
