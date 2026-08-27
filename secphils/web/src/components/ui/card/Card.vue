<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  class?: HTMLAttributes['class']
  size?: 'default' | 'sm'
  active?: boolean
}>(), {
  size: 'default',
  active: false,
})
</script>

<template>
  <div
    data-slot="card"
    :data-size="size"
    :class="cn('bg-card text-card-foreground gap-6 overflow-hidden rounded-lg py-6 text-sm shadow-xs has-[>img:first-child]:pt-0 data-[size=sm]:gap-4 data-[size=sm]:py-4 *:[img:first-child]:rounded-t-lg *:[img:last-child]:rounded-b-lg group/card flex flex-col', active ? 'ring-2 ring-offset-2 ring-offset-background ring-primary' : 'ring-1 ring-foreground/10', props.class)"
  >
    <slot />
  </div>
</template>
