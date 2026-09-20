<script setup lang="ts">
import { computed, watch } from 'vue'

/**
 * Shared pagination footer for every list surface (projects, documents,
 * reviews, announcements, admin tables, conversations).
 *
 * Deliberately dumb: it knows only `page`, `total`, and `pageSize` and emits
 * `update:page`. It works identically whether the caller pages client-side
 * (slice a full fetch) or server-side (re-query per page). It renders as long
 * as there is at least one row; on a single page the Prev/Next buttons are
 * present but disabled, so every list shows the same footer (consistency over
 * necessity). `total > pageSize` is where the nav actually does something.
 *
 * SELF-CLAMP (portal-wide rule): when the row count shrinks under the current
 * page — deleting the last row on page 2+, or an empty filter — the component
 * writes the page back to the last valid one. Without this, a delete on the
 * final row of a trailing page leaves the list showing a phantom-empty page
 * ("nothing found" while rows exist) on EVERY list surface that pages. The
 * clamp lives here, at the single choke point all lists share, instead of
 * ten copies of a length-watcher.
 */
const props = defineProps<{
  /** 1-based current page. */
  page: number
  /** Total number of rows (after any filter/search). */
  total: number
  /** Rows per page. */
  pageSize: number
  /** Optional label override (default: "Showing …"). */
  label?: string
}>()

const emit = defineEmits<{
  (e: 'update:page', page: number): void
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

// Clamp only when there ARE rows — at total 0 the component renders nothing,
// and resetting the page of an empty list would fight a caller that
// intentionally restores a saved page position.
watch(totalPages, (max) => {
  if (props.total > 0 && props.page > max) emit('update:page', max)
}, { immediate: true })
const start = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.pageSize + 1))
const end = computed(() => Math.min(props.page * props.pageSize, props.total))
const canPrev = computed(() => props.page > 1)
const canNext = computed(() => props.page < totalPages.value)

function prev() {
  if (canPrev.value) emit('update:page', props.page - 1)
}
function next() {
  if (canNext.value) emit('update:page', props.page + 1)
}
</script>

<template>
  <div
    v-if="total > 0"
    class="flex items-center justify-between gap-3 px-4 py-3 border-t border-gray-200"
  >
    <span class="text-xs sm:text-sm text-gray-500 tabular-nums">
      {{ label || 'Showing' }} {{ start }}–{{ end }} of {{ total }}
    </span>
    <div class="flex items-center gap-1.5">
      <button
        type="button"
        :disabled="!canPrev"
        @click="prev"
        class="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium border border-gray-300 text-gray-600 hover:bg-gray-50 hover:text-gray-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
      >
        <i class="fas fa-chevron-left text-[11px]" />
        <span class="hidden sm:inline">Previous</span>
      </button>
      <span class="px-1.5 text-xs text-gray-400 tabular-nums whitespace-nowrap">
        {{ page }} / {{ totalPages }}
      </span>
      <button
        type="button"
        :disabled="!canNext"
        @click="next"
        class="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium border border-gray-300 text-gray-600 hover:bg-gray-50 hover:text-gray-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
      >
        <span class="hidden sm:inline">Next</span>
        <i class="fas fa-chevron-right text-[11px]" />
      </button>
    </div>
  </div>
</template>
