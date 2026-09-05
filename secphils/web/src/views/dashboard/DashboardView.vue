<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useRole } from '@/composables/useRole'
import {
  useGetMe, useGetProjects,
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
  messageCount: number
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
// Full (unpaginated) project list for the admin Projects card — the default
// /projects call is page-capped, so this fetches enough to count every row.
const allProjects = ref<ProjectRow[]>([])
const loading = ref(true)
const loadError = ref('')
// System Health (admin + staff dashboards)
const apiHealth = ref<'UP' | 'DOWN' | 'UNKNOWN'>('UNKNOWN')
const apiHealthChecked = ref<Date | null>(null)
const maintenanceMode = ref(false)

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
    messageCount: p.messageCount ?? 0,
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    // size 10000: the /projects default page (20) would silently cap every
    // client + staff count on this dashboard (older projects vanish) — same
    // trap as the admin card, fixed once here for the shared first fetch.
    const [meRes, projRes] = await Promise.all([
      useGetMe(), useGetProjects({ size: 10000 }),
    ])
    // GET /users/me returns the UserResponse body directly (no envelope).
    me.value = meRes || null
    const projContent = Array.isArray(projRes) ? projRes : projRes?.content ?? []
    projects.value = projContent.map(mapProject)

    // The "Latest Updates" feed needs only each project's newest message —
    // which /projects already carries batched (latestUpdate*), so build it from
    // that instead of one /messages?projectId= round-trip per project (the old
    // N+1 that made the dashboard feel slow on login).
    const feedRows: MessageRow[] = []
    for (const p of projects.value) {
      const src = projContent.find((x: any) => x.id === p.id)
      if (src && src.latestUpdateAt) {
        feedRows.push({
          id: p.id,
          projectId: p.id,
          projectName: p.name,
          senderName: src.latestUpdateSender ?? null,
          body: src.latestUpdateBody ?? '',
          createdAt: src.latestUpdateAt,
        })
      }
    }
    feedRows.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    messages.value = feedRows

    // Provider staff (USER) mirror the admin dashboard — same tiles, same data:
    // staff have cross-company READ access (projects/documents/messages/audit),
    // so every number below resolves for them exactly as it does for ADMIN.
    if (isAdmin.value || isUser.value) {
      const [compRes, auditRes, usersRes, docsRes, trashRes, projRes2] = await Promise.all([
        useGetCompanies().catch(() => []),
        useGetAuditLogs({ page: 0, size: 20 }).catch(() => ({ content: [] })),
        useGetUsers().catch(() => []),
        // Full, unpaginated lists (admin scope = every company) → true counts.
        useGetDocuments().catch(() => []),
        useGetTrashDocuments().catch(() => []),
        // A large explicit size so the Projects card counts every project, not
        // just the first default page (20) the dashboard feed above fetched.
        useGetProjects({ size: 10000 }).catch(() => []),
      ])
      companies.value = (Array.isArray(compRes) ? compRes : []).map((c: any) => ({ id: c.id, name: c.name }))
      // Audit endpoint now returns a paged envelope { content, total, page, size }.
      auditLogs.value = (auditRes as any)?.content ?? []
      const userList = Array.isArray(usersRes) ? usersRes : []
      allUsers.value = userList.map((u: any) => ({ id: u.id, role: u.role, isActive: !!u.isActive }))
      const userMap: Record<number, string> = {}
      for (const u of userList) userMap[u.id] = u.fullName
      users.value = userMap
      allDocs.value = (Array.isArray(docsRes) ? docsRes : []).map((d: any) => ({ fileSize: d.fileSize ?? null }))
      trashedDocs.value = (Array.isArray(trashRes) ? trashRes : []).map((d: any) => ({ id: d.id }))
      const projContent2 = Array.isArray(projRes2) ? projRes2 : (projRes2 as any)?.content ?? []
      allProjects.value = projContent2.map(mapProject)
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
  notStarted: projects.value.filter(p => p.status === 'NOT_STARTED').length,
  completed: projects.value.filter(p => p.status === 'COMPLETED').length,
}))
const clientProjects = computed(() =>
  [...projects.value].sort((a, b) => (b.progress ?? 0) - (a.progress ?? 0)).slice(0, 5)
)
const latestUpdates = computed(() => messages.value.slice(0, 3))

// Client project-health donut — same SVG ring math as the admin card, driven
// by the client's own full (size 10000) project list.
const clientRing = computed(() => {
  const defs = [
    { status: 'IN_PROGRESS', label: 'In progress', color: '#059669' },
    { status: 'NOT_STARTED', label: 'Not started', color: '#d1d5db' },
    { status: 'COMPLETED', label: 'Completed', color: '#5eead4' },
    { status: 'ARCHIVED', label: 'Archived', color: '#f43f5e' },
  ]
  const total = projects.value.length || 1
  let acc = 0
  return defs.map(d => {
    const value = projects.value.filter(p => p.status === d.status).length
    const frac = value / total
    const seg = {
      label: d.label, color: d.color, value,
      dash: `${frac * RING_C} ${RING_C - frac * RING_C}`,
      offset: `${-acc * RING_C}`,
    }
    acc += frac
    return seg
  })
})

// ---------- Staff + Admin ----------
// totalCompanies is the top-row tile; it counts the `companies` table (client
// companies SECPhils serves) — distinct from client *user* accounts (Users card).
const adminStats = computed(() => ({
  totalCompanies: companies.value.length,
}))
const recentActivity = computed(() => auditLogs.value.slice(0, 8))

// Projects statistics — counted over the full (unpaginated) admin project
// list fetched with a large size, so these are exact totals, not the first
// page's length.
const projectStats = computed(() => ({
  total: allProjects.value.length,
  inProgress: allProjects.value.filter(p => p.status === 'IN_PROGRESS').length,
  notStarted: allProjects.value.filter(p => p.status === 'NOT_STARTED').length,
  completed: allProjects.value.filter(p => p.status === 'COMPLETED').length,
  archived: allProjects.value.filter(p => p.status === 'ARCHIVED').length,
}))
// Projects-card donut (plain SVG, no chart library): each segment is a
// stroke-dasharray slice of the circle's circumference.
const RING_R = 45
const RING_C = 2 * Math.PI * RING_R
const projectRing = computed(() => {
  const defs = [
    { key: 'inProgress' as const, label: 'In progress', color: '#059669' },
    { key: 'notStarted' as const, label: 'Not started', color: '#d1d5db' },
    { key: 'completed' as const, label: 'Completed', color: '#5eead4' },
    { key: 'archived' as const, label: 'Archived', color: '#f43f5e' },
  ]
  const total = projectStats.value.total || 1
  let acc = 0
  return defs.map(d => {
    const value = projectStats.value[d.key]
    const frac = value / total
    const seg = {
      label: d.label, color: d.color, value,
      dash: `${frac * RING_C} ${RING_C - frac * RING_C}`,
      offset: `${-acc * RING_C}`,
    }
    acc += frac
    return seg
  })
})

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
// Users card — Total / Active / Inactive, counted over the full (unpaginated)
// user list. (Staff/Client split intentionally not surfaced here — the Users
// card shows roster status, not role mix.)
const userStatsAdmin = computed(() => {
  const active = allUsers.value.filter(u => u.isActive)
  return {
    total: allUsers.value.length,
    active: active.length,
    inactive: allUsers.value.length - active.length,
  }
})

// "Good afternoon, Jayson" — hour-based, first name only.
const greeting = computed(() => {
  const h = new Date().getHours()
  const time = h < 12 ? 'morning' : h < 17 ? 'afternoon' : 'evening'
  return `Good ${time}, ${(me.value?.fullName ?? '').split(' ')[0] || 'there'}`
})

// Recent Activity timeline helpers: turn a raw audit action (e.g.
// "PROJECT_ARCHIVE") into a human verb + a colored icon chip. The entity
// prefix drives the icon/color; a verb override gives the common ones nicer
// phrasing; anything unmapped falls back to a prettified raw action so new
// actions still render cleanly instead of as a blank chip.
const ACTIVITY_ENTITY_ICONS: Record<string, { icon: string; chip: string }> = {
  PROJECT: { icon: 'fa-solid fa-folder', chip: 'bg-sky-100 text-sky-700' },
  DOCUMENT: { icon: 'fa-solid fa-file-lines', chip: 'bg-indigo-100 text-indigo-700' },
  COMPANY: { icon: 'fa-solid fa-building', chip: 'bg-emerald-100 text-emerald-700' },
  USER: { icon: 'fa-solid fa-user', chip: 'bg-amber-100 text-amber-700' },
  SERVICE: { icon: 'fa-solid fa-tag', chip: 'bg-violet-100 text-violet-700' },
  ROLE: { icon: 'fa-solid fa-shield-halved', chip: 'bg-cyan-100 text-cyan-700' },
  TASK: { icon: 'fa-solid fa-list-check', chip: 'bg-lime-100 text-lime-700' },
  MESSAGE: { icon: 'fa-regular fa-message', chip: 'bg-gray-100 text-gray-600' },
  ANNOUNCEMENT: { icon: 'fa-solid fa-bullhorn', chip: 'bg-orange-100 text-orange-700' },
  REVIEW: { icon: 'fa-regular fa-star', chip: 'bg-yellow-100 text-yellow-700' },
  SETTINGS: { icon: 'fa-solid fa-sliders', chip: 'bg-amber-100 text-amber-700' },
  NOTIFICATION: { icon: 'fa-regular fa-bell', chip: 'bg-gray-100 text-gray-600' },
  DROPDOWN: { icon: 'fa-solid fa-list', chip: 'bg-gray-100 text-gray-600' },
  STORAGE: { icon: 'fa-solid fa-database', chip: 'bg-gray-100 text-gray-600' },
}
const ACTIVITY_VERBS: Record<string, string> = {
  PROJECT_CREATE: 'created a project', PROJECT_UPDATE: 'updated a project',
  PROJECT_ARCHIVE: 'archived a project', PROJECT_RESTORE: 'restored a project',
  PROJECT_HARD_DELETE: 'permanently deleted a project',
  DOCUMENT_CREATE: 'created a document', DOCUMENT_UPLOAD: 'uploaded a document',
  DOCUMENT_UPDATE: 'updated a document', DOCUMENT_DELETE: 'deleted a document',
  DOCUMENT_TRASH: 'trashed a document', DOCUMENT_RESTORE: 'restored a document',
  DOCUMENT_PERMANENT_DELETE: 'purged a document', DOCUMENT_TRASH_PURGED: 'purged a trashed document',
  DOCUMENT_TRASH_EMPTY: 'emptied the document trash',
  COMPANY_CREATE: 'created a company', COMPANY_UPDATE: 'updated a company',
  COMPANY_TEAM_INVITE: 'invited a company team member',
  COMPANY_CUSTOMER_REP_INVITE: 'invited a company representative',
  USER_CREATE: 'created a user', USER_UPDATE: 'updated a user',
  USER_ACTIVATE: 'activated a user', USER_DEACTIVATE: 'deactivated a user',
  USER_HARD_DELETE: 'permanently deleted a user', USER_LOGIN: 'logged in',
  USER_LOGIN_2FA: 'logged in (2FA)', USER_INVITE_SENT: 'sent a user invite',
  USER_SET_PASSWORD: 'set a user password',
  SERVICE_CREATE: 'created a service', SERVICE_UPDATE: 'updated a service',
  SERVICE_ACTIVATE: 'activated a service', SERVICE_DEACTIVATE: 'deactivated a service',
  SERVICE_HARD_DELETE: 'permanently deleted a service',
  ROLE_CREATE: 'created a role', ROLE_UPDATE: 'updated a role', ROLE_DELETE: 'deleted a role',
  TASK_CREATE: 'created a task', TASK_UPDATE: 'updated a task',
  TASK_DELETE: 'deleted a task', TASK_STATUS_CHANGE: 'changed a task status',
  SETTINGS_UPDATE: 'updated system settings',
  ANNOUNCEMENT_CREATE: 'created an announcement', ANNOUNCEMENT_UPDATE: 'updated an announcement',
  ANNOUNCEMENT_PUBLISH: 'published an announcement', ANNOUNCEMENT_DELETE: 'deleted an announcement',
  MESSAGE_SEND: 'sent a message', REVIEW_CREATE: 'added a review',
  REVIEW_STATUS_CHANGE: 'updated a review status',
}
function prettifyAction(action: string): string {
  return (action || 'event').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
}
function activityMeta(action: string) {
  const entity = (action || '').split('_')[0]
  const ico = ACTIVITY_ENTITY_ICONS[entity] ?? { icon: 'fa-solid fa-circle-info', chip: 'bg-gray-100 text-gray-600' }
  return { verb: ACTIVITY_VERBS[action] ?? prettifyAction(action), icon: ico.icon, chip: ico.chip }
}
function activityDetail(log: { details?: string }): string {
  const raw = log.details || ''
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && 'message' in parsed && parsed.message) return String(parsed.message)
  } catch {
    // not JSON — use as-is
  }
  return raw
}
// Who did it: prefer the user map; login events have a NULL user row, so fall
// back to the email captured in the JSONB details, else "System".
function activityActor(log: { userId?: number | string; details?: string }, users: Record<number, string>): string {
  if (log.userId != null && log.userId !== '' && users[log.userId as number]) return users[log.userId as number]
  const m = (log.details || '').match(/([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/)
  return m ? m[1] : 'System'
}
// Sub-line: suppress details that only restate the actor (login rows show the
// same email twice otherwise).
function activitySubLine(log: { userId?: number | string; details?: string }, users: Record<number, string>): string {
  const d = activityDetail(log)
  const em = d.match(/([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/)
  if (em && em[1] === activityActor(log, users)) return ''
  return d
}
// Page header pill — green when the API is up and maintenance is off.
const statusPill = computed(() => {
  if (apiHealth.value === 'DOWN') return { text: 'API down — investigate', cls: 'bg-red-50 text-red-700 border-red-200', dot: 'bg-red-500' }
  if (maintenanceMode.value) return { text: 'Maintenance mode is ON', cls: 'bg-amber-50 text-amber-700 border-amber-200', dot: 'bg-amber-500' }
  if (apiHealth.value === 'UP') return { text: 'All systems operational', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200', dot: 'bg-emerald-500' }
  return { text: 'Checking system health…', cls: 'bg-gray-100 text-gray-600 border-gray-200', dot: 'bg-gray-400' }
})

const goToProject = (id: number) => router.push(`/projects/${id}`)
</script>

<template>
  <div>
    <!-- The client dashboard template below carries its own header (title +
         status pill); staff + admin share the greeting + status-pill header
         in their shared dashboard template. No standalone generic header. -->
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
      <div class="flex items-start justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">{{ greeting }}</h1>
          <p class="text-gray-600 mt-1">Your projects and latest updates from your consultants.</p>
        </div>
        <span class="mt-1 inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold" :class="statusPill.cls">
          <span class="h-2 w-2 rounded-full" :class="statusPill.dot"></span>
          {{ statusPill.text }}
        </span>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
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
              class="p-5 hover:bg-gray-50 transition-colors cursor-pointer"
            >
              <div class="flex items-center justify-between mb-2">
                <div class="min-w-0 pr-3">
                  <h3 class="font-medium text-gray-900 truncate">{{ project.name }}</h3>
                  <p class="text-xs text-gray-500 mt-0.5">{{ project.serviceName || '—' }}</p>
                </div>
                <span class="shrink-0 px-2 py-1 text-xs font-medium rounded-full" :class="PROJECT_STATUS_COLORS[project.statusLabel]">
                  {{ project.statusLabel }}
                </span>
              </div>
              <div class="h-2 bg-gray-100 rounded-full overflow-hidden mt-1">
                <div class="h-full bg-emerald-500 rounded-full" :style="{ width: Math.min(100, project.progress ?? 0) + '%' }"></div>
              </div>
              <div class="flex items-center justify-between mt-2 text-xs text-gray-500">
                <span>{{ (project.progress ?? 0) > 0 ? `${project.progress}% complete` : 'Not started yet' }}</span>
                <span><i class="fa-regular fa-message mr-1"></i>{{ project.messageCount }} {{ project.messageCount === 1 ? 'update' : 'updates' }}</span>
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
                <p class="text-xs text-gray-500">{{ formatDateTime(update.createdAt) }} &middot; {{ update.senderName || 'SECPhils' }}</p>
                <p class="text-sm font-medium text-gray-900 mt-0.5">{{ update.projectName }}</p>
                <p class="text-sm text-gray-600 mt-1">{{ update.body }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow mt-6">
        <h2 class="text-lg font-semibold text-gray-900 p-6 border-b border-gray-200">Overall Project Health</h2>
        <div class="p-6 flex items-center justify-center gap-10 flex-wrap">
          <div class="relative w-40 h-40">
            <svg class="-rotate-90" width="160" height="160" viewBox="0 0 160 160">
              <circle cx="80" cy="80" r="63" fill="none" stroke="#f1f1f1" stroke-width="16" />
              <circle
                v-for="seg in clientRing.filter(s => s.value > 0)"
                :key="seg.label"
                cx="80" cy="80" r="63" fill="none"
                :stroke="seg.color" stroke-width="16"
                :stroke-dasharray="seg.dash"
                :stroke-dashoffset="seg.offset"
              />
            </svg>
            <div class="absolute inset-0 flex flex-col items-center justify-center">
              <span class="text-3xl font-extrabold text-gray-900">{{ projects.length }}</span>
              <span class="text-xs text-gray-500">projects</span>
            </div>
          </div>
          <div class="flex flex-col gap-2.5">
            <div v-for="seg in clientRing" :key="seg.label" class="flex items-center gap-2 text-sm">
              <span class="w-3 h-3 rounded" :style="{ background: seg.color }"></span>
              <span class="text-gray-600">{{ seg.label }}</span>
              <span class="ml-auto font-semibold text-gray-500">{{ seg.value }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ================= STAFF + ADMIN DASHBOARD ================= -->
    <!-- USER (provider staff) is intentionally identical to ADMIN: same tiles,
         same feeds, same numbers — backed by staff cross-company READ access. -->
    <template v-else>
      <div class="flex items-start justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">{{ greeting }}</h1>
          <p class="text-gray-600 mt-1">System overview, key metrics, and recent activity.</p>
        </div>
        <span class="mt-1 inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold" :class="statusPill.cls">
          <span class="h-2 w-2 rounded-full" :class="statusPill.dot"></span>
          {{ statusPill.text }}
        </span>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-5 flex flex-col">
          <div class="flex items-center justify-between">
            <p class="text-sm font-semibold text-gray-600">Client Companies</p>
            <div class="w-9 h-9 bg-emerald-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-building text-emerald-600"></i>
            </div>
          </div>
          <p class="text-3xl font-extrabold text-gray-900 mt-2">{{ adminStats.totalCompanies }}</p>
          <p class="text-xs text-gray-500 mt-1">
            client companies served &middot; {{ userStatsAdmin.total }} accounts ({{ userStatsAdmin.active }} active)
          </p>
        </div>

        <!-- Projects card: donut over the full unpaginated project list, so
             the chart reflects every project, not the first page's length. -->
        <div class="bg-white rounded-lg shadow p-5 sm:col-span-2">
          <div class="flex items-center justify-between">
            <h2 class="text-base font-semibold text-gray-900">Projects</h2>
            <RouterLink to="/projects" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View all</RouterLink>
          </div>
          <div class="flex items-center gap-6 mt-3">
            <div class="relative flex-none">
              <svg viewBox="0 0 100 100" class="w-28 h-28 -rotate-90">
                <circle cx="50" cy="50" :r="RING_R" fill="none" stroke="#f1f5f9" stroke-width="11"></circle>
                <circle v-for="(seg, i) in projectRing" :key="i" cx="50" cy="50" :r="RING_R" fill="none"
                  :stroke="seg.color" stroke-width="11" :stroke-dasharray="seg.dash" :stroke-dashoffset="seg.offset"></circle>
              </svg>
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <span class="text-2xl font-extrabold text-gray-900 leading-none">{{ projectStats.total }}</span>
                <span class="text-[10px] text-gray-500 mt-0.5">projects</span>
              </div>
            </div>
            <div class="flex-1 space-y-2.5">
              <div v-for="seg in projectRing" :key="seg.label" class="flex items-center gap-2.5 text-sm">
                <span class="h-2.5 w-2.5 rounded-sm" :style="{ backgroundColor: seg.color }"></span>
                <span class="text-gray-600">{{ seg.label }}</span>
                <span class="ml-auto font-semibold text-gray-900">{{ seg.value }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Documents & Users statistics -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div class="bg-white rounded-lg shadow">
          <div class="p-5 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-base font-semibold text-gray-900">Documents</h2>
            <RouterLink to="/documents" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View</RouterLink>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3.5 p-5">
            <div class="flex items-center gap-3 rounded-xl border border-gray-200 p-4">
              <div class="flex h-[42px] w-[42px] flex-none items-center justify-center rounded-[11px] bg-indigo-100 text-lg text-indigo-600">
                <i class="fa-solid fa-file-lines"></i>
              </div>
              <div>
                <p class="text-[22px] font-extrabold leading-none text-gray-900">{{ documentStats.total }}</p>
                <p class="mt-1 text-xs font-medium text-gray-500">Total files</p>
              </div>
            </div>
            <div class="flex items-center gap-3 rounded-xl border border-gray-200 p-4">
              <div class="flex h-[42px] w-[42px] flex-none items-center justify-center rounded-[11px] bg-amber-100 text-lg text-amber-600">
                <i class="fa-solid fa-trash-can"></i>
              </div>
              <div>
                <p class="text-[22px] font-extrabold leading-none text-gray-900">{{ documentStats.trashed }}</p>
                <p class="mt-1 text-xs font-medium text-gray-500">In trash</p>
              </div>
            </div>
            <div class="flex items-center gap-3 rounded-xl border border-gray-200 p-4">
              <div class="flex h-[42px] w-[42px] flex-none items-center justify-center rounded-[11px] bg-sky-100 text-lg text-sky-600">
                <i class="fa-solid fa-database"></i>
              </div>
              <div>
                <p class="text-[22px] font-extrabold leading-none text-gray-900">{{ documentStats.storage }}</p>
                <p class="mt-1 text-xs font-medium text-gray-500">Storage used</p>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Users</h2>
            <RouterLink v-if="isAdmin" to="/admin" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View</RouterLink>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3.5 p-5">
            <div class="flex items-center gap-3 rounded-xl border border-gray-200 p-4">
              <div class="flex h-[42px] w-[42px] flex-none items-center justify-center rounded-[11px] bg-gray-100 text-lg text-gray-600">
                <i class="fa-solid fa-users"></i>
              </div>
              <div>
                <p class="text-[26px] font-extrabold leading-none text-gray-900">{{ userStatsAdmin.total }}</p>
                <p class="mt-1 text-xs font-medium text-gray-500">Total</p>
              </div>
            </div>
            <div class="flex items-center gap-3 rounded-xl border border-gray-200 p-4">
              <div class="flex h-[42px] w-[42px] flex-none items-center justify-center rounded-[11px] bg-emerald-100 text-lg text-emerald-600">
                <i class="fa-solid fa-user-check"></i>
              </div>
              <div>
                <p class="text-[26px] font-extrabold leading-none text-emerald-600">{{ userStatsAdmin.active }}</p>
                <p class="mt-1 text-xs font-medium text-gray-500">Active</p>
              </div>
            </div>
            <div class="flex items-center gap-3 rounded-xl border border-gray-200 p-4">
              <div class="flex h-[42px] w-[42px] flex-none items-center justify-center rounded-[11px] bg-gray-100 text-lg text-gray-400">
                <i class="fa-solid fa-user-slash"></i>
              </div>
              <div>
                <p class="text-[26px] font-extrabold leading-none text-gray-600">{{ userStatsAdmin.inactive }}</p>
                <p class="mt-1 text-xs font-medium text-gray-500">Inactive</p>
              </div>
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
          <div class="p-5 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-base font-semibold text-gray-900">Recent Activity</h2>
            <RouterLink v-if="isAdmin" to="/admin" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">Audit logs</RouterLink>
          </div>
          <div class="divide-y divide-gray-100">
            <div v-if="recentActivity.length === 0" class="p-5 text-sm text-gray-500">No activity recorded yet.</div>
            <div v-for="log in recentActivity" :key="log.id" class="px-5 py-3 flex items-center gap-3">
              <div class="flex h-9 w-9 flex-none items-center justify-center rounded-[10px]" :class="activityMeta(log.action).chip">
                <i :class="activityMeta(log.action).icon" class="text-sm"></i>
              </div>
              <div class="min-w-0 flex-1">
                <p class="text-sm text-gray-800 leading-snug">
                  <span class="font-semibold text-gray-900">{{ activityActor(log, users) }}</span>
                  {{ activityMeta(log.action).verb }}
                </p>
                <p v-if="activitySubLine(log, users)" class="text-xs text-gray-500 truncate mt-0.5">{{ activitySubLine(log, users) }}</p>
              </div>
              <span class="flex-none text-xs text-gray-400 whitespace-nowrap">{{ relativeTime(log.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
