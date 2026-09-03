<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRole } from '@/composables/useRole'
import {
  useGetAnnouncements, useCreateAnnouncement, useUpdateAnnouncement, useDeleteAnnouncement, useGetProjects,
} from '@/services/api'
import {
  ANNOUNCEMENT_CATEGORY_LABELS, ANNOUNCEMENT_CATEGORY_COLORS,
  ANNOUNCEMENT_AUDIENCE_LABELS,
} from '@/lib/labels'

const { isClient } = useRole()
const isUser = computed(() => !isClient.value)

interface Announcement {
  id: number
  companyId: number | null
  title: string
  body: string
  category: string
  audience: string
  projectId: number | null
  projectName: string | null
  isPublished: boolean
  createdById: number | null
  createdByName: string
  createdAt: string
}

function categoryLabel(c: string) {
  return ANNOUNCEMENT_CATEGORY_LABELS[c] || (c ? c.replace('_', ' ') : 'General')
}

function formatDate(d: string) {
  return d ? new Date(d).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }) : '—'
}

const announcements = ref<Announcement[]>([])
const loading = ref(true)
const error = ref('')
const searchQuery = ref('')

const projects = ref<{ id: number; name: string }[]>([])

const projectById = (id: number | null) =>
  projects.value.find(p => p.id === id)

/**
 * Staff: the backend already returns drafts for their own company, so show
 * everything. Clients: the backend filters to published-only; the client-side
 * filter is a second line of defense (drafts must never leak to clients).
 *
 * Standard search: every space-separated term must appear somewhere in the
 * announcement's displayed fields (title, body, category, audience, project,
 * author, date). Same multi-term AND convention as Admin → Users.
 */
const visibleAnnouncements = computed(() => {
  const list = isUser.value
    ? announcements.value
    : announcements.value.filter(a => a.isPublished)
  let out = list
  if (!isCustomer.value) {
    const myProjectIds = new Set(projects.value.map(p => p.id))
    out = list.filter(a =>
      a.audience === 'COMPANY' || (a.audience === 'PROJECT' && a.projectId && myProjectIds.has(a.projectId)),
    )
  }
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    const terms = q.split(/\s+/)
    out = out.filter(a => {
      const projName = a.audience === 'PROJECT' && a.projectId
        ? (projectById(a.projectId)?.name || a.projectName || '')
        : ''
      const haystack = [
        a.title,
        a.body,
        categoryLabel(a.category),
        ANNOUNCEMENT_AUDIENCE_LABELS[a.audience] || a.audience,
        a.isPublished ? 'published' : 'draft',
        projName,
        a.createdByName || '',
        formatDate(a.createdAt),
      ].join(' ').toLowerCase()
      return terms.every(t => haystack.includes(t))
    })
  }
  return out
})

const isCustomer = computed(() => isClient.value)

async function load() {
  loading.value = true
  error.value = ''
  try {
    // Full list — a page-capped fetch would hide older projects from the filter.
    const [annRes, projRes] = await Promise.all([useGetAnnouncements(), useGetProjects({ size: 10000 })])
    announcements.value = (annRes as Announcement[]) || []
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

// --- Create / edit form (staff only) ---
const showForm = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  title: '',
  body: '',
  category: 'PROJECT_UPDATE',
  audience: 'COMPANY',
  projectId: null as number | null,
  isPublished: true,
})
const saving = ref(false)
const saveError = ref('')

function openForm() {
  form.value = { title: '', body: '', category: 'PROJECT_UPDATE', audience: 'COMPANY', projectId: null, isPublished: true }
  editingId.value = null
  saveError.value = ''
  showForm.value = true
}

function openEdit(a: Announcement) {
  form.value = {
    title: a.title,
    body: a.body,
    category: a.category || 'PROJECT_UPDATE',
    audience: a.audience || 'COMPANY',
    projectId: a.audience === 'PROJECT' ? a.projectId : null,
    isPublished: a.isPublished,
  }
  editingId.value = a.id
  saveError.value = ''
  showForm.value = true
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const currentUserId = computed(() => Number(localStorage.getItem('userId') || 0))

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
  const payload = {
    title: form.value.title.trim(),
    body: form.value.body.trim(),
    category: form.value.category,
    audience: form.value.audience,
    projectId: form.value.audience === 'PROJECT' ? form.value.projectId : null,
    isPublished: form.value.isPublished,
  }
  try {
    if (editingId.value == null) {
      await useCreateAnnouncement(payload)
    } else {
      await useUpdateAnnouncement(editingId.value, payload)
    }
    showForm.value = false
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    saveError.value = err.response?.data?.message || err.message || 'Failed to save announcement'
  } finally {
    saving.value = false
  }
}

/** Quick publish/unpublish toggle from a card — PUT with the flipped flag. */
async function togglePublish(a: Announcement) {
  try {
    await useUpdateAnnouncement(a.id, {
      title: a.title,
      body: a.body,
      category: a.category,
      audience: a.audience,
      projectId: a.audience === 'PROJECT' ? a.projectId : null,
      isPublished: !a.isPublished,
    })
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    alert(err.response?.data?.message || err.message || 'Failed to update announcement')
  }
}

async function remove(a: Announcement) {
  if (!confirm(`Delete "${a.title}"? This cannot be undone.`)) return
  try {
    await useDeleteAnnouncement(a.id)
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    alert(err.response?.data?.message || err.message || 'Failed to delete announcement')
  }
}

/** Staff may delete only their own announcements (admin: any) — mirrors the backend rule. */
const canDelete = (a: Announcement) =>
  isUser.value && (currentUserId.value > 0 && a.createdById === currentUserId.value)

function categoryColor(c: string) {
  return ANNOUNCEMENT_CATEGORY_COLORS[c] || 'bg-gray-100 text-gray-800'
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

    <!-- Search -->
    <div class="bg-white rounded-lg shadow p-4 mb-6">
      <div class="relative">
        <i class="fas fa-magnifying-glass absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search all announcements — title, body, category, audience, project, author, date"
          class="w-full pl-9 pr-9 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
        />
        <button
          v-if="searchQuery"
          @click="searchQuery = ''"
          class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
          aria-label="Clear search"
        >
          <i class="fas fa-xmark text-sm" />
        </button>
      </div>
    </div>

    <!-- Create / edit form -->
    <div v-if="showForm" class="bg-white rounded-lg shadow p-6 mb-6">
      <h3 class="font-semibold text-gray-900 mb-4">
        {{ editingId == null ? 'New Announcement' : 'Edit Announcement' }}
      </h3>
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
        <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
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
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Visibility</label>
            <select
              v-model="form.isPublished"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option :value="true">Published — notify company</option>
              <option :value="false">Draft — only staff see it</option>
            </select>
          </div>
        </div>
        <p
          v-if="form.isPublished"
          class="text-xs text-gray-500"
        >
          Publishing (or re-publishing) sends an in-app notification and email to every active
          member of the company who has announcement notifications enabled.
        </p>
        <p v-if="saveError" class="text-sm text-red-600">{{ saveError }}</p>
        <div class="flex gap-3">
          <button
            @click="submit"
            :disabled="saving"
            class="px-4 py-2 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700 disabled:opacity-50"
          >
            {{ saving ? 'Saving…' : (form.isPublished ? 'Publish' : editingId == null ? 'Save Draft' : 'Save Changes') }}
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
        :class="['bg-white rounded-lg shadow p-6 transition-all',
          !announcement.isPublished ? 'opacity-80 border border-dashed border-gray-300' : '']"
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
              <span
                v-if="isUser && !announcement.isPublished"
                class="px-2 py-0.5 text-xs font-medium rounded-full bg-amber-100 text-amber-800"
              >
                Draft
              </span>
            </div>
            <p class="text-gray-700 text-sm whitespace-pre-line">{{ announcement.body }}</p>
          </div>
        </div>

        <div class="flex items-center justify-between text-sm text-gray-500 pt-3 border-t border-gray-100">
          <div class="flex items-center gap-4 flex-wrap">
            <span>By: {{ announcement.createdByName || '—' }}</span>
            <span>{{ announcement.isPublished ? 'Published' : 'Drafted' }}: {{ formatDate(announcement.createdAt) }}</span>
            <span v-if="announcement.audience === 'PROJECT' && announcement.projectId" class="text-emerald-600">
              Project: {{ projectById(announcement.projectId)?.name || announcement.projectName || 'N/A' }}
            </span>
          </div>
          <div v-if="isUser" class="flex items-center gap-3">
            <button
              @click="openEdit(announcement)"
              class="px-3 py-1 text-sm text-gray-700 hover:bg-gray-100 rounded font-medium"
            >
              Edit
            </button>
            <button
              @click="togglePublish(announcement)"
              :class="['px-3 py-1 text-sm rounded font-medium',
                announcement.isPublished ? 'text-gray-600 hover:bg-gray-100' : 'text-emerald-600 hover:bg-emerald-50']"
            >
              {{ announcement.isPublished ? 'Unpublish' : 'Publish' }}
            </button>
            <button
              v-if="canDelete(announcement)"
              @click="remove(announcement)"
              class="px-3 py-1 text-sm text-red-600 hover:text-red-700 font-medium"
            >
              Delete
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading && visibleAnnouncements.length === 0" class="bg-white rounded-lg shadow p-12 text-center">
      <p class="text-gray-600">
        {{ visibleAnnouncements.length === 0 && (isUser ? announcements.length : true) && searchQuery
          ? 'No announcements match your search.'
          : (isUser ? 'No announcements yet. Use "+ New Announcement" to create one.' : 'No announcements available.') }}
      </p>
    </div>
    <div v-if="loading" class="bg-white rounded-lg shadow p-12 text-center">
      <p class="text-gray-500">Loading announcements…</p>
    </div>
  </div>
</template>
