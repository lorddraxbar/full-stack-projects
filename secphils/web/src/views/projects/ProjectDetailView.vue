<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRole } from '@/composables/useRole'
import RowActionsMenu from '@/components/RowActionsMenu.vue'
import {
  useGetMe, useGetProject, useGetCompany, useGetProjectTeam,
  useGetDocuments, useCreateDocument, useDeleteDocument,
  useGetMessages, useSendMessage, useUpdateProject,
  useArchiveProject, useRestoreProject, useHardDeleteProject,
} from '@/services/api'
import {
  projectStatusLabel, documentCategoryLabel,
  PROJECT_STATUS_COLORS, DOCUMENT_CATEGORY_COLORS,
  formatDate, formatDateTime,
} from '@/lib/labels'

const { isClient, isAdmin } = useRole()
const isUser = computed(() => !isClient.value && !isAdmin.value)
const route = useRoute()
const projectId = computed(() => Number(route.params.id))

const me = ref<{ id: number; fullName: string; role: string } | null>(null)
const project = ref<any>(null)
const company = ref<any>(null)
const team = ref<{ userId: number; fullName: string; role: string }[]>([])
const documents = ref<any[]>([])
const messages = ref<any[]>([])
const loading = ref(true)
const loadError = ref('')
const saveError = ref('')

// ---------- Role-based tabs ----------
const tabs = computed(() => {
  const base = ['Overview', 'Documents', 'Messages']
  if (isClient.value) return [...base, 'Team']
  if (isUser.value) return ['Overview', 'Company', 'Documents', 'Messages']
  if (isAdmin.value) return ['Overview', 'Company', 'Documents', 'Messages', 'Admin Controls']
  return base
})
const activeTab = ref('Overview')

function initials(name: string): string {
  return name.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase()
}

function isMine(msg: any): boolean {
  return me.value != null && msg.senderId === me.value.id
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [meRes, projRes] = await Promise.all([useGetMe(), useGetProject(projectId.value)])
    // GET /users/me returns the UserResponse body directly (no envelope)
    me.value = meRes || null
    project.value = projRes
    // Initialize the admin form only once the project has loaded
    initAdminForm()

    const [teamRes, docsRes, msgsRes] = await Promise.all([
      useGetProjectTeam(projectId.value).catch(() => []),
      useGetDocuments({ projectId: projectId.value }).catch(() => []),
      useGetMessages(projectId.value).catch(() => []),
    ])
    team.value = (Array.isArray(teamRes) ? teamRes : []).map((m: any) => ({
      userId: m.userId, fullName: m.fullName, role: m.role,
    }))
    documents.value = Array.isArray(docsRes) ? docsRes : []
    messages.value = Array.isArray(msgsRes) ? msgsRes : []

    if (project.value?.companyId) {
      company.value = await useGetCompany(project.value.companyId).catch(() => null)
    }
  } catch (err: any) {
    loadError.value = err?.response?.data?.message || err?.message || 'Failed to load project'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------- Archive lifecycle (soft delete / restore / hard delete) ----------
const router = useRouter()
const lifecycleBusy = ref(false)
const archived = computed(() => project.value?.status === 'ARCHIVED')

async function archiveProject() {
  if (!confirm('Archive this project? It will be hidden and its files moved into the archive. You can restore it within the grace period.')) return
  lifecycleBusy.value = true
  saveError.value = ''
  try {
    await useArchiveProject(projectId.value)
    await load()
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to archive project'
  } finally {
    lifecycleBusy.value = false
  }
}

async function restoreProject() {
  if (!confirm('Restore this archived project? It will return to its previous status.')) return
  lifecycleBusy.value = true
  saveError.value = ''
  try {
    await useRestoreProject(projectId.value)
    await load()
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to restore project'
  } finally {
    lifecycleBusy.value = false
  }
}

async function hardDeleteProject() {
  if (!confirm('Permanently delete this project? All data and files will be permanently removed. This cannot be undone.')) return
  // Admins must supply their password when the 7-day grace window hasn't elapsed.
  const password = window.prompt('Enter your password to permanently delete this project:')
  if (password === null) return
  lifecycleBusy.value = true
  saveError.value = ''
  try {
    await useHardDeleteProject(projectId.value, password)
    router.push('/projects')
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to delete project'
    lifecycleBusy.value = false
  }
}

// ---------- Messages ----------
const messageDraft = ref('')
const sending = ref(false)
async function sendMessage() {
  if (!messageDraft.value.trim() || sending.value) return
  sending.value = true
  try {
    await useSendMessage(projectId.value, messageDraft.value.trim())
    messageDraft.value = ''
    messages.value = await useGetMessages(projectId.value)
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to send message'
  } finally {
    sending.value = false
  }
}

// ---------- Documents (add / delete) ----------
const docDialogOpen = ref(false)
const docSaving = ref(false)
const docForm = ref({ title: '', category: 'DELIVERABLE', description: '', fileUrl: '' })
const docCategories = ['CLIENT_SUBMITTED', 'REQUESTED', 'DELIVERABLE']

function openDocDialog() {
  docForm.value = { title: '', category: 'DELIVERABLE', description: '', fileUrl: '' }
  docDialogOpen.value = true
}

async function submitDocument() {
  if (!docForm.value.title.trim() || docSaving.value) return
  docSaving.value = true
  try {
    await useCreateDocument({
      projectId: projectId.value,
      title: docForm.value.title.trim(),
      category: docForm.value.category,
      description: docForm.value.description.trim() || null,
      fileUrl: docForm.value.fileUrl.trim() || null,
    })
    docDialogOpen.value = false
    documents.value = await useGetDocuments({ projectId: projectId.value })
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to add document'
  } finally {
    docSaving.value = false
  }
}

async function deleteDocument(id: number) {
  if (!confirm('Delete this document?')) return
  try {
    await useDeleteDocument(id)
    documents.value = await useGetDocuments({ projectId: projectId.value })
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to delete document'
  }
}

// ---------- Admin controls ----------
const adminForm = ref({
  status: '',
  scope: '',
  objectives: '',
  progress: 0,
})
const adminReady = ref(false)
const projectStatusCodes = ['NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'ARCHIVED']

function initAdminForm() {
  if (!project.value) return
  adminForm.value = {
    status: project.value.status || 'NOT_STARTED',
    scope: project.value.scope || '',
    objectives: project.value.objectives || '',
    progress: project.value.progress ?? 0,
  }
  adminReady.value = true
}
// (adminForm is initialized inside load() once the project is available)

async function saveAdminChanges() {
  if (!project.value) return
  saveError.value = ''
  try {
    const updated = await useUpdateProject(projectId.value, {
      companyId: project.value.companyId,
      serviceId: project.value.serviceId ?? null,
      name: project.value.name,
      scope: adminForm.value.scope,
      objectives: adminForm.value.objectives,
      deliverables: project.value.deliverables ?? null,
      status: adminForm.value.status,
      totalCost: project.value.totalCost ?? null,
      rawMaterials: project.value.rawMaterials ?? null,
      productionOutput: project.value.productionOutput ?? null,
      wasteManagement: project.value.wasteManagement ?? null,
      wasteMaterials: project.value.wasteMaterials ?? null,
      manufacturingProcedure: project.value.manufacturingProcedure ?? null,
      productionFlowchartUrl: project.value.productionFlowchartUrl ?? null,
      progress: adminForm.value.progress,
    })
    project.value = updated
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to save project'
  }
}
</script>

<template>
  <div>
    <div v-if="loading" class="flex items-center justify-center py-20">
      <svg class="animate-spin h-8 w-8 text-emerald-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
    </div>

    <div v-else-if="loadError" class="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
      {{ loadError }}
    </div>

    <div v-else-if="project">
      <!-- Header -->
      <div class="mb-6">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h1 class="text-2xl font-bold text-gray-900">{{ project.name }}</h1>
            <p class="text-gray-600 mt-1">
              {{ project.companyName || company?.name || '—' }} &middot; {{ project.serviceName || '—' }}
            </p>
          </div>
          <span :class="['px-3 py-1 text-sm font-medium rounded-full', PROJECT_STATUS_COLORS[projectStatusLabel(project.status)]]">
            {{ projectStatusLabel(project.status) }}
          </span>
        </div>

        <div class="flex items-center gap-6 text-sm text-gray-600" v-if="!isClient && project.totalCost != null">
          <i class="fas fa-coins mr-1"></i>Contract: ${{ Number(project.totalCost).toLocaleString() }}
        </div>

        <div class="mt-4 w-full bg-gray-200 rounded-full h-2">
          <div class="bg-emerald-600 h-2 rounded-full transition-all" :style="{ width: (project.progress ?? 0) + '%' }" />
        </div>
        <p class="text-sm text-gray-600 mt-1">{{ project.progress ?? 0 }}% complete</p>
      </div>

      <!-- Tabs -->
      <div class="border-b border-gray-200 mb-6">
        <nav class="-mb-px flex gap-8 overflow-x-auto">
          <button
            v-for="tab in tabs"
            :key="tab"
            @click="activeTab = tab"
            :class="[
              'py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors',
              activeTab === tab
                ? 'border-emerald-600 text-emerald-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300',
            ]"
          >
            {{ tab }}
          </button>
        </nav>
      </div>

      <!-- ================= OVERVIEW ================= -->
      <div v-if="activeTab === 'Overview'">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          <div class="bg-white rounded-lg shadow p-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Scope</h2>
            <p class="text-gray-700">{{ project.scope || '—' }}</p>
          </div>
          <div class="bg-white rounded-lg shadow p-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Objectives</h2>
            <p class="text-gray-700">{{ project.objectives || '—' }}</p>
          </div>
        </div>

        <!-- Add Update (user/admin only) -->
        <div v-if="isUser || isAdmin" class="bg-white rounded-lg shadow p-6 mb-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Add Update</h2>
          <textarea
            v-model="messageDraft"
            rows="3"
            placeholder="Post a progress update to the project conversation..."
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
          />
          <div class="mt-3 flex justify-end">
            <button
              @click="sendMessage"
              :disabled="sending"
              class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
            >
              Post Update
            </button>
          </div>
        </div>

        <!-- Update History -->
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">
            {{ isClient ? 'Recent Updates' : 'Update History' }}
          </h2>
          <div v-if="messages.length === 0" class="text-sm text-gray-500">No updates posted yet.</div>
          <div v-else class="relative">
            <div class="absolute left-3 top-1 bottom-1 w-px bg-gray-200" />
            <div
              v-for="msg in (isClient ? [...messages].reverse().slice(0, 3) : [...messages].reverse())"
              :key="msg.id"
              class="relative pl-10 pb-6 last:pb-0"
            >
              <span class="absolute left-1.5 top-1 w-3 h-3 rounded-full bg-emerald-500 ring-4 ring-emerald-100" />
              <p class="text-xs text-gray-500">{{ formatDateTime(msg.createdAt) }} &middot; {{ msg.senderName || '—' }}</p>
              <p class="text-sm text-gray-700 mt-1">{{ msg.body }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= COMPANY ================= -->
      <div v-if="activeTab === 'Company'" class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Client Company</h2>
        <div class="space-y-4">
          <div>
            <p class="text-sm text-gray-500">Company Name</p>
            <p class="text-gray-900 font-medium">{{ company?.name || project.companyName || '—' }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Location</p>
            <p class="text-gray-900">{{ company?.location || '—' }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Owner</p>
            <p class="text-gray-900">{{ company?.owner || '—' }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Authorized Representative</p>
            <p class="text-gray-900">{{ company?.authorizedRepName || '—' }}</p>
          </div>
        </div>
      </div>

      <!-- ================= TEAM (client) ================= -->
      <div v-if="activeTab === 'Team'" class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Your Project Team</h2>
        <div v-if="team.length === 0" class="text-sm text-gray-500">No team members assigned yet.</div>
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="member in team"
            :key="member.userId"
            class="flex items-center gap-4 p-4 border border-gray-200 rounded-lg"
          >
            <div class="w-12 h-12 rounded-full bg-emerald-600 flex items-center justify-center text-white font-medium">
              {{ initials(member.fullName) }}
            </div>
            <div>
              <h3 class="font-medium text-gray-900">{{ member.fullName }}</h3>
              <p class="text-sm text-gray-600">{{ member.role }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= DOCUMENTS ================= -->
      <div v-if="activeTab === 'Documents'" class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900">Project Documents</h2>
          <button
            v-if="!isClient"
            @click="openDocDialog"
            class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium"
          >
            <i class="fas fa-upload mr-1" /> Add Document
          </button>
        </div>
        <div v-if="documents.length === 0" class="p-6 text-sm text-gray-500">
          No documents for this project yet.
        </div>
        <div v-else class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Document Name</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Version</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Uploaded By</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th class="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="doc in documents" :key="doc.id" class="hover:bg-gray-50">
                <td class="px-6 py-4">
                  <div class="flex items-center gap-3">
                    <i class="fas fa-file-lines text-emerald-500 text-lg" />
                    <div>
                      <span class="font-medium text-gray-900 text-sm">{{ doc.title }}</span>
                      <p v-if="doc.description" class="text-xs text-gray-500">{{ doc.description }}</p>
                    </div>
                  </div>
                </td>
                <td class="px-6 py-4">
                  <span :class="['px-2 py-1 text-xs font-medium rounded-full', DOCUMENT_CATEGORY_COLORS[documentCategoryLabel(doc.category)]]">
                    {{ documentCategoryLabel(doc.category) }}
                  </span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">v{{ doc.version ?? 1 }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ doc.uploaderName || '—' }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ formatDate(doc.uploadedAt) }}</td>
                <td class="px-3 py-4 text-right whitespace-nowrap">
                  <RowActionsMenu v-if="!isClient" :actions="[
                    { label: 'Delete', color: 'text-red-600 hover:text-red-700 hover:bg-red-50', onClick: () => deleteDocument(doc.id) }
                  ]" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ================= MESSAGES ================= -->
      <div v-if="activeTab === 'Messages'" class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Project Conversation</h2>
          <p class="text-sm text-gray-600 mt-1">Shared thread for all project participants</p>
        </div>
        <div class="p-6 space-y-4">
          <div v-if="messages.length === 0" class="text-sm text-gray-500">No messages yet. Start the conversation below.</div>
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['flex', isMine(msg) ? 'justify-end' : 'justify-start']"
          >
            <div
              :class="[
                'max-w-md rounded-lg p-4',
                isMine(msg) ? 'bg-emerald-600 text-white' : 'bg-gray-100',
              ]"
            >
              <div class="flex items-center justify-between gap-4 mb-1">
                <p :class="['text-xs font-medium', isMine(msg) ? 'text-emerald-100' : 'text-gray-600']">
                  {{ msg.senderName || '—' }}
                </p>
                <p :class="['text-xs', isMine(msg) ? 'text-emerald-200' : 'text-gray-400']">
                  {{ formatDateTime(msg.createdAt) }}
                </p>
              </div>
              <p class="text-sm">{{ msg.body }}</p>
              <div
                v-if="msg.attachmentFileName"
                class="mt-2 pt-2 border-t text-xs"
                :class="isMine(msg) ? 'border-white/20 text-emerald-100' : 'border-gray-300 text-gray-600'"
              >
                <i class="fas fa-paperclip mr-1"></i>{{ msg.attachmentFileName }}
              </div>
            </div>
          </div>
        </div>
        <div class="p-6 border-t border-gray-200">
          <div class="flex gap-3">
            <textarea
              v-model="messageDraft"
              rows="2"
              placeholder="Type a message..."
              class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
            <button
              @click="sendMessage"
              :disabled="sending"
              class="self-end bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
            >
              Send
            </button>
          </div>
        </div>
      </div>

      <!-- ================= ADMIN CONTROLS ================= -->
      <div v-if="activeTab === 'Admin Controls'" class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-6">Project Configuration</h2>
        <p v-if="saveError" class="mb-4 text-sm text-red-600">{{ saveError }}</p>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Status</label>
            <select
              v-model="adminForm.status"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option v-for="s in projectStatusCodes" :key="s" :value="s">{{ projectStatusLabel(s) }}</option>
            </select>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Progress ({{ adminForm.progress }}%)</label>
            <input
              v-model.number="adminForm.progress"
              type="range"
              min="0"
              max="100"
              step="5"
              class="w-full accent-emerald-600"
            />
          </div>

          <div class="lg:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Scope</label>
            <textarea
              v-model="adminForm.scope"
              rows="4"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>

          <div class="lg:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Objectives</label>
            <textarea
              v-model="adminForm.objectives"
              rows="3"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>
        </div>

        <div class="mt-6 flex justify-end">
          <button
            @click="saveAdminChanges"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium"
          >
            Save Changes
          </button>
        </div>

        <!-- Lifecycle: archive / restore / hard delete -->
        <div class="mt-8 border-t border-gray-200 pt-6">
          <h3 class="text-base font-semibold text-gray-900">Lifecycle</h3>
          <p class="mt-1 text-sm text-gray-600">
            Archiving hides the project from the list and moves its files into the archive.
            While archived it can be restored within the retention window; after that it can only
            be permanently deleted.
          </p>

          <div v-if="archived" class="mt-4 rounded-lg bg-amber-50 border border-amber-200 p-4 text-sm">
            <p class="font-medium text-amber-800">This project is archived.</p>
            <p class="mt-1 text-amber-700">
              Archived {{ project.archivedAt ? formatDate(project.archivedAt) : '—' }}
              <span v-if="project.deleteAt">
                · retention ends {{ formatDate(project.deleteAt) }}
              </span>
            </p>
          </div>

          <div class="mt-4 flex flex-wrap items-center gap-3">
            <button
              v-if="!archived"
              @click="archiveProject"
              :disabled="lifecycleBusy"
              class="px-4 py-2 border border-amber-300 text-amber-800 bg-amber-50 rounded-lg hover:bg-amber-100 transition-colors text-sm font-medium disabled:opacity-50"
            >
              {{ lifecycleBusy ? 'Working…' : 'Archive Project' }}
            </button>
            <button
              v-else
              @click="restoreProject"
              :disabled="lifecycleBusy"
              class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
            >
              {{ lifecycleBusy ? 'Working…' : 'Restore Project' }}
            </button>
            <button
              @click="hardDeleteProject"
              :disabled="lifecycleBusy"
              class="px-4 py-2 border border-red-300 text-red-700 bg-red-50 rounded-lg hover:bg-red-100 transition-colors text-sm font-medium disabled:opacity-50"
            >
              {{ lifecycleBusy ? 'Working…' : 'Permanently Delete' }}
            </button>
            <p class="text-xs text-gray-500">
              Permanent deletion requires your password inside the retention window.
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= DOCUMENT DIALOG ================= -->
    <div
      v-if="docDialogOpen"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      @click.self="docDialogOpen = false"
    >
      <div class="bg-white rounded-lg shadow-xl w-full max-w-md">
        <div class="p-6 border-b border-gray-200">
          <h3 class="text-lg font-semibold text-gray-900">Add Document</h3>
        </div>
        <div class="p-6 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Document Title</label>
            <input
              v-model="docForm.title"
              type="text"
              placeholder="e.g. Bottleneck Analysis — Line 3"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <select
              v-model="docForm.category"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            >
              <option v-for="c in docCategories" :key="c" :value="c">{{ documentCategoryLabel(c) }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">File URL (optional)</label>
            <input
              v-model="docForm.fileUrl"
              type="url"
              placeholder="https://… (link to the hosted file)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              v-model="docForm.description"
              rows="3"
              placeholder="What does this document contain?"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>
        </div>
        <div class="p-6 border-t border-gray-200 flex justify-end gap-3">
          <button
            @click="docDialogOpen = false"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            @click="submitDocument"
            :disabled="docSaving"
            class="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-emerald-700 disabled:opacity-50"
          >
            {{ docSaving ? 'Saving…' : 'Add Document' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
