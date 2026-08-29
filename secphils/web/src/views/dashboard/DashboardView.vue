<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRole } from '@/composables/useRole'
import {
  useGetMe, useGetProjects, useGetTasks, useGetMessages, useGetNotifications,
  useGetAuditLogs, useGetCompanies, useGetUsers,
} from '@/services/api'
import {
  projectStatusLabel, taskStatusLabel, priorityLabel,
  PROJECT_STATUS_COLORS, PRIORITY_COLORS, TASK_STATUS_COLORS,
  formatDate, formatDateTime, formatPhpCompact,
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

interface TaskRow {
  id: number
  projectId: number | null
  assigneeId: number | null
  projectName: string
  title: string
  assigneeName: string | null
  status: string
  statusLabel: string
  priority: string
  priorityLabel: string
  dueDate: string | null
  createdAt: string
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
const tasks = ref<TaskRow[]>([])
const messages = ref<MessageRow[]>([])
const notifications = ref<{ id: number; isRead: boolean }[]>([])
const companies = ref<{ id: number; name: string }[]>([])
const auditLogs = ref<{ id: number; userId: number | string; action: string; entityType: string; details: string; createdAt: string }[]>([])
const users = ref<Record<number, string>>({})
const loading = ref(true)
const loadError = ref('')

function projectName(id: number | null): string {
  if (id == null) return '—'
  return projects.value.find(p => p.id === id)?.name ?? `Project #${id}`
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

function mapTask(t: any): TaskRow {
  return {
    id: t.id,
    projectId: t.projectId,
    assigneeId: t.assigneeId ?? null,
    projectName: projectName(t.projectId),
    title: t.title,
    assigneeName: t.assigneeName ?? null,
    status: t.status,
    statusLabel: taskStatusLabel(t.status),
    priority: t.priority,
    priorityLabel: priorityLabel(t.priority),
    dueDate: t.dueDate ?? null,
    createdAt: t.createdAt,
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
    const [meRes, projRes, taskRes, notifRes] = await Promise.all([
      useGetMe(), useGetProjects(),
      // Admins keep the pre-scoping behaviour (all tasks); everyone else now
      // only sees their own tasks on the dashboard.
      useGetTasks(isAdmin.value ? { scope: 'ALL' } : undefined), useGetNotifications(),
    ])
    me.value = meRes.user ?? null
    const projContent = Array.isArray(projRes) ? projRes : projRes?.content ?? []
    projects.value = projContent.map(mapProject)
    tasks.value = (Array.isArray(taskRes) ? taskRes : []).map(mapTask)
    notifications.value = Array.isArray(notifRes) ? notifRes : []

    // Messages live per project — fetch for each project in parallel.
    const msgResults = await Promise.all(
      projects.value.map(p => useGetMessages(p.id).catch(() => []))
    )
    const allMessages = msgResults.flat().map(mapMessage)
    allMessages.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    messages.value = allMessages

    if (isAdmin.value) {
      const [compRes, auditRes, usersRes] = await Promise.all([
        useGetCompanies().catch(() => []),
        useGetAuditLogs({ limit: 20 }).catch(() => []),
        useGetUsers().catch(() => []),
      ])
      companies.value = (Array.isArray(compRes) ? compRes : []).map((c: any) => ({ id: c.id, name: c.name }))
      auditLogs.value = Array.isArray(auditRes) ? auditRes : []
      const userMap: Record<number, string> = {}
      for (const u of (Array.isArray(usersRes) ? usersRes : [])) userMap[u.id] = u.fullName
      users.value = userMap
    }
  } catch (err: any) {
    loadError.value = err?.response?.data?.message || err?.message || 'Failed to load dashboard data'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------- Client ----------
const clientStats = computed(() => ({
  assignedProjects: projects.value.length,
  inProgress: projects.value.filter(p => p.status === 'IN_PROGRESS').length,
  pendingDocuments: 0, // documents endpoint has no client scoping; shown in Documents view
  unreadMessages: notifications.value.filter(n => !n.isRead).length,
}))
const clientProjects = computed(() =>
  [...projects.value].sort((a, b) => (b.progress ?? 0) - (a.progress ?? 0)).slice(0, 5)
)
const latestUpdates = computed(() => messages.value.slice(0, 3))

// ---------- user ----------
const userStats = computed(() => {
  const weekFromNow = Date.now() + 7 * 86400000
  return {
    activeProjects: projects.value.filter(p => p.status === 'IN_PROGRESS').length,
    myTasks: me.value ? tasks.value.filter(t => t.assigneeId === me.value!.id).length : 0,
    pendingMessages: notifications.value.filter(n => !n.isRead).length,
    dueThisWeek: tasks.value.filter(t => t.dueDate && t.status !== 'DONE' && new Date(t.dueDate).getTime() <= weekFromNow).length,
  }
})
const recentTasks = computed(() =>
  [...tasks.value].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5)
)
const pendingMessages = computed(() => messages.value.slice(0, 3))
const activeProjects = computed(() =>
  projects.value.filter(p => p.status === 'IN_PROGRESS' || p.status === 'NOT_STARTED').slice(0, 5)
)
const projectUpdates = computed(() => messages.value.slice(0, 3))

// ---------- Admin ----------
const adminStats = computed(() => {
  const totalCost = projects.value.reduce((sum, p) => sum + ((p as any).totalCost ?? 0), 0)
  return {
    totalClients: companies.value.length,
    activeProjects: projects.value.filter(p => p.status === 'IN_PROGRESS').length,
    totalRevenue: totalCost,
    projectedRevenue: totalCost,
  }
})
const recentActivity = computed(() => auditLogs.value.slice(0, 8))

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
            ? 'Your tasks, active projects, and recent messages.'
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
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
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
              <p class="text-sm text-gray-600">Unread Messages</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ clientStats.unreadMessages }}</p>
            </div>
            <div class="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-comment-dots text-green-600 text-xl"></i>
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
              <div class="mt-2 w-full bg-gray-200 rounded-full h-2">
                <div class="bg-emerald-600 h-2 rounded-full transition-all" :style="{ width: project.progress + '%' }" />
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
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
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
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Total Tasks</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ tasks.length }}</p>
            </div>
            <div class="w-12 h-12 bg-yellow-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-tasks text-yellow-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Unread Messages</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ userStats.pendingMessages }}</p>
            </div>
            <div class="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-comment-dots text-green-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Due This Week</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ userStats.dueThisWeek }}</p>
            </div>
            <div class="w-12 h-12 bg-red-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-clock text-red-600 text-xl"></i>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Recent Tasks</h2>
            <RouterLink to="/tasks" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">View all</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div v-if="recentTasks.length === 0" class="p-6 text-sm text-gray-500">No tasks yet.</div>
            <div v-for="task in recentTasks" :key="task.id" class="p-6 hover:bg-gray-50 transition-colors">
              <div class="flex items-center justify-between mb-2">
                <h3 class="font-medium text-gray-900">{{ task.title }}</h3>
                <span :class="['px-2 py-1 text-xs font-medium rounded-full', PRIORITY_COLORS[task.priorityLabel]]">
                  {{ task.priorityLabel }}
                </span>
              </div>
              <p class="text-sm text-gray-600">{{ task.projectName }}</p>
              <p class="text-sm text-gray-500 mt-1">
                Due: {{ formatDate(task.dueDate) }} &middot;
                <span :class="['font-medium', TASK_STATUS_COLORS[task.statusLabel]]">{{ task.statusLabel }}</span>
                <template v-if="task.assigneeName"> &middot; {{ task.assigneeName }}</template>
              </p>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Recent Messages</h2>
            <RouterLink to="/messages" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">Open inbox</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div v-if="pendingMessages.length === 0" class="p-6 text-sm text-gray-500">No messages yet.</div>
            <div v-for="msg in pendingMessages" :key="msg.id" class="p-6 hover:bg-gray-50 transition-colors">
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
              <div class="mt-2 w-full bg-gray-200 rounded-full h-2">
                <div class="bg-emerald-600 h-2 rounded-full transition-all" :style="{ width: project.progress + '%' }" />
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
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
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
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Contract Value</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ formatPhpCompact(adminStats.totalRevenue) }}</p>
            </div>
            <div class="w-12 h-12 bg-yellow-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-coins text-yellow-600 text-xl"></i>
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
              <span class="px-2 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">REACHABLE</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Authenticated User</span>
              <span class="text-sm text-gray-600">{{ me?.fullName || '—' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Role</span>
              <span class="text-sm text-gray-600">{{ me?.role || '—' }}</span>
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
