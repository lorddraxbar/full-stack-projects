<script setup lang="ts">
import { ref, watch } from 'vue'
import { useTrashMessage } from '@/services/api'
import { useRetention } from '@/composables/useRetention'

/**
 * Provider-only "Remove message" dialog (the supported erasure path, V33).
 * A required reason lands in the audit trail — this surface exists for legal/
 * erasure requests, not convenience edits, and it must be able to explain
 * itself later. The message leaves the thread for everyone (client included),
 * its bell rows die with it, and it auto-purges after the retention window.
 *
 * v-model:open controls visibility; `msg` carries { id, preview }.
 * Emits 'removed' so the parent can reload its thread.
 */
const props = defineProps<{
  open: boolean
  msg: { id: number; preview: string } | null
}>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'removed', msg: { id: number; preview: string }): void
}>()

const { retentionDays } = useRetention()
const reason = ref('')
const busy = ref(false)
const error = ref('')

watch(() => props.open, (v) => {
  if (v) { reason.value = ''; error.value = '' }
})

async function submit() {
  if (!props.msg || busy.value) return
  if (!reason.value.trim()) { error.value = 'A reason is required — it is recorded in the audit trail.'; return }
  busy.value = true
  error.value = ''
  try {
    await useTrashMessage(props.msg.id, reason.value.trim())
    emit('update:open', false)
    emit('removed', props.msg)
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Failed to remove the message'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div v-if="open && msg" class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <div class="absolute inset-0 bg-black/30" @click="emit('update:open', false)" />
    <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-1">Remove message?</h2>
      <p class="text-sm text-gray-500 mb-3 italic">"{{ msg.preview }}"</p>
      <p class="text-sm text-gray-600 mb-2">
        The message disappears from the conversation <span class="font-medium text-gray-800">for everyone</span>,
        including the client, and its notification rows are deleted.
      </p>
      <p class="text-sm text-gray-600 mb-4">
        It stays in the staff message trash, <span class="font-medium text-gray-800">restorable for
        {{ retentionDays }} days</span>, then purges automatically. Every step is audited.
      </p>
      <label class="block text-sm font-medium text-gray-700 mb-1">Reason <span class="text-red-500">*</span></label>
      <textarea
        v-model="reason"
        rows="3"
        placeholder="e.g. Contains personal data — erasure request from the client's DPO"
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
          class="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors text-sm font-medium disabled:opacity-50"
          :disabled="busy"
          @click="submit"
        >
          {{ busy ? 'Removing…' : 'Remove Message' }}
        </button>
      </div>
    </div>
  </div>
</template>
