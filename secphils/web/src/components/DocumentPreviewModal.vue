<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useDocumentContentBlob, useMessageContentBlob } from '@/services/api'

/**
 * Inline preview (Documents view, project Documents tab, Messages inbox,
 * project Messages tab — every attachment surface renders files identically).
 * Fetches the authenticated content blob (document or message attachment)
 * and renders it by MIME type: images inline, PDFs in the browser viewer,
 * plain text readable. The API only inlines a safe non-executable allowlist —
 * anything else (SVG, Office, archives) arrives as octet-stream and lands on
 * the fallback card, which offers Download instead.
 *
 * v-model:open controls visibility; `doc` carries { kind, id, title,
 * fileName } where kind picks the endpoint ('document' | 'message').
 */
const props = defineProps<{
  open: boolean
  doc: { kind?: 'document' | 'message'; id: number; title: string; fileName?: string } | null
}>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()

const loading = ref(false)
const error = ref('')
const objectUrl = ref('')   // always created once the blob loads (preview + fallback download)
const kind = ref<'image' | 'pdf' | 'text' | 'fallback'>('fallback')
const textContent = ref('')

function revoke() {
  if (objectUrl.value) { URL.revokeObjectURL(objectUrl.value); objectUrl.value = '' }
}

async function load() {
  revoke()
  kind.value = 'fallback'
  textContent.value = ''
  error.value = ''
  if (!props.doc) return
  loading.value = true
  try {
    const blob = props.doc.kind === 'message'
      ? await useMessageContentBlob(props.doc.id)
      : await useDocumentContentBlob(props.doc.id)
    objectUrl.value = URL.createObjectURL(blob)
    const mime = (blob.type || '').toLowerCase()
    if (mime.startsWith('image/')) kind.value = 'image'
    else if (mime === 'application/pdf') kind.value = 'pdf'
    else if (mime.startsWith('text/')) { textContent.value = await blob.text(); kind.value = 'text' }
    else kind.value = 'fallback' // API refused inline rendering (allowlist)
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Failed to load the preview'
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (v) => { if (v) load(); else revoke() })
onBeforeUnmount(revoke)

function close() { emit('update:open', false) }

function downloadOriginal() {
  if (!props.doc || !objectUrl.value) return
  const a = document.createElement('a')
  a.href = objectUrl.value
  a.download = props.doc.fileName || props.doc.title || 'document'
  document.body.appendChild(a); a.click(); a.remove()
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <div class="absolute inset-0 bg-black/50" @click="close" />
    <div class="relative bg-white rounded-lg shadow-xl w-full max-w-5xl max-h-[90vh] flex flex-col">
      <div class="flex items-center justify-between px-5 py-3 border-b border-gray-200 flex-shrink-0">
        <h3 class="font-semibold text-gray-900 truncate pr-4">{{ doc?.title || 'Document preview' }}</h3>
        <button class="text-gray-400 hover:text-gray-600 flex-shrink-0" aria-label="Close preview" @click="close">
          <i class="fas fa-xmark text-lg" />
        </button>
      </div>

      <div class="flex-1 overflow-auto bg-gray-100">
        <div v-if="loading" class="h-full min-h-[40vh] flex items-center justify-center text-gray-500">
          <i class="fas fa-spinner fa-spin mr-2" /> Loading preview…
        </div>
        <div v-else-if="error" class="h-full min-h-[40vh] flex items-center justify-center text-red-600 text-sm px-6 text-center">
          {{ error }}
        </div>
        <img v-else-if="kind === 'image'" :src="objectUrl" :alt="doc?.title || 'preview'" class="max-w-full mx-auto my-6 rounded shadow" />
        <iframe v-else-if="kind === 'pdf'" :src="objectUrl" :title="doc?.title || 'PDF preview'" class="w-full h-[75vh] bg-white" />
        <pre v-else-if="kind === 'text'" class="p-6 text-sm text-gray-800 whitespace-pre-wrap font-mono">{{ textContent }}</pre>
        <div v-else class="min-h-[40vh] flex flex-col items-center justify-center text-center px-6 py-10">
          <i class="fas fa-file-circle-question text-4xl text-gray-300 mb-3" />
          <p class="text-gray-600 mb-1">This file type can't be previewed in the browser.</p>
          <p class="text-sm text-gray-400 mb-4">Use Download to open it in its native application.</p>
          <button
            class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium"
            @click="downloadOriginal"
          >
            <i class="fas fa-download mr-1" /> Download
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
