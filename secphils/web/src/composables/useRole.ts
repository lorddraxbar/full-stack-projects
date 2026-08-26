import { ref, computed } from 'vue'

export type UserRole = 'CLIENT' | 'USER' | 'ADMIN'

const ROLE_KEY = 'userRole'
const VALID: UserRole[] = ['CLIENT', 'USER', 'ADMIN']

function readRole(): UserRole {
  const raw = localStorage.getItem(ROLE_KEY) as UserRole | null
  return raw && VALID.includes(raw) ? raw : 'CLIENT'
}

// Shared reactive role — all components reading this see the same instance.
const currentRole = ref<UserRole>(readRole())

// The ONLY way to change the role: writes localStorage and re-syncs the
// shared ref, so SPA login/logout never leaves a stale role in memory.
function setRole(role: UserRole | null) {
  if (role && VALID.includes(role)) {
    localStorage.setItem(ROLE_KEY, role)
  } else {
    localStorage.removeItem(ROLE_KEY)
  }
  currentRole.value = readRole()
}

export function useRole() {
  const role = computed(() => currentRole.value)
  const isClient = computed(() => role.value === 'CLIENT')
  const isUser = computed(() => role.value === 'USER')
  const isAdmin = computed(() => role.value === 'ADMIN')
  return { role, isClient, isUser, isAdmin, setRole }
}
