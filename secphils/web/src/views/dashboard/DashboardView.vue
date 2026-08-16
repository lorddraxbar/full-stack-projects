<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useRole } from '@/composables/useRole'

const { isClient, isProvider, isAdmin } = useRole()
const router = useRouter()

const statusColors: Record<string, string> = {
  'In Progress': 'bg-blue-100 text-blue-800',
  'Not Started': 'bg-gray-100 text-gray-800',
  'Planning': 'bg-yellow-100 text-yellow-800',
  'Completed': 'bg-green-100 text-green-800',
  'On Hold': 'bg-red-100 text-red-800',
}

const priorityColors: Record<string, string> = {
  'High': 'bg-red-100 text-red-800',
  'Medium': 'bg-yellow-100 text-yellow-800',
  'Low': 'bg-green-100 text-green-800',
}

const heading = computed(() =>
  isClient.value ? 'Client Dashboard' : isProvider.value ? 'Provider Dashboard' : 'Admin Dashboard'
)
const subheading = computed(() =>
  isClient.value
    ? 'Your projects and latest updates from your consultants.'
    : isProvider.value
      ? 'Your tasks, active projects, and pending messages.'
      : 'System overview, key metrics, and recent activity.'
)

// ---------- Client data ----------
const clientStats = ref({
  assignedProjects: 3,
  inProgress: 2,
  pendingDocuments: 4,
  unreadMessages: 3,
})

const assignedProjects = ref([
  { id: 1, name: 'Manufacturing Process Optimization', client: 'ABC Manufacturing', status: 'In Progress', progress: 65, dueDate: '2026-09-15', serviceType: 'Process Consulting' },
  { id: 2, name: 'Energy Sector Compliance Audit', client: 'ABC Manufacturing', status: 'Planning', progress: 20, dueDate: '2026-10-01', serviceType: 'Compliance Audit' },
  { id: 3, name: 'Supply Chain Feasibility Study', client: 'ABC Manufacturing', status: 'Completed', progress: 100, dueDate: '2026-08-01', serviceType: 'Feasibility Study' },
])

const latestUpdates = ref([
  { id: 1, date: '2026-08-15', author: 'John Doe (Lead Engineer)', project: 'Manufacturing Process Optimization', text: 'Completed the bottleneck analysis for Line 3. Draft findings shared in the project documents.' },
  { id: 2, date: '2026-08-14', author: 'Jane Smith (Process Analyst)', project: 'Energy Sector Compliance Audit', text: 'Audit scope confirmed. Document request list sent to your team.' },
  { id: 3, date: '2026-08-12', author: 'Alice Brown (Project Manager)', project: 'Supply Chain Feasibility Study', text: 'Final report delivered. Awaiting your sign-off.' },
])

// ---------- Provider data ----------
const providerStats = ref({
  activeProjects: 4,
  myTasks: 7,
  pendingMessages: 5,
  dueThisWeek: 3,
})

const activeProjects = ref([
  { id: 1, name: 'Manufacturing Process Optimization', client: 'ABC Manufacturing', status: 'In Progress', progress: 65, dueDate: '2026-09-15', assignee: 'John Doe' },
  { id: 2, name: 'Water Treatment Plant Design', client: 'Municipal Water Authority', status: 'In Progress', progress: 45, dueDate: '2026-11-30', assignee: 'Jane Smith' },
  { id: 3, name: 'Energy Sector Compliance Audit', client: 'XYZ Energy Corp', status: 'Planning', progress: 20, dueDate: '2026-10-01', assignee: 'Bob Wilson' },
])

const recentTasks = ref([
  { id: 1, title: 'Review process flow diagrams', project: 'Manufacturing Process Optimization', dueDate: '2026-08-20', priority: 'High', status: 'In Progress' },
  { id: 2, title: 'Submit compliance documentation', project: 'Energy Sector Compliance Audit', dueDate: '2026-08-25', priority: 'Medium', status: 'To Do' },
  { id: 3, title: 'Approve final report', project: 'Supply Chain Feasibility Study', dueDate: '2026-08-18', priority: 'Low', status: 'Done' },
])

const pendingMessages = ref([
  { id: 1, project: 'Manufacturing Process Optimization', sender: 'Maria Santos (ABC Manufacturing)', preview: 'Can we schedule a walkthrough of Line 3 this week?', time: '2h ago', unread: 2 },
  { id: 2, project: 'Water Treatment Plant Design', sender: 'City Engineer R. Lim', preview: 'The revised pump specs are attached for review.', time: '1d ago', unread: 1 },
  { id: 3, project: 'Energy Sector Compliance Audit', sender: 'XYZ Energy Corp', preview: 'Please confirm the audit start date.', time: '2d ago', unread: 2 },
])

const projectUpdates = ref([
  { id: 1, date: '2026-08-15', author: 'John Doe', project: 'Manufacturing Process Optimization', text: 'Bottleneck analysis for Line 3 completed.' },
  { id: 2, date: '2026-08-14', author: 'Jane Smith', project: 'Water Treatment Plant Design', text: 'Hydraulic model updated with new pump specs.' },
  { id: 3, date: '2026-08-13', author: 'Bob Wilson', project: 'Energy Sector Compliance Audit', text: 'Site survey scheduled for next Monday.' },
])

// ---------- Admin data ----------
const adminStats = ref({
  totalClients: 24,
  activeProjects: 18,
  totalRevenue: 2400000,
  projectedRevenue: 3200000,
})

const systemHealth = ref({
  backend: 'HEALTHY',
  database: 'HEALTHY',
  lastBackup: '2026-08-15 02:00:00',
})

const recentActivity = ref([
  { id: 1, timestamp: '2026-08-15 10:30', user: 'Jane Smith', action: 'LOGIN', entity: 'USER', details: 'User logged in' },
  { id: 2, timestamp: '2026-08-15 10:25', user: 'John Doe', action: 'CREATE', entity: 'PROJECT', details: 'Created project "Energy Audit"' },
  { id: 3, timestamp: '2026-08-15 10:20', user: 'Jane Smith', action: 'UPDATE', entity: 'DOCUMENT', details: 'Updated document v2.1' },
  { id: 4, timestamp: '2026-08-15 10:15', user: 'Bob Wilson', action: 'DELETE', entity: 'TASK', details: 'Deleted task #45' },
])

const goToProject = (id: number) => router.push(`/projects/${id}`)
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">{{ heading }}</h1>
      <p class="text-gray-600 mt-1">{{ subheading }}</p>
    </div>

    <!-- ================= CLIENT DASHBOARD ================= -->
    <template v-if="isClient">
      <!-- Key Metrics -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Assigned Projects</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ clientStats.assignedProjects }}</p>
            </div>
            <div class="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-folder-open text-blue-600 text-xl"></i>
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
              <p class="text-sm text-gray-600">Pending Documents</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ clientStats.pendingDocuments }}</p>
            </div>
            <div class="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-file-circle-exclamation text-purple-600 text-xl"></i>
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
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Assigned Projects -->
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Assigned Projects</h2>
            <RouterLink to="/projects" class="text-sm text-blue-600 hover:text-blue-700 font-medium">View all</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div
              v-for="project in assignedProjects"
              :key="project.id"
              @click="goToProject(project.id)"
              class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
            >
              <div class="flex items-center justify-between mb-2">
                <h3 class="font-medium text-gray-900">{{ project.name }}</h3>
                <span :class="['px-2 py-1 text-xs font-medium rounded-full', statusColors[project.status]]">
                  {{ project.status }}
                </span>
              </div>
              <div class="flex items-center justify-between text-sm text-gray-600">
                <span>{{ project.serviceType }}</span>
                <span>Due: {{ project.dueDate }}</span>
              </div>
              <div class="mt-2 w-full bg-gray-200 rounded-full h-2">
                <div
                  class="bg-blue-600 h-2 rounded-full transition-all"
                  :style="{ width: project.progress + '%' }"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Latest Updates -->
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200">
            <h2 class="text-lg font-semibold text-gray-900">Latest Updates</h2>
          </div>
          <div class="p-6">
            <div class="relative">
              <div class="absolute left-3 top-1 bottom-1 w-px bg-gray-200" />
              <div
                v-for="update in latestUpdates"
                :key="update.id"
                class="relative pl-10 pb-6 last:pb-0"
              >
                <span class="absolute left-1.5 top-1 w-3 h-3 rounded-full bg-blue-500 ring-4 ring-blue-100" />
                <p class="text-xs text-gray-500">{{ update.date }} &middot; {{ update.author }}</p>
                <p class="text-sm font-medium text-gray-900 mt-0.5">{{ update.project }}</p>
                <p class="text-sm text-gray-600 mt-1">{{ update.text }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ================= PROVIDER DASHBOARD ================= -->
    <template v-if="isProvider">
      <!-- Key Metrics -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Active Projects</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ providerStats.activeProjects }}</p>
            </div>
            <div class="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-folder-open text-blue-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">My Tasks</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ providerStats.myTasks }}</p>
            </div>
            <div class="w-12 h-12 bg-yellow-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-tasks text-yellow-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Pending Messages</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ providerStats.pendingMessages }}</p>
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
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ providerStats.dueThisWeek }}</p>
            </div>
            <div class="w-12 h-12 bg-red-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-clock text-red-600 text-xl"></i>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <!-- Recent Tasks -->
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Recent Tasks</h2>
            <RouterLink to="/tasks" class="text-sm text-blue-600 hover:text-blue-700 font-medium">View all</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div
              v-for="task in recentTasks"
              :key="task.id"
              class="p-6 hover:bg-gray-50 transition-colors"
            >
              <div class="flex items-center justify-between mb-2">
                <h3 class="font-medium text-gray-900">{{ task.title }}</h3>
                <span :class="['px-2 py-1 text-xs font-medium rounded-full', priorityColors[task.priority]]">
                  {{ task.priority }}
                </span>
              </div>
              <p class="text-sm text-gray-600">{{ task.project }}</p>
              <p class="text-sm text-gray-500 mt-1">Due: {{ task.dueDate }} &middot; {{ task.status }}</p>
            </div>
          </div>
        </div>

        <!-- Pending Messages -->
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Pending Messages</h2>
            <RouterLink to="/messages" class="text-sm text-blue-600 hover:text-blue-700 font-medium">Open inbox</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div
              v-for="msg in pendingMessages"
              :key="msg.id"
              class="p-6 hover:bg-gray-50 transition-colors"
            >
              <div class="flex items-center justify-between mb-1">
                <h3 class="font-medium text-gray-900 text-sm">{{ msg.project }}</h3>
                <span class="text-xs text-gray-500">{{ msg.time }}</span>
              </div>
              <p class="text-sm text-gray-700">{{ msg.sender }}: {{ msg.preview }}</p>
              <span class="inline-block mt-2 px-2 py-0.5 bg-blue-100 text-blue-800 text-xs font-medium rounded-full">
                {{ msg.unread }} unread
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Active Projects -->
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Active Projects</h2>
            <RouterLink to="/projects" class="text-sm text-blue-600 hover:text-blue-700 font-medium">View all</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div
              v-for="project in activeProjects"
              :key="project.id"
              @click="goToProject(project.id)"
              class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
            >
              <div class="flex items-center justify-between mb-2">
                <h3 class="font-medium text-gray-900">{{ project.name }}</h3>
                <span :class="['px-2 py-1 text-xs font-medium rounded-full', statusColors[project.status]]">
                  {{ project.status }}
                </span>
              </div>
              <div class="flex items-center justify-between text-sm text-gray-600">
                <span>{{ project.client }}</span>
                <span>Assignee: {{ project.assignee }}</span>
              </div>
              <div class="mt-2 w-full bg-gray-200 rounded-full h-2">
                <div
                  class="bg-blue-600 h-2 rounded-full transition-all"
                  :style="{ width: project.progress + '%' }"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Project Updates -->
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200">
            <h2 class="text-lg font-semibold text-gray-900">Project Updates</h2>
          </div>
          <div class="p-6">
            <div class="relative">
              <div class="absolute left-3 top-1 bottom-1 w-px bg-gray-200" />
              <div
                v-for="update in projectUpdates"
                :key="update.id"
                class="relative pl-10 pb-6 last:pb-0"
              >
                <span class="absolute left-1.5 top-1 w-3 h-3 rounded-full bg-blue-500 ring-4 ring-blue-100" />
                <p class="text-xs text-gray-500">{{ update.date }} &middot; {{ update.author }}</p>
                <p class="text-sm font-medium text-gray-900 mt-0.5">{{ update.project }}</p>
                <p class="text-sm text-gray-600 mt-1">{{ update.text }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ================= ADMIN DASHBOARD ================= -->
    <template v-if="isAdmin">
      <!-- Key Metrics -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Total Clients</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">{{ adminStats.totalClients }}</p>
            </div>
            <div class="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-building text-blue-600 text-xl"></i>
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
              <p class="text-sm text-gray-600">Total Revenue</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">${{ (adminStats.totalRevenue / 1000000).toFixed(1) }}M</p>
            </div>
            <div class="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-coins text-purple-600 text-xl"></i>
            </div>
          </div>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600">Projected Revenue</p>
              <p class="text-2xl font-bold text-gray-900 mt-1">${{ (adminStats.projectedRevenue / 1000000).toFixed(1) }}M</p>
            </div>
            <div class="w-12 h-12 bg-yellow-100 rounded-lg flex items-center justify-center">
              <i class="fas fa-chart-line text-yellow-600 text-xl"></i>
            </div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- System Health -->
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">System Health</h2>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Backend</span>
              <span class="px-2 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">
                {{ systemHealth.backend }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Database</span>
              <span class="px-2 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">
                {{ systemHealth.database }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Last Backup</span>
              <span class="text-sm text-gray-600">{{ systemHealth.lastBackup }}</span>
            </div>
          </div>
        </div>

        <!-- Recent Activity -->
        <div class="bg-white rounded-lg shadow">
          <div class="p-6 border-b border-gray-200 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Recent Activity</h2>
            <RouterLink to="/admin" class="text-sm text-blue-600 hover:text-blue-700 font-medium">Audit logs</RouterLink>
          </div>
          <div class="divide-y divide-gray-200">
            <div
              v-for="log in recentActivity"
              :key="log.id"
              class="p-4 hover:bg-gray-50 transition-colors"
            >
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span class="px-2 py-0.5 bg-blue-100 text-blue-800 text-xs font-medium rounded">{{ log.action }}</span>
                  <span class="text-sm font-medium text-gray-900">{{ log.entity }}</span>
                </div>
                <span class="text-xs text-gray-500">{{ log.timestamp }}</span>
              </div>
              <p class="text-xs text-gray-600 mt-1">{{ log.user }} &mdash; {{ log.details }}</p>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
