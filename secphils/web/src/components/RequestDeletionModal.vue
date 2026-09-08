<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRequestDocumentDeletion } from '@/services/api'

/**
 * Client-facing "Request deletion" dialog (Documents page + a project's
 * Documents tab — every client document surface asks identically, same
 * shared-modal precedent as DocumentPreviewModal).
 *
 * The client CANNOT delete files — that's a deliberate provider-editorial
 * policy. This modal is the convenience path: it posts a request message to
 * the project's thread and notifies SECPhils (bell + email). Honest copy:
 * the file stays live until a person trashes it; there is no auto-delete.
 *
 * v-model:open controls visibility; `doc` carries { id, title }.
 * Emits 'requested' on success so the parent can flash its own notice.
 */
const props = defineProps<{
  open: boolean
  doc: { id: number; title: string } | null
}>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'requested', doc: { id: number; title: string }): void
}>()

const note = ref('')
const busy = ref(false)
const error = ref('')

watch(() => props.open, (v) => {
  if (v) { note.value = ''; error.value = '' }
})

async function submit() {
  if (!props.doc || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await useRequestDocumentDeletion(props.doc.id, note.value)
    emit('update:open', false)
    emit('requested', props.doc)
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Failed to send the request'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div v-if="open && doc" class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <div class="absolute inset-0 bg-black/30" @click="emit('update:open', false)" />
    <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-1">Request deletion</h2>
      <p class="text-sm text-gray-600 mb-4">
        Ask SECPhils to remove
        <span class="font-medium text-gray-800">"{{ doc.title }}"</span>.
        This sends a request to your project team —
        <span class="font-medium text-gray-800">the file stays available</span>
        until SECPhils removes it.
      </p>
      <label class="block text-sm font-medium text-gray-700 mb-1">
        Reason <span class="text-gray-400 font-normal">(optional)</span>
      </label>
      <textarea
        v-model="note"
        rows="3"
        placeholder="e.g. Wrong version uploaded — contains confidential figures"
        class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 resize-none"
      />
      <p v-if="error" class="mt-2 text-sm text-red-600">{{ error }}</p>
      <div class="flex justify-end gap-3 mt-5">
        <button
          class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors text-sm font-medium"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
          :disabled="busy"
          @click="submit"
        >
          {{ busy ? 'Sending…' : 'Send Request' }}
        </button>
      </div>
    </div>
  </div>
</template>
