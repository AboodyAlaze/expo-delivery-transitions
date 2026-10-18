/**
 * Link between `<DeliveryTarget loading>` and the running transition (kept in its own file so both can import it).
 * While a transition runs, `runTransition` installs a handler; a target that stops loading calls `notifyLoaded()`.
 */
let handler: (() => void) | null = null;

export function setLoadedHandler(next: (() => void) | null) {
  handler = next;
}

export function notifyLoaded() {
  handler?.();
}
