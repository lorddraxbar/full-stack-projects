import { ref, onMounted, onUnmounted } from 'vue'

export function useWindowSize() {
  const width = ref(window.innerWidth)
  const height = ref(window.innerHeight)

  function updateSize() {
    width.value = window.innerWidth
    height.value = window.innerHeight
  }

  onMounted(() => window.addEventListener('resize', updateSize))
  onUnmounted(() => window.removeEventListener('resize', updateSize))

  return { width, height }
}

export function useLocalStorage<T>(key: string, initialValue: T) {
  const storedValue = ref<T>(
    JSON.parse(localStorage.getItem(key) || JSON.stringify(initialValue))
  )

  function setValue(value: T | ((val: T) => T)) {
    const valueToStore = value instanceof Function ? value(storedValue.value) : value
    storedValue.value = valueToStore
    localStorage.setItem(key, JSON.stringify(valueToStore))
  }

  return { storedValue, setValue }
}

export function useConfirm() {
  const showModal = ref(false)
  const message = ref('')
  const resolvePromise = ref<((value: boolean) => void) | null>(null)

  function confirm(prompt: string): Promise<boolean> {
    message.value = prompt
    showModal.value = true

    return new Promise(resolve => {
      resolvePromise.value = resolve
    })
  }

  function confirmResult(result: boolean) {
    showModal.value = false
    resolvePromise.value?.(result)
    resolvePromise.value = null
  }

  return { showModal, message, confirm, confirmResult }
}
