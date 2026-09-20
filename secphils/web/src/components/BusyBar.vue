<script setup lang="ts">
import { ref, watch } from 'vue'
import { useBusy } from '@/composables/useBusy'

/**
 * Portal-wide loading indicator: an indeterminate top bar above everything,
 * driven by the axios in-flight counter (composables/useBusy.ts). Appears on
 * ANY portal request — including the long ones (notification-backed creates,
 * uploads) that used to look like a frozen portal. Public pages ride the same
 * axios instance, so landing/contact get it too.
 *
 * Shows only after a 350ms delay: routine sub-second calls (and the periodic
 * notification poll) must NOT blink it every few seconds — only genuinely
 * slow requests earn the feedback. Hides instantly when the last call settles.
 *
 * Deliberately thin + non-blocking: it never masks the UI or eats clicks;
 * buttons keep their own local busy states for the disable-during-submit
 * behavior. This answers "is the portal working on it?" globally.
 */
const { busy } = useBusy()
const visible = ref(false)
let showTimer: ReturnType<typeof setTimeout> | null = null

watch(busy, (b) => {
  if (showTimer) { clearTimeout(showTimer); showTimer = null }
  if (b) {
    showTimer = setTimeout(() => { visible.value = true }, 350)
  } else {
    visible.value = false
  }
})
</script>

<template>
  <div
    v-if="visible"
    class="fixed top-0 left-0 right-0 z-[100] h-[3px] overflow-hidden"
    role="progressbar"
    aria-label="Loading"
    aria-busy="true"
  >
    <div class="busybar-sweep absolute inset-y-0 w-1/3 rounded-full" />
  </div>
</template>

<style scoped>
.busybar-sweep {
  background: linear-gradient(90deg, transparent, var(--primary, #1e6bb8) 35%, var(--primary, #1e6bb8) 65%, transparent);
  animation: busybar 1.15s ease-in-out infinite;
}
@keyframes busybar {
  0% { left: -35%; }
  100% { left: 100%; }
}
@media (prefers-reduced-motion: reduce) {
  .busybar-sweep { animation-duration: 2.4s; }
}
</style>
