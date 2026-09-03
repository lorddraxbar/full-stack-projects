import { computed, onMounted, ref } from 'vue'
import { useGetLanding } from '@/services/api'

/**
 * The admin-configurable retention window (default 7 days): how long an
 * archived project, a deactivated user/service, and a trashed document stay
 * recoverable before hard deletion becomes passwordless — and the trash is
 * auto-purged.
 *
 * Sourced from the public landing payload rather than /admin/settings, so
 * staff (USER) roles — which get 403 on the admin settings endpoint — still
 * see the live value in the views that quote it.
 */
export function useRetention() {
  const days = ref(7)
  onMounted(async () => {
    try {
      const data = await useGetLanding()
      if (typeof data.retentionWindowDays === 'number' && data.retentionWindowDays > 0) {
        days.value = data.retentionWindowDays
      }
    } catch {
      // keep the default if the landing payload is unavailable
    }
  })
  return { retentionDays: computed(() => days.value) }
}
