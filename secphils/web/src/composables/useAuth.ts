import { ref } from 'vue'
import { useAuthStore } from '../stores/auth'

export function useAuth() {
  const authStore = useAuthStore()
  const loading = ref(false)
  const error = ref('')

  async function login(email: string, _password: string) {
    loading.value = true
    error.value = ''
    try {
      const response = {
        token: 'dummy-token',
        user: {
          id: 1,
          name: 'John Doe',
          email: email,
          role: 'CLIENT',
        },
      }
      authStore.login(response)
      return true
    } catch (err: unknown) {
      error.value = err instanceof Error ? err.message : 'Login failed'
      return false
    } finally {
      loading.value = false
    }
  }

  function logout() {
    authStore.logout()
  }

  return { login, logout, loading, error, isAuthenticated: authStore.isAuthenticated, user: authStore.user }
}
