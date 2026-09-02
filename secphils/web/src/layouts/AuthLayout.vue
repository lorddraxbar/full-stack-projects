<script setup lang="ts">
import { RouterView } from 'vue-router'
import { usePortalName } from '../composables/useBrand'
const { portalName } = usePortalName()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-emerald-50 to-emerald-100">
    <div class="w-full max-w-md">
      <div class="bg-white rounded-lg shadow-lg p-8">
        <div class="text-center mb-8">
          <h1 class="text-2xl font-bold text-gray-900">{{ portalName }}</h1>
          <p class="text-sm text-gray-600 mt-2">Welcome Back!</p>
        </div>
        <RouterView />
      </div>
    </div>
  </div>
</template>
