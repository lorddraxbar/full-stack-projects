import { ref, computed } from 'vue'

/**
 * Portal-wide busy tracker (pairs with components/BusyBar.vue).
 *
 * A ref-count driven from the shared axios interceptors (services/api.ts):
 * every request the portal makes — page loads, saves, uploads, the slow
 * notification-fanout-backed creates — increments while in flight and
 * decrements on settle. BusyBar renders whenever the count is > 0.
 *
 * Why ref-count, not boolean: parallel requests (the dashboard fires ~6 on
 * mount) would let the first finisher hide the bar while others still run.
 *
 * Why this exists: the wizard-create "silence" incident — a create whose
 * server side fanned out mail could stall tens of seconds with zero visual
 * feedback; the user assumed the portal was broken. Even with server-side
 * async mail now, ANY slow call gets honest feedback, portal-wide, from one
 * mount point — no per-view spinners to forget.
 */
const pending = ref(0)

export function useBusy() {
  return {
    busy: computed(() => pending.value > 0),
    /** Called by the axios request interceptor. */
    start() { pending.value++ },
    /** Called by the axios response/error interceptor. Idempotent floor at 0. */
    end() { pending.value = Math.max(0, pending.value - 1) },
  }
}
