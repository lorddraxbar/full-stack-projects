<script setup lang="ts">
import { RouterView } from 'vue-router'
import ToastHub from '@/components/ToastHub.vue'
import BusyBar from '@/components/BusyBar.vue'
import { refreshBrandTheme } from '@/composables/useBrandTheme'

// Apply the admin-configured brand scheme to the whole shell (public /landing
// payload — works pre-login and for every role). Falls back to the cached
// scheme applied synchronously in main.ts.
void refreshBrandTheme()
</script>

<template>
  <RouterView />
  <!-- Portal-wide busy indicator (composables/useBusy.ts via axios counters) -->
  <BusyBar />
  <!-- Portal-wide toast stack (see composables/useToast.ts) -->
  <ToastHub />
</template>