package expo.modules.deliverytransitions

import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * JavaScript API (all options travel as ONE JSON string, so there is no custom type to convert):
 *   begin()              freeze the current screen
 *   play(optionsJson)    the new screen is ready: animate. Resolves when the animation ends or is skipped.
 *   skip() / cancel() / isRunning()
 */
class DeliveryTransitionsModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("DeliveryTransitions")

    AsyncFunction("begin") { promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("E_NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }
      activity.runOnUiThread {
        try {
          TransitionController.begin(activity)
          promise.resolve()
        } catch (t: Throwable) {
          TransitionController.finish()
          promise.reject("E_BEGIN", t.message ?: "begin failed", t)
        }
      }
    }

    AsyncFunction("play") { optionsJson: String, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.resolve()
        return@AsyncFunction
      }
      activity.runOnUiThread {
        try {
          TransitionController.play(activity, optionsJson) { promise.resolve() }
        } catch (t: Throwable) {
          TransitionController.finish()
          promise.reject("E_PLAY", t.message ?: "play failed", t)
        }
      }
    }

    // optional: start the animation right away while the new screen is still rendering (Android)
    Function("preroll") { optionsJson: String ->
      val activity = appContext.currentActivity
      activity?.runOnUiThread {
        try {
          TransitionController.preroll(activity, optionsJson)
        } catch (_: Throwable) {
        }
      }
    }

    Function("skip") { -> TransitionController.skip() }
    Function("cancel") { -> TransitionController.cancel() }
    Function("isRunning") { -> TransitionController.isRunning }
  }
}
