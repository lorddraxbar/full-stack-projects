<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

export interface RowAction {
  label: string
  onClick: () => void
  /** tailwind text color classes for the label, e.g. 'text-red-600 hover:text-red-700' */
  color?: string
  /** render as a separator line before this item */
  divider?: boolean
}

const props = defineProps<{
  actions: RowAction[]
}>()

const open = ref(false)
const menuRef = ref<HTMLElement | null>(null)

const toggle = () => { open.value = !open.value }
const close = () => { open.value = false }

const run = (action: RowAction) => {
  close()
  action.onClick()
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') close()
}

const onDocClick = (e: MouseEvent) => {
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) close()
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="menuRef" class="relative inline-block text-left">
    <button
      type="button"
      @click.stop="toggle"
      class="p-1.5 rounded-md text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors"
      :aria-label="open ? 'Close actions menu' : 'Open actions menu'"
      :aria-expanded="open"
    >
      <i class="fas fa-ellipsis-vertical text-sm" />
    </button>
    <div
      v-if="open"
      class="absolute right-0 z-30 mt-1 w-44 bg-white rounded-lg shadow-lg border border-gray-200 py-1"
    >
      <template v-for="(action, i) in props.actions" :key="i">
        <div v-if="action.divider" class="my-1 border-t border-gray-100" />
        <button
          type="button"
          @click="run(action)"
          :class="[
            'w-full text-left px-4 py-2 text-sm font-medium transition-colors',
            action.color || 'text-gray-700 hover:bg-gray-50 hover:text-gray-900'
          ]"
        >
          {{ action.label }}
        </button>
      </template>
    </div>
  </div>
</template>
