import UIKit

/// The full-screen overlay. It shows the frozen OLD screen and later plays a `Transition` between the two screenshots
/// by moving Core Animation layers (cheap, on the GPU). Any tap skips to the end.
///
/// Layer order (bottom to top):  fromLayer (old screen) -> stage (layers a transition adds) -> toContainer (new screen)
/// -> overlay (vector extras drawn with Core Graphics) -> skip hint.
final class TransitionView: UIView {
  let fromLayer = CALayer()
  let stage = CALayer()
  let toContainer = CALayer()
  let toLayer = CALayer()
  private let overlay = OverlayView()
  private let hint = UILabel()

  let fromImage: UIImage
  private(set) var toImage: UIImage?
  fileprivate(set) var progress: F = 0
  fileprivate(set) var transition: Transition?

  private var link: CADisplayLink?
  private var startTime: CFTimeInterval = 0
  private var duration: Double = 1
  private var done = false
  private var onFinished: (() -> Void)?

  init(frame: CGRect, from: UIImage) {
    self.fromImage = from
    super.init(frame: frame)
    backgroundColor = UIColor.clear
    isOpaque = false
    isUserInteractionEnabled = true  // swallow touches: nothing underneath can be tapped while the overlay is there

    for l in [fromLayer, stage, toContainer] {
      l.frame = bounds
      layer.addSublayer(l)
    }
    fromLayer.contents = from.cgImage
    fromLayer.contentsScale = from.scale
    fromLayer.contentsGravity = .resize

    toLayer.frame = bounds
    toLayer.contentsGravity = .resize
    toContainer.addSublayer(toLayer)
    toContainer.isHidden = true

    overlay.frame = bounds
    overlay.host = self
    overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(overlay)

    hint.text = "Tap to skip"
    hint.font = UIFont.systemFont(ofSize: 12)
    hint.textColor = UIColor.white.withAlphaComponent(0.85)
    hint.backgroundColor = UIColor.black.withAlphaComponent(0.4)
    hint.textAlignment = .center
    hint.layer.cornerRadius = 12
    hint.clipsToBounds = true
    hint.sizeToFit()
    hint.frame = CGRect(x: 0, y: 0, width: hint.bounds.width + 24, height: 24)
    hint.center = CGPoint(x: bounds.midX, y: bounds.height - 44)
    hint.autoresizingMask = [.flexibleLeftMargin, .flexibleRightMargin, .flexibleTopMargin]
    hint.isHidden = true
    addSubview(hint)

    addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapped)))
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  deinit {
    link?.invalidate()
  }

  func play(to image: UIImage, transition t: Transition, req: TransitionRequest, finished: @escaping () -> Void) {
    // IMPORTANT: every property change of these stand-alone layers (opacity, position, anchorPoint, mask...) is
    // IMPLICITLY animated by Core Animation (0.25 s) unless actions are disabled. Without this, un-hiding the new
    // screen and then setting its opacity to 0 made the whole new page flash over the list for a quarter of a second.
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    toImage = image
    toLayer.contents = image.cgImage
    toLayer.contentsScale = image.scale
    toContainer.opacity = 1
    toContainer.isHidden = false
    transition = t
    onFinished = finished
    done = false
    progress = 0
    t.prepare(host: self, req: req)
    t.update(p: 0, host: self)
    CATransaction.commit()
    if t.wantsOverlay { overlay.setNeedsDisplay() }
    hint.isHidden = !t.showsSkipHint

    let ms = req.durationMs > 0 ? req.durationMs : t.defaultMs
    duration = max(Double(ms) / 1000, 0.05)
    startTime = CACurrentMediaTime()
    let l = CADisplayLink(target: self, selector: #selector(tick(_:)))
    l.add(to: RunLoop.main, forMode: RunLoop.Mode.common)
    link = l
  }

  @objc private func tick(_ l: CADisplayLink) {
    let x = min(max((CACurrentMediaTime() - startTime) / duration, 0), 1)
    let eased = transition?.easing(x) ?? 1
    step(p: F(eased))
    if x >= 1 { finish() }
  }

  private func step(p: F) {
    progress = p
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    transition?.update(p: p, host: self)
    CATransaction.commit()
    if transition?.wantsOverlay == true { overlay.setNeedsDisplay() }
  }

  /// Jump to the end of the running transition.
  func skip() {
    if done || transition == nil { return }
    step(p: 1)
    finish()
  }

  func cancelNow() {
    finish()
  }

  private func finish() {
    if done { return }
    done = true
    link?.invalidate()
    link = nil
    let cb = onFinished
    onFinished = nil
    cb?()
  }

  @objc private func tapped() {
    skip()
  }
}

/// Transparent view on top: draws the vector extras of a transition (vehicles, glass, radar line...).
private final class OverlayView: UIView {
  weak var host: TransitionView?

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
    guard let c = UIGraphicsGetCurrentContext(), let h = host, let t = h.transition, t.wantsOverlay else {
      return
    }
    t.drawOverlay(c, p: h.progress, size: bounds.size)
  }
}
