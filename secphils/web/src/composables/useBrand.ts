import { computed } from 'vue'

/**
 * The admin-configurable brand (default "SECPhils"): the collapsed
 * provider sender name on client-visible surfaces and the portal drawer
 * wordmark. Persisted to localStorage by LoginView / SSOCallbackView from
 * the `brand` field the backend echoes on every login path.
 */
export function useBrand() {
  const brand = computed(() => {
    const stored = localStorage.getItem('brandName')
    return stored && stored.trim() ? stored.trim() : 'SECPhils'
  })
  return { brand }
}

/**
 * The admin-configurable app title (default "SECPhils Portal"), used for the
 * browser tab and the login page heading. Same delivery channel as the brand.
 */
export function usePortalName() {
  const portalName = computed(() => {
    const stored = localStorage.getItem('portalName')
    return stored && stored.trim() ? stored.trim() : 'SECPhils Portal'
  })
  return { portalName }
}
