import { ref, computed } from 'vue'

export type UserRole = 'CLIENT' | 'USER' | 'ADMIN'

const ROLE_KEY = 'userRole'

// Shared reactive role — all components reading this see the same instance.
const currentRole = ref<UserRole>((localStorage.getItem(ROLE_KEY) as UserRole) || 'CLIENT')

export function useRole() {
  const role = computed(() => currentRole.value)
  const isClient = computed(() => role.value === 'CLIENT')
  const isUser = computed(() => role.value === 'USER')
  const isAdmin = computed(() => role.value === 'ADMIN')

  return { role, isClient, isUser, isAdmin }
}
