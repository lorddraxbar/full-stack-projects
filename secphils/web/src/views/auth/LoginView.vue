<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useGetSsoStatus, useGoogleSsoAuthorize } from '@/services/api'
import { useRole } from '@/composables/useRole'

const router = useRouter()
const { setRole } = useRole()
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const error = ref('')
const loading = ref(false)

// Google SSO
const ssoEnabled = ref(false)
const ssoLoading = ref(false)
const handleGoogleLogin = async () => {
  ssoLoading.value = true
  error.value = ''
  try {
    const { url } = await useGoogleSsoAuthorize()
    window.location.href = url
  } catch (err: any) {
    error.value = err?.response?.data?.message || err.message || 'Unable to start Google sign-in'
    ssoLoading.value = false
  }
}
onMounted(async () => {
  try {
    const s = await useGetSsoStatus()
    ssoEnabled.value = !!(s.googleEnabled && s.googleConfigured)
  } catch {
    ssoEnabled.value = false
  }
})

const awaiting2fa = ref(false)
const pendingToken = ref('')
const code = ref('')
const verifyLoading = ref(false)

function storeSession(data: any) {
  localStorage.setItem('accessToken', data.accessToken)
  localStorage.setItem('refreshToken', data.refreshToken)
  setRole(data.user?.role || 'CLIENT')
  localStorage.setItem('userName', data.user?.fullName || 'User')
  if (data.user?.id) localStorage.setItem('userId', String(data.user.id))
  // Admin-configurable brand + app title (echoed on every login/SSO path) so
  // the drawer wordmark and document.title can render them without a fetch.
  if (data.brand) localStorage.setItem('brandName', data.brand)
  if (data.portalName) localStorage.setItem('portalName', data.portalName)
}

function finishLogin() {
  const redirect = (router.currentRoute.value.query.redirect as string) || '/dashboard'
  router.push(redirect)
}

const handleLogin = async () => {
  if (!email.value || !password.value) {
    error.value = 'Please enter email and password'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value, password: password.value }),
    })

    const contentType = response.headers.get('content-type')
    if (!contentType || !contentType.includes('application/json')) {
      error.value = 'Backend API is not running. Login endpoint unavailable.'
      return
    }

    if (!response.ok) {
      const data = await response.json()
      throw new Error(data.message || 'Login failed')
    }

    const data = await response.json()
    if (data.requires2fa && data.pendingToken) {
      pendingToken.value = data.pendingToken
      awaiting2fa.value = true
      error.value = ''
      return
    }

    storeSession(data)
    finishLogin()
  } catch (err: any) {
    error.value = err.message || 'Login failed. Please try again.'
  } finally {
    loading.value = false
  }
}

const handle2faVerify = async () => {
  if (code.value.length !== 6) {
    error.value = 'Enter the 6-digit code from your authenticator app'
    return
  }
  verifyLoading.value = true
  error.value = ''
  try {
    const response = await fetch('/api/v1/auth/2fa/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pendingToken: pendingToken.value, code: code.value }),
    })
    const contentType = response.headers.get('content-type')
    if (!contentType || !contentType.includes('application/json')) {
      error.value = 'Backend API is not running.'
      return
    }
    if (!response.ok) {
      const data = await response.json()
      throw new Error(data.message || 'Invalid verification code')
    }
    const data = await response.json()
    storeSession(data)
    finishLogin()
  } catch (err: any) {
    error.value = err.message || 'Verification failed. Please try again.'
  } finally {
    verifyLoading.value = false
  }
}
</script>

<template>
  <div>
    <h2 class="text-xl font-semibold text-gray-900 mb-1">
      {{ awaiting2fa ? 'Two-Factor Authentication' : 'Sign in to your account' }}
    </h2>
    <p v-if="awaiting2fa" class="text-sm text-gray-500 mb-4">
      Enter the 6-digit code from your authenticator app to continue.
    </p>

    <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
      {{ error }}
    </div>

    <form v-if="!awaiting2fa" @submit.prevent="handleLogin" class="space-y-4">
      <div>
        <label for="email" class="block text-sm font-medium text-gray-700 mb-1">Email</label>
        <input
          id="email"
          v-model="email"
          type="email"
          required
          class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
          placeholder="you@example.com"
        />
      </div>

      <div>
        <label for="password" class="block text-sm font-medium text-gray-700 mb-1">Password</label>
        <div class="relative">
          <input
            id="password"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            required
            class="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
            placeholder="••••••••"
          />
          <button
            type="button"
            @click="showPassword = !showPassword"
            :aria-label="showPassword ? 'Hide password' : 'Show password'"
            class="absolute inset-y-0 right-0 flex items-center pr-3 text-gray-400 hover:text-gray-600 transition-colors"
          >
            <i :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'" class="text-sm" />
          </button>
        </div>
      </div>

      <button
        type="submit"
        :disabled="loading"
        class="w-full bg-emerald-600 text-white py-2 px-4 rounded-lg hover:bg-emerald-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
      >
        <span v-if="loading" class="flex items-center justify-center">
          <svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          Signing in...
        </span>
        <span v-else>Sign In</span>
      </button>
    </form>

    <form v-else @submit.prevent="handle2faVerify" class="space-y-4">
      <div>
        <label for="code" class="block text-sm font-medium text-gray-700 mb-1">Verification Code</label>
        <input
          id="code"
          v-model="code"
          inputmode="numeric"
          maxlength="6"
          autocomplete="one-time-code"
          required
          class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
          placeholder="123456"
        />
      </div>

      <button
        type="submit"
        :disabled="verifyLoading"
        class="w-full bg-emerald-600 text-white py-2 px-4 rounded-lg hover:bg-emerald-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
      >
        <span v-if="verifyLoading" class="flex items-center justify-center">
          <svg class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          Verifying...
        </span>
        <span v-else>Verify &amp; Sign In</span>
      </button>

      <button
        type="button"
        @click="awaiting2fa = false; pendingToken = ''; code = ''; error = ''; password = ''"
        class="w-full text-center text-sm text-gray-500 hover:text-gray-700"
      >
        Back
      </button>
    </form>

    <div v-if="!awaiting2fa && ssoEnabled" class="mt-6">
      <div class="relative">
        <div class="absolute inset-0 flex items-center">
          <div class="w-full border-t border-gray-300"></div>
        </div>
        <div class="relative flex justify-center text-sm">
          <span class="px-2 bg-white text-gray-500">Or continue with</span>
        </div>
      </div>

      <div class="mt-4">
        <button
          type="button"
          @click="handleGoogleLogin"
          :disabled="ssoLoading"
          class="w-full flex items-center justify-center px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50"
        >
          <i class="fab fa-google text-lg"></i>
          <span class="ml-2 text-sm">{{ ssoLoading ? 'Redirecting to Google…' : 'Google' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
