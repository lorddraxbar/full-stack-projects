import { ref, computed } from 'vue'

export type UserRole = 'CLIENT' | 'PROVIDER' | 'ADMIN'

const ROLE_KEY = 'userRole'

// Shared reactive role — all components reading this see the same instance,
// so the preview role switcher updates the whole app instantly.
const currentRole = ref<UserRole>((localStorage.getItem(ROLE_KEY) as UserRole) || 'CLIENT')

export function useRole() {
  const role = computed(() => currentRole.value)
  const isClient = computed(() => role.value === 'CLIENT')
  const isProvider = computed(() => role.value === 'PROVIDER')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isPreview = computed(() => localStorage.getItem('previewMode') === 'true')

  function setRole(newRole: UserRole) {
    currentRole.value = newRole
    localStorage.setItem(ROLE_KEY, newRole)
  }

  return { role, isClient, isProvider, isAdmin, isPreview, setRole }
}
