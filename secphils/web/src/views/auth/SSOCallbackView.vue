<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useGoogleSsoCallback } from '@/services/api'
import { useRole } from '@/composables/useRole'

const router = useRouter()
const { setRole } = useRole()

const status = ref('Completing Google sign-in…')
const error = ref('')
const redirecting = ref(false)

function storeSession(data: any) {
  localStorage.setItem('accessToken', data.accessToken)
  localStorage.setItem('refreshToken', data.refreshToken)
  setRole(data.user?.role || 'CLIENT')
  localStorage.setItem('userName', data.user?.fullName || 'User')
  if (data.user?.id) localStorage.setItem('userId', String(data.user.id))
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const state = params.get('state')
  const errorMsg = params.get('error')

  if (errorMsg) {
    error.value = decodeURIComponent(errorMsg)
    status.value = 'Google sign-in failed'
    return
  }

  if (!code || !state) {
    error.value = 'Incomplete Google sign-in response. Please try again.'
    status.value = 'Google sign-in failed'
    return
  }

  status.value = 'Completing Google sign-in…'
  redirecting.value = true

  try {
    const data = await useGoogleSsoCallback(code, state)

    // A brand-new SSO account with 2FA pending shouldn't silently lose the
    // session — bounce them back to the normal login to finish authenticating.
    if (data.requires2fa && data.pendingToken) {
      error.value = 'Two-factor authentication is required for this account. Please sign in with your password.'
      status.value = '2FA required'
      redirecting.value = false
      return
    }

    storeSession(data)
    const redirect = (router.currentRoute.value.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (err: any) {
    error.value = err?.response?.data?.message || err.message || 'Google sign-in failed'
    status.value = 'Google sign-in failed'
  } finally {
    redirecting.value = false
  }
})
</script>

<template>
  <div class="text-center py-12">
    <div v-if="redirecting && !error" class="mb-4">
      <svg class="animate-spin h-10 w-10 text-emerald-600 mx-auto" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
    </div>

    <h2 class="text-xl font-semibold text-gray-900 mb-2">{{ status }}</h2>

    <div v-if="error" class="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
      {{ error }}
    </div>

    <p v-if="redirecting && !error" class="text-sm text-gray-600 mt-4">Redirecting you to your dashboard...</p>

    <div v-else-if="error" class="mt-6">
      <router-link
        to="/auth/login"
        class="inline-block px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium"
      >
        Back to sign in
      </router-link>
    </div>
  </div>
</template>