<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const status = ref('Authenticating...')
const error = ref('')

onMounted(async () => {
  try {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    const error_msg = params.get('error')

    if (error_msg) {
      error.value = decodeURIComponent(error_msg)
      status.value = 'Authentication failed'
      return
    }

    if (!token) {
      error.value = 'No authentication token received'
      status.value = 'SSO callback failed'
      return
    }

    status.value = 'Processing SSO token...'

    // Store token and redirect
    localStorage.setItem('accessToken', token)
    localStorage.setItem('userRole', 'CLIENT')
    localStorage.setItem('userName', 'SSO User')

    router.push('/dashboard')
  } catch (err: any) {
    error.value = err.message || 'SSO authentication failed'
    status.value = 'Error'
  }
})
</script>

<template>
  <div class="text-center py-12">
    <div v-if="status === 'Processing SSO token...'" class="mb-4">
      <svg class="animate-spin h-10 w-10 text-blue-600 mx-auto" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
    </div>

    <h2 class="text-xl font-semibold text-gray-900 mb-2">{{ status }}</h2>

    <div v-if="error" class="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
      {{ error }}
    </div>

    <p class="text-sm text-gray-600 mt-4">Redirecting you to your dashboard...</p>
  </div>
</template>
