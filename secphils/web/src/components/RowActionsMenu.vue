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
const btnRef = ref<HTMLElement | null>(null)

// Fixed-position style for the menu, computed so it never clips inside
// an overflow container and flips upward when near the bottom viewport edge.
const posStyle = ref<{ top: string; left: string }>({ top: '0px', left: '0px' })
const flip = ref(false)

const MENU_W = 176   // matches w-44 (11rem)
const EST_ITEM_H = 38

const toggle = () => {
  if (open.value) {
    close()
  } else {
    openMenu()
  }
}
const close = () => { open.value = false }

const openMenu = () => {
  const btn = btnRef.value
  if (!btn) return
  const rect = btn.getBoundingClientRect()
  const menuH = Math.min(360, (props.actions.filter(a => !a.divider).length * EST_ITEM_H) + 16)
  const spaceBelow = window.innerHeight - rect.bottom
  flip.value = spaceBelow < menuH
  const top = flip.value ? rect.top - menuH - 6 : rect.bottom + 6
  const left = Math.max(8, Math.min(rect.right - MENU_W, window.innerWidth - MENU_W - 8))
  posStyle.value = { top: `${Math.max(8, top)}px`, left: `${left}px` }
  open.value = true
}

const run = (action: RowAction) => {
  close()
  action.onClick()
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') close()
}

const onDocClick = (e: MouseEvent) => {
  if (open.value && menuRef.value && !menuRef.value.contains(e.target as Node)
      && btnRef.value && !btnRef.value.contains(e.target as Node)) close()
}

const onScroll = () => { if (open.value) close() }

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('scroll', onScroll, true)
  window.addEventListener('resize', onScroll)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', onScroll)
})
</script>

<template>
  <div ref="btnRef" class="inline-block text-left">
    <button
      type="button"
      @click.stop="toggle"
      class="p-1.5 rounded-md text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors"
      :aria-label="open ? 'Close actions menu' : 'Open actions menu'"
      :aria-expanded="open"
    >
      <i class="fas fa-ellipsis-vertical text-sm" />
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        ref="menuRef"
        :style="posStyle"
        :class="[
          'fixed z-50 w-44 bg-white rounded-lg shadow-lg border border-gray-200 py-1',
          flip ? 'mb-1' : 'mt-1'
        ]"
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
    </Teleport>
  </div>
</template>
