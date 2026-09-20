import { ref } from 'vue'

/**
 * Portal-wide toast standard — the ONLY sanctioned way to surface transient
 * action feedback (replaced raw alert() and three divergent per-view flash
 * banners, 2026-09-21). Rules:
 *   • alert() is banned: it blocks the UI thread and renders as browser
 *     chrome — off-brand, and hostile on mobile (Jaybar tests on a phone).
 *   • No per-view flash/notice banner clones anymore — route everything
 *     through toast() so success/error styling, timing and placement stay
 *     identical across surfaces.
 *   • ConfirmModal.run() errors stay IN the dialog (fixable without
 *     restarting the action); toasts are for what happens AFTER an action
 *     completes or when no dialog owns the moment.
 *
 * Module-level state: one stack for the whole app, mounted once by
 * <ToastHub> in App.vue. Max 4 visible; oldest evicted. Auto-dismiss after
 * `duration` ms (5s default; errors linger a little longer at 7s so a
 * server message can actually be read on a phone).
 *
 * Usage:
 *   import { toast } from '@/composables/useToast'
 *   toast.success('Profile updated')
 *   toast.error(e?.response?.data?.message || 'Failed to save')
 */
export interface Toast {
  id: number
  type: 'success' | 'error' | 'info'
  text: string
}

const toasts = ref<Toast[]>([])
let nextId = 1
const timers = new Map<number, ReturnType<typeof setTimeout>>()

function dismiss(id: number) {
  const t = timers.get(id)
  if (t) { clearTimeout(t); timers.delete(id) }
  toasts.value = toasts.value.filter(x => x.id !== id)
}

function push(type: Toast['type'], text: string, duration?: number) {
  const id = nextId++
  // Cap the stack: never cover a phone screen with a tower of toasts.
  if (toasts.value.length >= 4) dismiss(toasts.value[0].id)
  toasts.value = [...toasts.value, { id, type, text }]
  const ms = duration ?? (type === 'error' ? 7000 : 5000)
  timers.set(id, setTimeout(() => dismiss(id), ms))
}

export const toast = {
  success: (text: string, duration?: number) => push('success', text, duration),
  error: (text: string, duration?: number) => push('error', text, duration),
  info: (text: string, duration?: number) => push('info', text, duration),
  dismiss,
}

/** Exposed for ToastHub + tests; consumers use `toast`, not this. */
export function useToast() {
  return { toasts, dismiss }
}
