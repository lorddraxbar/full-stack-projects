<script setup lang="ts">
import { useToast } from '@/composables/useToast'

/**
 * Renders the portal-wide toast stack (see useToast.ts for the rules).
 * Mounted ONCE in App.vue. z-[70] sits above modals (z-50) so feedback
 * from a dialog action is visible while the dialog is still on screen;
 * pointer-events pass through the container, each toast keeps its own.
 */
const { toasts, dismiss } = useToast()

const styles: Record<string, string> = {
  success: 'bg-emerald-50 border-emerald-300 text-emerald-900',
  error: 'bg-red-50 border-red-300 text-red-800',
  info: 'bg-sky-50 border-sky-300 text-sky-900',
}
const icons: Record<string, string> = {
  success: 'fas fa-circle-check text-emerald-600',
  error: 'fas fa-triangle-exclamation text-red-600',
  info: 'fas fa-circle-info text-sky-600',
}
</script>

<template>
  <div
    class="fixed top-4 right-4 z-[70] flex flex-col gap-2 w-[calc(100vw-2rem)] max-w-sm pointer-events-none"
    role="region"
    aria-label="Notifications"
    aria-live="polite"
  >
    <TransitionGroup name="toast">
      <div
        v-for="t in toasts"
        :key="t.id"
        :class="[
          'toast-item pointer-events-auto flex items-start gap-3 p-3 rounded-lg shadow-lg border text-sm',
          styles[t.type],
        ]"
        data-testid="toast"
        :data-toast-type="t.type"
      >
        <i :class="[icons[t.type], 'mt-0.5 flex-shrink-0']" aria-hidden="true" />
        <span class="flex-1 min-w-0 break-words">{{ t.text }}</span>
        <button
          class="opacity-50 hover:opacity-100 flex-shrink-0 transition-opacity"
          aria-label="Dismiss notification"
          @click="dismiss(t.id)"
        >
          <i class="fas fa-xmark" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active { transition: all 0.25s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; position: absolute; right: 0; left: 0; }
.toast-enter-from { opacity: 0; transform: translateX(24px); }
.toast-leave-to { opacity: 0; transform: translateX(24px); }
</style>
