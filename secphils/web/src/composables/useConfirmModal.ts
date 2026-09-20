import { ref } from 'vue'

/**
 * Drives the shared ConfirmModal — the portal-wide replacement for native
 * confirm()/prompt(). One dialog at a time; `ask()` while one is open is a
 * no-op (never stack confirms), and close/overlay is locked while `busy` so
 * an in-flight action can't be cancelled or swallowed under an async
 * continuation (the re-opened-dialog bug class — the child owns its close
 * through the parent's open prop, and the parent only clears it on success).
 *
 * Usage:
 *   const confirm = useConfirmModal()
 *   confirm.ask({ title, message, confirmLabel, danger, run })
 *   <ConfirmModal v-bind="confirm.props" @confirm="confirm.onConfirm"
 *                 @update:open="confirm.onOpenChange" />
 *
 * `run` throws on failure (API errors surface inside the dialog, which stays
 * open so a wrong password can be fixed without starting over).
 */
export interface ConfirmRequest {
  title: string
  message?: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  /** Show a required password field (hard-delete re-auth); its value is
   *  passed to `run(password)`. */
  requirePassword?: boolean
  passwordHint?: string
  run: (password: string) => Promise<void>
}

export function useConfirmModal() {
  const request = ref<ConfirmRequest | null>(null)
  const busy = ref(false)
  const error = ref('')

  function ask(req: ConfirmRequest) {
    if (request.value) return
    error.value = ''
    busy.value = false
    request.value = req
  }

  function onOpenChange(v: boolean) {
    if (!v && !busy.value) request.value = null
  }

  async function onConfirm(password: string) {
    const req = request.value
    if (!req || busy.value) return
    busy.value = true
    error.value = ''
    try {
      await req.run(password)
      request.value = null
    } catch (e: any) {
      error.value =
        e?.response?.data?.message || e?.message || 'The action failed. Try again.'
    } finally {
      busy.value = false
    }
  }

  // A getter, not a computed ref: it lives on a plain object, and nested
  // refs do NOT auto-unwrap in templates — a computed would force
  // `confirm.props.value` in every v-bind. Reading the refs inside the
  // getter still tracks them (the getter runs during render).
  return {
    ask,
    onOpenChange,
    onConfirm,
    busy,
    get props() {
      const r = request.value
      return {
        open: r !== null,
        title: r?.title ?? '',
        message: r?.message ?? '',
        confirmLabel: r?.confirmLabel ?? 'Confirm',
        cancelLabel: r?.cancelLabel ?? 'Cancel',
        danger: r?.danger ?? false,
        requirePassword: r?.requirePassword ?? false,
        ...(r?.passwordHint ? { passwordHint: r.passwordHint } : {}),
        busy: busy.value,
        error: error.value,
      }
    },
  }
}
