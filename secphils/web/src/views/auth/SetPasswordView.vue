<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSetPassword } from '../../services/api'

const route = useRoute()
const router = useRouter()

const token = (route.query.token as string) || ''
const password = ref('')
const confirm = ref('')
const showPassword = ref(false)
const error = ref('')
const loading = ref(false)
const done = ref(false)
const doneMessage = ref('')

const missingToken = computed(() => !token && !done.value)

const handleSetPassword = async () => {
  if (password.value.length < 8) {
    error.value = 'Password must be at least 8 characters'
    return
  }
  if (password.value !== confirm.value) {
    error.value = 'Passwords do not match'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await useSetPassword(token, password.value)
    doneMessage.value = data.message || 'Password set. You can now sign in.'
    done.value = true
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Failed to set password. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <div v-if="missingToken" class="text-center">
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Invalid link</h2>
      <p class="text-sm text-gray-600 mb-6">
        This password link is missing or malformed. Please ask your admin to resend the invite.
      </p>
      <button
        @click="router.push('/auth/login')"
        class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium text-sm"
      >
        Go to Sign In
      </button>
    </div>

    <div v-else-if="done" class="text-center">
      <div class="mx-auto mb-4 w-12 h-12 rounded-full bg-green-100 flex items-center justify-center">
        <i class="fas fa-check text-green-600 text-xl" />
      </div>
      <h2 class="text-xl font-semibold text-gray-900 mb-2">You're all set!</h2>
      <p class="text-sm text-gray-600 mb-6">{{ doneMessage }}</p>
      <button
        @click="router.push('/auth/login')"
        class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium text-sm"
      >
        Sign In
      </button>
    </div>

    <div v-else>
      <h2 class="text-xl font-semibold text-gray-900 mb-2">Set your password</h2>
      <p class="text-sm text-gray-600 mb-6">
        Choose a password for your SECPhils Portal account. It must be at least 8 characters.
      </p>

      <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
        {{ error }}
      </div>

      <form @submit.prevent="handleSetPassword" class="space-y-4">
        <div>
          <label for="new-password" class="block text-sm font-medium text-gray-700 mb-1">New Password</label>
          <div class="relative">
            <input
              id="new-password"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              required
              class="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
              placeholder="Min. 8 characters"
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

        <div>
          <label for="confirm-password" class="block text-sm font-medium text-gray-700 mb-1">Confirm Password</label>
          <input
            id="confirm-password"
            v-model="confirm"
            :type="showPassword ? 'text' : 'password'"
            required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
            placeholder="Repeat your password"
          />
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
            Setting password...
          </span>
          <span v-else>Set Password</span>
        </button>
      </form>
    </div>
  </div>
</template>
