import ExpoModulesCore

/// JavaScript API (all options travel as ONE JSON string, so there is no custom type to convert):
///   begin()              freeze the current screen
///   play(optionsJson)    the new screen is ready: animate. Resolves when the animation ends or is skipped.
///   skip() / cancel() / isRunning()
public class DeliveryTransitionsModule: Module {
  public func definition() -> ModuleDefinition {
    Name("DeliveryTransitions")

    AsyncFunction("begin") { () -> Void in
      TransitionController.shared.begin()
    }
    .runOnQueue(.main)

    AsyncFunction("play") { (optionsJson: String, promise: Promise) in
      TransitionController.shared.play(json: optionsJson) {
        promise.resolve()
      }
    }
    .runOnQueue(.main)

    // optional: start the animation right away while the new screen is still rendering
    Function("preroll") { (optionsJson: String) -> Void in
      DispatchQueue.main.async {
        TransitionController.shared.preroll(json: optionsJson)
      }
    }

    // resolves when the NEW screen is really on screen (view tree / picture changed), or after the timeout
    AsyncFunction("screenReady") { (timeoutMs: Int, promise: Promise) in
      TransitionController.shared.waitForScreen(timeoutMs: timeoutMs) {
        promise.resolve()
      }
    }
    .runOnQueue(.main)

    // the data of a loading element arrived: fresh picture of the new screen, the vehicle goes on
    Function("refresh") { () -> Void in
      DispatchQueue.main.async {
        TransitionController.shared.refresh()
      }
    }

    Function("skip") { () -> Void in
      DispatchQueue.main.async {
        TransitionController.shared.skip()
      }
    }

    Function("cancel") { () -> Void in
      DispatchQueue.main.async {
        TransitionController.shared.cancel()
      }
    }

    Function("isRunning") { () -> Bool in
      return TransitionController.shared.isRunning
    }
  }
}
