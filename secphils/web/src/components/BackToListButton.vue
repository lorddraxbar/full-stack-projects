<script setup lang="ts">
import { useRouter } from 'vue-router'
// Standard back-to-list affordance for detail screens (the app's only
// route-based detail screen is /projects/:id; list+modal views need none).
// Usage: <BackToListButton to="Projects" />
const props = defineProps<{
  to: string
}>()
const router = useRouter()
</script>

<template>
  <button
    type="button"
    class="inline-flex items-center gap-2 text-sm font-medium text-gray-600 hover:text-emerald-700 transition-colors"
    @click="router.push({ name: props.to })"
  >
    <i class="fas fa-arrow-left text-xs"></i>
    Back to list
  </button>
</template>
