import { ref, computed } from 'vue'

/**
 * Reactive holder for the signed-in user's display name.
 *
 * The name used to be read straight from localStorage inside a computed
 * (MainLayout's user chip) — computeds cache, localStorage doesn't trigger
 * anything, so renaming yourself on Settings kept showing the OLD name in
 * the header until a full reload. This ref is the single reactive source:
 * every write site calls setName() (which still persists to localStorage
 * for the non-reactive readers: the api refresh interceptor and logout),
 * and reactive readers — the header chip, avatar initial — read the ref.
 *
 * Module-level on purpose: one identity per app instance, no Pinia dance
 * for a two-field value.
 */
const name = ref(localStorage.getItem('userName') || 'User')

export function useUserName() {
  const userName = computed(() => name.value)
  const userInitial = computed(() => name.value.charAt(0))

  function setName(value: string) {
    name.value = value || 'User'
    localStorage.setItem('userName', name.value)
  }

  /** Logout / session-loss path: reset the ref AND drop the key. */
  function clearName() {
    name.value = 'User'
    localStorage.removeItem('userName')
  }

  return { userName, userInitial, setName, clearName }
}
