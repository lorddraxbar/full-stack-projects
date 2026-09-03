<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useRole } from '@/composables/useRole'
import {
  useGetMe, useGetProjects, useGetMessages,
  useGetAuditLogs, useGetCompanies, useGetUsers, useGetApiHealth, useGetLanding,
  useGetDocuments, useGetTrashDocuments,
} from '@/services/api'
import {
  projectStatusLabel,
  PROJECT_STATUS_COLORS,
  formatDate, formatDateTime, formatFileSize,
} from '@/lib/labels'

const { isClient, isAdmin } = useRole()
const isUser = computed(() => !isClient.value && !isAdmin.value)
const router = useRouter()

interface ProjectRow {
  id: number
  name: string
  companyName: string | null
  serviceName: string | null
  status: string
  statusLabel: string
  progress: number
}

interface MessageRow {
  id: number
  projectId: number | null
  projectName: string
  senderName: string | null
  body: string
  createdAt: string
}

const me = ref<{ id: number; fullName: string; role: string } | null>(null)
const projects = ref<ProjectRow[]>([])
const messages = ref<MessageRow[]>([])
const companies = ref<{ id: number; name: string }[]>([])
const auditLogs = ref<{ id: number; userId: number | string; action: string; entityType: string; details: string; createdAt: string }[]>([])
const users = ref<Record<number, string>>({})
// Full, unpaginated lists (admin scope = all companies) — used to compute
// true Documents/Users statistics on the admin dashboard.
const allUsers = ref<{ id: number; role: string; isActive: boolean }[]>([])
const allDocs = ref<{ fileSize: number | null }[]>([])
const trashedDocs = ref<{ id: number }[]>([])
const loading = ref(true)
const loadError = ref('')
// System Health (admin + staff dashboards)
const apiHealth = ref<'UP' | 'DOWN' | 'UNKNOWN'>('UNKNOWN')
const apiHealthChecked = ref<Date | null>(null)
const maintenanceMode = ref(false)

function projectName(id: number | null): string {
  if (id == null) return '—'
  return projects.value.find(p => p.id === id)?.name ?? `Project #${id}`
}

// Local clock with seconds for the "health checked" row.
function formatTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function relativeTime(iso: string): string {
  const then = new Date(iso).getTime()
  if (isNaN(then)) return iso
  const diff = Date.now() - then
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return formatDate(iso)
}

function mapProject(p: any): ProjectRow {
  return {
    id: p.id,
    name: p.name,
    companyName: p.companyName ?? null,
    serviceName: p.serviceName ?? null,
    status: p.status,
    statusLabel: projectStatusLabel(p.status),
    progress: p.progress ?? 0,
  }
}

function mapMessage(m: any): MessageRow {
  return {
    id: m.id,
    projectId: m.projectId,
    projectName: projectName(m.projectId),
    senderName: m.senderName ?? null,
    body: m.body,
    createdAt: m.createdAt,
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [meRes, projRes] = await Promise.all([
      useGetMe(), useGetProjects(),
    ])
    // GET /users/me returns the UserResponse body directly (no envelope).
    me.value = meRes || null
    const projContent = Array.isArray(projRes) ? projRes : projRes?.content ?? []
    projects.value = projContent.map(mapProject)

    // Messages live per project — fetch for each project in parallel.
    const msgResults = await Promise.all(
      projects.value.map(p => useGetMessages(p.id).catch(() => []))
    )
    const allMessages = msgResults.flat().map(mapMessage)
    allMessages.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    messages.value = allMessages

    if (isAdmin.value) {
      const [compRes, auditRes, usersRes, docsRes, trashRes] = await Promise.all([
        useGetCompanies().catch(() => []),
        useGetAuditLogs({ limit: 20 }).catch(() => []),
        useGetUsers().catch(() => []),
        // Full, unpaginated lists (admin scope = every company) → true counts.
        useGetDocuments().catch(() => []),
        useGetTrashDocuments().catch(() => []),
      ])
      companies.value = (Array.isArray(compRes) ? compRes : []).map((c: any) => ({ id: c.id, name: c.name }))
      auditLogs.value = Array.isArray(auditRes) ? auditRes : []
      const userList = Array.isArray(usersRes) ? usersRes : []
      allUsers.value = userList.map((u: any) => ({ id: u.id, role: u.role, isActive: !!u.isActive }))
      const userMap: Record<number, string> = {}
      for (const u of userList) userMap[u.id] = u.fullName
      users.value = userMap
      allDocs.value = (Array.isArray(docsRes) ? docsRes : []).map((d: any) => ({ fileSize: d.fileSize ?? null }))
      trashedDocs.value = (Array.isArray(trashRes) ? trashRes : []).map((d: any) => ({ id: d.id }))
    }
  } catch (err: any) {
    loadError.value = err?.response?.data?.message || err?.message || 'Failed to load dashboard data'
  } finally {
    loading.value = false
  }
}

// ---------- System Health ----------
// Live probes, never blocking the dashboard load:
//  - API: Spring actuator via the nginx /api/health route (UP/DOWN + time)
//  - Maintenance: system setting, mirrored off the public landing endpoint
//    (the admin /settings endpoint is admin-only, but this card is shared
//    with the staff dashboard).
let healthTimer: ReturnType<typeof setInterval> | null = null
async function refreshSystemHealth() {
  const t = await useGetApiHealth()
    .then(r => (r?.status === 'UP' || r?.status === 'DOWN') ? r.status as 'UP' | 'DOWN' : 'UNKNOWN')
    .catch(() => 'UNKNOWN' as const)
  apiHealth.value = t
  apiHealthChecked.value = new Date()
  await useGetLanding()
    .then(l => { maintenanceMode.value = !!l?.maintenanceMode })
    .catch(() => {})
}

onMounted(() => {
  load()
  refreshSystemHealth()
  healthTimer = setInterval(refreshSystemHealth, 5 * 60 * 1000)
})
onBeforeUnmount(() => {
  if (healthTimer) clearInterval(healthTimer)
})

// ---------- Client ----------
const clientStats = computed(() => ({
  assignedProjects: projects.value.length,
  inProgress: projects.value.filter(p => p.status === 'IN_PROGRESS').length,
}))
const clientProjects = computed(() =>
  [...projects.value].sort((a, b) => (b.progress ?? 0) - (a.progress ?? 0)).slice(0, 5)
)
const latestUpdates = computed(() => messages.value.slice(0, 3))

// ---------- user ----------
const userStats = computed(() => ({
  activeProjects: projects.value.filter(p => p.status === 'IN_PROGRESS').length,
}))
const recentMessages = computed(() => messages.value.slice(0, 3))
const activeProjects = computed(() =>
  projects.value.filter(p => p.status === 'IN_PROGRESS' || p.status === 'NOT_STARTED').slice(0, 5)
)
const projectUpdates = computed(() => messages.value.slice(0, 3))

// ---------- Admin ----------
const adminStats = computed(() => ({
  totalClients: companies.value.length,
  activeProjects: projects.value.filter(p => p.status === 'IN_PROGRESS').length,
}))
const recentActivity = computed(() => auditLogs.value.slice(0, 8))

// Documents & Users statistics — derived from the full (unpaginated) admin
// lists, so the numbers are exact, not capped by any list size.
const documentStats = computed(() => {
  const totalSize = allDocs.value.reduce((sum, d) => sum + (d.fileSize ?? 0), 0)
  return {
    total: allDocs.value.length,
    trashed: trashedDocs.value.length,
    storage: formatFileSize(totalSize),
  }
})
const userStatsAdmin = computed(() => {
  const active = allUsers.value.filter(u => u.isActive)
  return {
    total: allUsers.value.length,
    active: active.length,
    staff: active.filter(u => u.role !== 'CLIENT').length,
    clients: active.filter(u => u.role === 'CLIENT').length,
    inactive: allUsers.value.length - active.length,
  }
})

const goToProject = (id: number) => router.push(`/projects/${id}`)
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">
        {{ isClient ? 'Client Dashboard' : isUser ? 'User Dashboard' : 'Admin Dashboard' }}
      </h1>
      <p class="text-gray-600 mt-1">
        {{ isClient
          ? 'Your projects and latest updates from your consultants.'
          : isUser
            ? 'Your active projects and recent messages.'
            : 'System overview, key metrics, and recent activity.' }}
      </p>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20">
      <svg class="animate-spin h-8 w-8 text-emerald-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
    </div>

    <div v-else-if="loadError" class="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
      {{ loadError }}
    </div>

    <!-- ================= CLIENT DASHBOARD ================= -->
    <template v-else-if="isClient">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Assigned Projects</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ clientStats.assignedProjects }}</p>
            </div>
            <div class="w-12 h-12 bg-emerald-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-folder-open text-emerald-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">In Progress</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ clientStats.inProgress }}</p>
            </div>
            <div class="w-12 h-12 bg-yellow-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-spinner text-yellow-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Project Updates</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ messages.length }}</p>
            </div>
            <div class="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-bolt text-purple-600 text-xl"></i>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Your Projects</h2>
            <RouterLink to="/projects" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View all</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div v-if="clientProjects.length === 0" class="p-6 text-sm text-gray-500">
              No projects yet.
            </div>
            <div
              v-for="project in clientProjects"
              :key="project.id"
              @click="goToProject(project.id)"
              class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
            >
              <div class="flex items-center justify-between mb-2">
                <h3 class="font-medium text-gray-900">{{ project.name }}</h3>
                <span :class="['px-2 py-1 text-xs font-medium rounded-full', PROJECT_STATUS_COLORS[project.statusLabel]]">
                  {{ project.statusLabel }}
                </span>
              </div>
              <div class="flex items-center justify-between text-sm text-gray-600">
                <span>{{ project.serviceName || '—' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200">
            <h2 class="text-lg font-semibold text-gray-900">Latest Updates</h2>
          </div>
          <div class="p-6">
            <div v-if="latestUpdates.length === 0" class="text-sm text-gray-500">
              No updates yet.
            </div>
            <div v-else class="relative">
              <div class="absolute left-3 top-1 bottom-1 w-px bg-gray-200" />
              <div v-for="update in latestUpdates" :key="update.id" class="relative pl-10 pb-6 last:pb-0">
                <span class="absolute left-1.5 top-1 w-3 h-3 rounded-full bg-emerald-500 ring-4 ring-emerald-100" />
                <p class="text-xs text-gray-500">{{ formatDateTime(update.createdAt) }} &middot; {{ update.senderName || '—' }}</p>
                <p class="text-sm font-medium text-gray-900 mt-0.5">{{ update.projectName }}</p>
                <p class="text-sm text-gray-600 mt-1">{{ update.body }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ================= user DASHBOARD ================= -->
    <template v-else-if="isUser">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Active Projects</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ userStats.activeProjects }}</p>
            </div>
            <div class="w-12 h-12 bg-emerald-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-folder-open text-emerald-600 text-xl"></i>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Recent Messages</h2>
            <RouterLink to="/messages" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">Open inbox</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div v-if="recentMessages.length === 0" class="p-6 text-sm text-gray-500">No messages yet.</div>
            <div v-for="msg in recentMessages" :key="msg.id" class="p-6 hover:bg-gray-50 transition-colors">
              <div class="flex items-center justify-between mb-1">
                <h3 class="font-medium text-gray-900 text-sm">{{ msg.projectName }}</h3>
                <span class="text-xs text-gray-500">{{ relativeTime(msg.createdAt) }}</span>
              </div>
              <p class="text-sm text-gray-700">{{ msg.senderName || '—' }}: {{ msg.body }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Projects</h2>
            <RouterLink to="/projects" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View all</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div v-if="activeProjects.length === 0" class="p-6 text-sm text-gray-500">No active projects.</div>
            <div
              v-for="project in activeProjects"
              :key="project.id"
              @click="goToProject(project.id)"
              class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
            >
              <div class="flex items-center justify-between mb-2">
                <h3 class="font-medium text-gray-900">{{ project.name }}</h3>
                <span :class="['px-2 py-1 text-xs font-medium rounded-full', PROJECT_STATUS_COLORS[project.statusLabel]]">
                  {{ project.statusLabel }}
                </span>
              </div>
              <div class="flex items-center justify-between text-sm text-gray-600">
                <span>{{ project.companyName || '—' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200">
            <h2 class="text-lg font-semibold text-gray-900">Project Updates</h2>
          </div>
          <div class="p-6">
            <div v-if="projectUpdates.length === 0" class="text-sm text-gray-500">No updates yet.</div>
            <div v-else class="relative">
              <div class="absolute left-3 top-1 bottom-1 w-px bg-gray-200" />
              <div v-for="update in projectUpdates" :key="update.id" class="relative pl-10 pb-6 last:pb-0">
                <span class="absolute left-1.5 top-1 w-3 h-3 rounded-full bg-emerald-500 ring-4 ring-emerald-100" />
                <p class="text-xs text-gray-500">{{ formatDateTime(update.createdAt) }} &middot; {{ update.senderName || '—' }}</p>
                <p class="text-sm font-medium text-gray-900 mt-0.5">{{ update.projectName }}</p>
                <p class="text-sm text-gray-600 mt-1">{{ update.body }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ================= ADMIN DASHBOARD ================= -->
    <template v-else>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Total Clients</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ adminStats.totalClients }}</p>
            </div>
            <div class="w-12 h-12 bg-emerald-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-building text-emerald-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Active Projects</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ adminStats.activeProjects }}</p>
            </div>
            <div class="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-folder-open text-green-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Total Contracts</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ projects.length }}</p>
            </div>
            <div class="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-file-contract text-purple-600 text-xl"></i>
            </div>
          </div>
        </div>
      </div>

      <!-- Documents & Users statistics -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Documents</h2>
            <RouterLink to="/documents" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View</RouterLink>
          </div>
          <div class="grid grid-cols-3 divide-x divide-gray-100 text-center">
            <div class="p-5">
              <p class="text-2xl font-bold text-gray-900">{{ documentStats.total }}</p>
              <p class="text-xs text-gray-500 mt-1">Total files</p>
            </div>
            <div class="p-5">
              <p class="text-2xl font-bold text-gray-900">{{ documentStats.trashed }}</p>
              <p class="text-xs text-gray-500 mt-1">In trash</p>
            </div>
            <div class="p-5">
              <p class="text-2xl font-bold text-gray-900">{{ documentStats.storage }}</p>
              <p class="text-xs text-gray-500 mt-1">Storage used</p>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Users</h2>
            <RouterLink to="/admin" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View</RouterLink>
          </div>
          <div class="grid grid-cols-4 divide-x divide-gray-100 text-center">
            <div class="p-5">
              <p class="text-2xl font-bold text-gray-900">{{ userStatsAdmin.total }}</p>
              <p class="text-xs text-gray-500 mt-1">Total</p>
            </div>
            <div class="p-5">
              <p class="text-2xl font-bold text-emerald-600">{{ userStatsAdmin.active }}</p>
              <p class="text-xs text-gray-500 mt-1">Active</p>
            </div>
            <div class="p-5">
              <p class="text-2xl font-bold text-gray-900">{{ userStatsAdmin.staff }}</p>
              <p class="text-xs text-gray-500 mt-1">Staff</p>
            </div>
            <div class="p-5">
              <p class="text-2xl font-bold text-gray-900">{{ userStatsAdmin.clients }}</p>
              <p class="text-xs text-gray-500 mt-1">Clients</p>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">System Health</h2>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-gray-700">API</span>
              <span
                class="px-2 py-1 text-xs font-medium rounded-full"
                :class="apiHealth === 'UP'
                  ? 'bg-green-100 text-green-800'
                  : apiHealth === 'DOWN'
                    ? 'bg-red-100 text-red-800'
                    : 'bg-gray-100 text-gray-600'"
              >{{ apiHealth === 'UP' ? 'UP' : apiHealth === 'DOWN' ? 'DOWN' : 'CHECKING…' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Maintenance Mode</span>
              <span
                class="px-2 py-1 text-xs font-medium rounded-full"
                :class="maintenanceMode ? 'bg-amber-100 text-amber-800' : 'bg-green-100 text-green-800'"
              >{{ maintenanceMode ? 'ON' : 'OFF' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Authenticated User</span>
              <span class="text-sm text-gray-600">{{ me?.fullName || '—' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Role</span>
              <span class="text-sm text-gray-600">{{ me?.role || '—' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Health checked</span>
              <span class="text-sm text-gray-500">{{ apiHealthChecked ? formatTime(apiHealthChecked.toISOString()) : '—' }}</span>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Recent Activity</h2>
            <RouterLink to="/admin" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">Audit logs</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div v-if="recentActivity.length === 0" class="p-4 text-sm text-gray-500">No activity recorded yet.</div>
            <div v-for="log in recentActivity" :key="log.id" class="p-4 hover:bg-gray-50 transition-colors">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span class="px-2 py-0.5 bg-emerald-100 text-emerald-800 text-xs font-medium rounded">{{ log.action }}</span>
                  <span class="text-sm font-medium text-gray-900">{{ log.entityType }}</span>
                </div>
                <span class="text-xs text-gray-500">{{ formatDateTime(log.createdAt) }}</span>
              </div>
              <p class="text-xs text-gray-600 mt-1">{{ users[log.userId as number] || `User #${log.userId}` }} &mdash; {{ log.details }}</p>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
