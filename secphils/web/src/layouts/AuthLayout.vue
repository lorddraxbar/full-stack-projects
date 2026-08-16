<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100">
    <div class="w-full max-w-md">
      <div class="bg-white rounded-lg shadow-lg p-8">
        <div class="text-center mb-8">
          <h1 class="text-2xl font-bold text-gray-900">SecPhils Client Portal</h1>
          <p class="text-sm text-gray-600 mt-2">Engineering Consultancy Platform</p>
        </div>
        <RouterView />
      </div>
    </div>
  </div>
</template>
