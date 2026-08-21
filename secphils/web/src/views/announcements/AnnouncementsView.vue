<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRole } from '@/composables/useRole'
import {
  useGetAnnouncements, useCreateAnnouncement, useDeleteAnnouncement, useGetProjects,
} from '@/services/api'
import {
  ANNOUNCEMENT_CATEGORY_LABELS, ANNOUNCEMENT_CATEGORY_COLORS,
  ANNOUNCEMENT_AUDIENCE_LABELS,
} from '@/lib/labels'

const { isClient } = useRole()
const isUser = computed(() => !isClient.value)
const isCustomer = computed(() => isClient.value)

interface Announcement {
  id: number
  title: string
  body: string
  category: string
  audience: string
  projectId: number | null
  isPublished: boolean
  createdByName: string
  createdAt: string
}

const announcements = ref<Announcement[]>([])
const loading = ref(true)
const error = ref('')
const readIds = ref<Set<number>>(new Set())

const projects = ref<{ id: number; name: string }[]>([])

const projectById = (id: number | null) =>
  projects.value.find(p => p.id === id)

const visibleAnnouncements = computed(() => {
  if (!isCustomer.value) return announcements.value
  // Customers see company-wide announcements plus project announcements
  // for projects they belong to (backend already scopes projects by role,
  // so any project id present here is one they can see).
  const myProjectIds = new Set(projects.value.map(p => p.id))
  return announcements.value.filter(a =>
    a.audience === 'COMPANY' || (a.audience === 'PROJECT' && a.projectId && myProjectIds.has(a.projectId)),
  )
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [annRes, projRes] = await Promise.all([useGetAnnouncements(), useGetProjects()])
    announcements.value = ((annRes as Announcement[]) || []).filter(a => a.isPublished !== false)
    const projList = Array.isArray(projRes) ? projRes : ((projRes as any)?.content ?? [])
    projects.value = (projList as { id: number; name: string }[]).map(p => ({ id: p.id, name: p.name }))
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    error.value = err.response?.data?.message || err.message || 'Failed to load announcements'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// --- Create form (user/admin only) ---
const showForm = ref(false)
const form = ref({
  title: '',
  body: '',
  category: 'PROJECT_UPDATE',
  audience: 'COMPANY',
  projectId: null as number | null,
})
const saving = ref(false)
const saveError = ref('')

function openForm() {
  form.value = { title: '', body: '', category: 'PROJECT_UPDATE', audience: 'COMPANY', projectId: null }
  saveError.value = ''
  showForm.value = true
}

async function submit() {
  if (!form.value.title.trim() || !form.value.body.trim()) {
    saveError.value = 'Title and body are required.'
    return
  }
  if (form.value.audience === 'PROJECT' && !form.value.projectId) {
    saveError.value = 'Select a project for project-scoped announcements.'
    return
  }
  saving.value = true
  saveError.value = ''
  try {
    await useCreateAnnouncement({
      title: form.value.title.trim(),
      body: form.value.body.trim(),
      category: form.value.category,
      audience: form.value.audience,
      projectId: form.value.audience === 'PROJECT' ? form.value.projectId : null,
    })
    showForm.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    saveError.value = err.response?.data?.message || err.message || 'Failed to create announcement'
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  if (!confirm('Delete this announcement?')) return
  try {
    await useDeleteAnnouncement(id)
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    alert(err.response?.data?.message || err.message || 'Failed to delete announcement')
  }
}

function markAsRead(id: number) {
  readIds.value.add(id)
  readIds.value = new Set(readIds.value)
}

function categoryLabel(c: string) {
  return ANNOUNCEMENT_CATEGORY_LABELS[c] || c.replace('_', ' ')
}

function categoryColor(c: string) {
  return ANNOUNCEMENT_CATEGORY_COLORS[c] || 'bg-gray-100 text-gray-800'
}

function formatDate(d: string) {
  return d ? new Date(d).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }) : '—'
}
</script>

<template>
  <div>
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Announcements</h1>
        <p class="text-gray-600 mt-1">Project and company-wide announcements</p>
      </div>
      <button
        v-if="isUser"
        @click="openForm"
        class="px-4 py-2 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700"
      >
        + New Announcement
      </button>
    </div>

    <!-- Create form -->
    <div v-if="showForm" class="bg-white rounded-lg shadow p-6 mb-6">
      <h3 class="font-semibold text-gray-900 mb-4">New Announcement</h3>
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Title</label>
          <input
            v-model="form.title"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            placeholder="Announcement title"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Body</label>
          <textarea
            v-model="form.body"
            rows="3"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            placeholder="Announcement details"
          />
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <select
              v-model="form.category"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option value="PROJECT_UPDATE">Project Update</option>
              <option value="COMPANY_NEWS">Company News</option>
              <option value="MAINTENANCE">Maintenance</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Audience</label>
            <select
              v-model="form.audience"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option value="COMPANY">Company-wide</option>
              <option value="PROJECT">Project</option>
            </select>
          </div>
          <div v-if="form.audience === 'PROJECT'">
            <label class="block text-sm font-medium text-gray-700 mb-1">Project</label>
            <select
              v-model="form.projectId"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option :value="null" disabled>Select project…</option>
              <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </div>
        </div>
        <p v-if="saveError" class="text-sm text-red-600">{{ saveError }}</p>
        <div class="flex gap-3">
          <button
            @click="submit"
            :disabled="saving"
            class="px-4 py-2 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700 disabled:opacity-50"
          >
            {{ saving ? 'Publishing…' : 'Publish' }}
          </button>
          <button
            @click="showForm = false"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>

    <p v-if="error" class="mb-4 text-sm text-red-600">{{ error }}</p>

    <!-- Announcements List -->
    <div class="space-y-6">
      <div
        v-for="announcement in visibleAnnouncements"
        :key="announcement.id"
        :class="[
          'bg-white rounded-lg shadow p-6 transition-all',
          !readIds.has(announcement.id) ? 'border-l-4 border-emerald-600' : ''
        ]"
      >
        <div class="flex items-start justify-between mb-3">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-2 flex-wrap">
              <h3 class="font-semibold text-gray-900">{{ announcement.title }}</h3>
              <span :class="['px-2 py-0.5 text-xs font-medium rounded-full', categoryColor(announcement.category)]">
                {{ categoryLabel(announcement.category) }}
              </span>
              <span
                :class="['px-2 py-0.5 text-xs font-medium rounded-full',
                  announcement.audience === 'COMPANY' ? 'bg-purple-100 text-purple-800' : 'bg-emerald-100 text-emerald-800']"
              >
                {{ ANNOUNCEMENT_AUDIENCE_LABELS[announcement.audience] || announcement.audience }}
              </span>
              <span v-if="!readIds.has(announcement.id)" class="w-2 h-2 bg-emerald-600 rounded-full" />
            </div>
            <p class="text-gray-700 text-sm whitespace-pre-line">{{ announcement.body }}</p>
          </div>
        </div>

        <div class="flex items-center justify-between text-sm text-gray-500 pt-3 border-t border-gray-100">
          <div class="flex items-center gap-4 flex-wrap">
            <span>By: {{ announcement.createdByName || '—' }}</span>
            <span>Published: {{ formatDate(announcement.createdAt) }}</span>
            <span v-if="announcement.audience === 'PROJECT' && announcement.projectId" class="text-emerald-600">
              Project: {{ projectById(announcement.projectId)?.name || 'N/A' }}
            </span>
          </div>
          <div class="flex items-center gap-3">
            <button
              v-if="!readIds.has(announcement.id)"
              @click="markAsRead(announcement.id)"
              class="px-3 py-1 text-sm text-emerald-600 hover:text-emerald-700 font-medium"
            >
              Mark as read
            </button>
            <button
              v-if="isUser"
              @click="remove(announcement.id)"
              class="px-3 py-1 text-sm text-red-600 hover:text-red-700 font-medium"
            >
              Delete
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading && visibleAnnouncements.length === 0" class="bg-white rounded-lg shadow p-12 text-center">
      <p class="text-gray-600">No announcements available.</p>
    </div>
    <div v-if="loading" class="bg-white rounded-lg shadow p-12 text-center">
      <p class="text-gray-500">Loading announcements…</p>
    </div>
  </div>
</template>
