<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRole } from '@/composables/useRole'
import {
  useGetDocuments, useCreateDocument, useDeleteDocument, useGetProjects,
} from '@/services/api'
import {
  documentCategoryLabel, DOCUMENT_CATEGORY_LABELS, DOCUMENT_CATEGORY_COLORS,
  formatDate, formatFileSize,
} from '@/lib/labels'

const { isClient } = useRole()

interface DocRow {
  id: number
  title: string
  description: string
  projectId: number
  project: string
  category: string
  categoryLabel: string
  fileUrl: string
  fileSize: number | null
  version: number | null
  uploaderName: string
  uploadedAt: string
}

const documents = ref<DocRow[]>([])
const projects = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const loadError = ref('')
const searchQuery = ref('')
const selectedProject = ref('ALL')
const selectedCategory = ref('ALL')

const showUploadModal = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const uploadForm = ref({
  title: '',
  projectId: null as number | null,
  category: 'DELIVERABLE',
  description: '',
  fileUrl: '',
})

const projectById = computed(() => {
  const map: Record<number, string> = {}
  for (const p of projects.value) map[p.id] = p.name
  return map
})

const filteredDocuments = computed(() => {
  let result = documents.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(d =>
      d.title.toLowerCase().includes(q) ||
      d.project.toLowerCase().includes(q) ||
      (d.description || '').toLowerCase().includes(q)
    )
  }
  if (selectedProject.value !== 'ALL') {
    result = result.filter(d => d.projectId === Number(selectedProject.value))
  }
  if (selectedCategory.value !== 'ALL') {
    result = result.filter(d => d.category === selectedCategory.value)
  }
  return result
})

function mapDoc(d: any): DocRow {
  return {
    id: d.id,
    title: d.title,
    description: d.description || '',
    projectId: d.projectId,
    project: projectById.value[d.projectId] || `Project #${d.projectId}`,
    category: d.category || '',
    categoryLabel: documentCategoryLabel(d.category),
    fileUrl: d.fileUrl || '',
    fileSize: d.fileSize ?? null,
    version: d.version ?? null,
    uploaderName: d.uploaderName || '—',
    uploadedAt: d.uploadedAt || '',
  }
}

async function loadProjects() {
  try {
    const data = await useGetProjects()
    const content = Array.isArray(data) ? data : data?.content ?? []
    projects.value = content.map((p: any) => ({ id: p.id, name: p.name }))
  } catch {
    // non-fatal: filters just show raw ids
  }
}

async function loadDocuments() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await useGetDocuments()
    documents.value = (Array.isArray(data) ? data : []).map(mapDoc)
  } catch (e: any) {
    loadError.value = e?.response?.data?.message || 'Failed to load documents'
  } finally {
    loading.value = false
  }
}

async function submitUpload() {
  if (!uploadForm.value.title.trim()) { uploadError.value = 'Title is required'; return }
  if (!uploadForm.value.projectId) { uploadError.value = 'Project is required'; return }
  uploading.value = true
  uploadError.value = ''
  try {
    await useCreateDocument({
      title: uploadForm.value.title.trim(),
      projectId: uploadForm.value.projectId,
      category: uploadForm.value.category,
      description: uploadForm.value.description.trim() || undefined,
      fileUrl: uploadForm.value.fileUrl.trim() || undefined,
    })
    showUploadModal.value = false
    uploadForm.value = { title: '', projectId: null, category: 'DELIVERABLE', description: '', fileUrl: '' }
    await loadDocuments()
  } catch (e: any) {
    uploadError.value = e?.response?.data?.message || 'Failed to add document'
  } finally {
    uploading.value = false
  }
}

async function removeDocument(doc: DocRow) {
  if (!confirm(`Delete "${doc.title}"? This cannot be undone.`)) return
  try {
    await useDeleteDocument(doc.id)
    await loadDocuments()
  } catch (e: any) {
    alert(e?.response?.data?.message || 'Failed to delete document')
  }
}

onMounted(async () => {
  await loadProjects()
  await loadDocuments()
})
</script>

<template>
  <div>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Documents</h1>
        <p class="text-gray-600 mt-1">View, upload, and manage project documents</p>
      </div>
      <button
        class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
        @click="showUploadModal = true"
      >
        + Upload Document
      </button>
    </div>

    <!-- Filters -->
    <div class="bg-white rounded-lg shadow p-4 mb-6">
      <div class="flex flex-col sm:flex-row gap-4">
        <div class="flex-1">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search documents..."
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <select
          v-model="selectedProject"
          class="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="ALL">All Projects</option>
          <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
        <select
          v-model="selectedCategory"
          class="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="ALL">All Categories</option>
          <option v-for="(label, code) in DOCUMENT_CATEGORY_LABELS" :key="code" :value="code">{{ label }}</option>
        </select>
      </div>
    </div>

    <!-- Error / loading -->
    <div v-if="loadError" class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-6 text-sm">
      {{ loadError }}
    </div>
    <div v-if="loading" class="bg-white rounded-lg shadow p-12 text-center text-gray-500">
      Loading documents...
    </div>

    <!-- Documents List -->
    <div v-else class="bg-white rounded-lg shadow overflow-hidden">
      <div class="divide-y divide-gray-200">
        <div
          v-for="doc in filteredDocuments"
          :key="doc.id"
          class="p-6 hover:bg-gray-50 transition-colors"
        >
          <div class="flex items-start justify-between mb-3 gap-4">
            <div class="flex items-start gap-4 min-w-0">
              <div class="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center flex-shrink-0">
                <i class="fas fa-file-lines text-blue-600"></i>
              </div>
              <div class="min-w-0">
                <h3 class="font-medium text-gray-900 truncate">{{ doc.title }}</h3>
                <p class="text-sm text-gray-600 truncate">{{ doc.project }}</p>
              </div>
            </div>
            <span class="text-sm text-gray-500 flex-shrink-0">{{ formatFileSize(doc.fileSize) }}</span>
          </div>

          <div v-if="doc.description" class="text-sm text-gray-600 mb-2">{{ doc.description }}</div>

          <div class="flex flex-wrap items-center justify-between gap-2 text-sm">
            <div class="flex flex-wrap items-center gap-3">
              <span :class="['px-2 py-0.5 text-xs font-medium rounded-full', DOCUMENT_CATEGORY_COLORS[doc.categoryLabel] || 'bg-gray-100 text-gray-700']">
                {{ doc.categoryLabel }}
              </span>
              <span class="text-gray-600">By: {{ doc.uploaderName }}</span>
              <span v-if="doc.version" class="text-gray-600">v{{ doc.version }}</span>
            </div>
            <div class="flex items-center gap-3">
              <span class="text-gray-500">{{ formatDate(doc.uploadedAt) }}</span>
              <a
                v-if="doc.fileUrl"
                :href="doc.fileUrl"
                target="_blank"
                rel="noopener"
                class="text-blue-600 hover:text-blue-700 font-medium"
              >
                Open
              </a>
              <button
                v-if="!isClient"
                class="text-red-600 hover:text-red-700 font-medium"
                @click="removeDocument(doc)"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="filteredDocuments.length === 0" class="p-12 text-center">
        <p class="text-gray-600">
          {{ documents.length === 0 ? 'No documents yet. Upload the first one.' : 'No documents found matching your criteria.' }}
        </p>
      </div>
    </div>

    <!-- Upload Modal -->
    <div v-if="showUploadModal" class="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div class="absolute inset-0 bg-black/30" @click="showUploadModal = false" />
      <div class="relative bg-white rounded-lg shadow-xl w-full max-w-lg p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Upload Document</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Title *</label>
            <input
              v-model="uploadForm.title"
              type="text"
              placeholder="e.g. Bottleneck Analysis — Line 3"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Project *</label>
            <select
              v-model="uploadForm.projectId"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option :value="null" disabled>Select a project...</option>
              <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <select
              v-model="uploadForm.category"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option v-for="(label, code) in DOCUMENT_CATEGORY_LABELS" :key="code" :value="code">{{ label }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              v-model="uploadForm.description"
              rows="2"
              placeholder="Optional notes about this document..."
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">File URL</label>
            <input
              v-model="uploadForm.fileUrl"
              type="url"
              placeholder="https://... (optional — link to the hosted file)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
            />
          </div>
          <p class="text-xs text-gray-500">
            File storage is not yet wired to object storage; the file URL is stored as a reference.
          </p>
        </div>
        <p v-if="uploadError" class="text-sm text-red-600 mt-3">{{ uploadError }}</p>
        <div class="mt-6 flex justify-end gap-3">
          <button
            class="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors text-sm font-medium"
            @click="showUploadModal = false"
          >
            Cancel
          </button>
          <button
            class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium disabled:opacity-50"
            :disabled="uploading"
            @click="submitUpload"
          >
            {{ uploading ? 'Saving...' : 'Save Document' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
