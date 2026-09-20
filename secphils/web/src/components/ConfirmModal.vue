<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

/**
 * Standard confirmation dialog for destructive or irreversible actions —
 * the portal-wide replacement for native confirm()/prompt() (those render as
 * browser chrome, off-brand, and a prompt() types passwords in plaintext).
 *
 * Usage: the parent holds the pending request; `action` is invoked on
 * confirm and its errors surface inside the dialog (the modal stays open so
 * the admin can fix a wrong password without starting over). While `busy`,
 * cancel/overlay/Esc are locked — an in-flight action cannot be cancelled,
 * and the modal can never be re-opened underneath a completing await (the
 * swallowed-dialog bug class from the async-continuation audits).
 *
 * Pass `requirePassword` for actions the backend guards with a password
 * (hard deletes inside the retention window) — the field is required and
 * submitted on the confirm event, never in a URL.
 */
const props = withDefaults(defineProps<{
  open: boolean
  title: string
  message?: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  busy?: boolean
  error?: string
  requirePassword?: boolean
  passwordHint?: string
  /** Single-line text input mode (renames, short required text — the
   *  replacement for native prompt()). Prefilled with inputValue; its value
   *  arrives as the confirm event's second argument and run()'s second. */
  input?: boolean
  inputValue?: string
  inputLabel?: string
}>(), {
  message: '',
  confirmLabel: 'Confirm',
  cancelLabel: 'Cancel',
  danger: false,
  busy: false,
  error: '',
  requirePassword: false,
  passwordHint: 'Your password confirms this action is really yours.',
  input: false,
  inputValue: '',
  inputLabel: 'Value',
})

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'confirm', password: string, input: string): void
}>()

const password = ref('')
const inputValue = ref('')
watch(() => props.open, (v) => { if (v) { password.value = ''; inputValue.value = props.inputValue || '' } })

const confirmDisabled = computed(
  () => props.busy
    || (props.requirePassword && password.value.length === 0)
    || (props.input && inputValue.value.trim().length === 0))

function submit() {
  if (!props.open || confirmDisabled.value) return
  emit('confirm', password.value, props.input ? inputValue.value.trim() : '')
}

function onKey(e: KeyboardEvent) {
  if (!props.open) return
  if (e.key === 'Enter' && !confirmDisabled.value) submit()
  if (e.key === 'Escape' && !props.busy) emit('update:open', false)
}

// Document-level Esc/Enter: a listener on the modal root misses the key when
// focus sits outside the dialog (body, page background). Attach while open,
// detach otherwise — Esc must be ignored while busy (an in-flight destructive
// action cannot be cancelled), which the handler enforces via props.busy.
watch(() => props.open, (v) => {
  if (typeof document === 'undefined') return
  if (v) document.addEventListener('keydown', onKey)
  else document.removeEventListener('keydown', onKey)
}, { immediate: true })
onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center p-4"
    @keydown="onKey"
  >
    <div
      class="absolute inset-0 bg-black/30"
      @click="!busy && emit('update:open', false)"
    />
    <div
      class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6"
      role="dialog"
      aria-modal="true"
      tabindex="-1"
    >
      <h2 class="text-lg font-semibold text-gray-900 mb-2">{{ title }}</h2>
      <p v-if="message" class="text-sm text-gray-600 mb-4 whitespace-pre-line">{{ message }}</p>
      <div v-if="requirePassword" class="mb-1">
        <label class="block text-sm font-medium text-gray-700 mb-1">
          Your password <span class="text-red-500">*</span>
        </label>
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          placeholder="********"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
          @keyup.enter="submit"
        />
        <p class="mt-1 text-xs text-gray-500">{{ passwordHint }}</p>
      </div>
      <div v-if="input" class="mb-1">
        <label class="block text-sm font-medium text-gray-700 mb-1">
          {{ inputLabel }} <span class="text-red-500">*</span>
        </label>
        <input
          v-model="inputValue"
          type="text"
          autocomplete="off"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
          @keyup.enter="submit"
        />
      </div>
      <p v-if="error" class="mt-3 text-sm text-red-600">{{ error }}</p>
      <div class="flex justify-end gap-3 mt-5">
        <button
          :disabled="busy"
          class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors text-sm font-medium disabled:opacity-50"
          @click="emit('update:open', false)"
        >
          {{ cancelLabel }}
        </button>
        <button
          :disabled="confirmDisabled"
          :class="[
            'px-4 py-2 rounded-lg transition-colors text-sm font-medium text-white disabled:opacity-50',
            danger ? 'bg-red-600 hover:bg-red-700' : 'bg-emerald-600 hover:bg-emerald-700',
          ]"
          @click="submit"
        >
          {{ busy ? 'Working…' : confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>
