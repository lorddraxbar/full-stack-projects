<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useRole } from '../composables/useRole'
import { useGetNotifications, useMarkNotificationRead, useMarkAllNotificationsRead } from '../services/api'

const { role } = useRole()
const router = useRouter()

const allNavItems = [
  { name: 'Dashboard', path: '/dashboard', icon: 'fas fa-chart-bar', roles: ['CLIENT', 'USER', 'ADMIN'] },
  { name: 'Projects', path: '/projects', icon: 'fas fa-folder', roles: ['CLIENT', 'USER', 'ADMIN'] },
  { name: 'Tasks', path: '/tasks', icon: 'fas fa-check-square', roles: ['USER', 'ADMIN'] },
  { name: 'Documents', path: '/documents', icon: 'fas fa-file-alt', roles: ['CLIENT', 'USER', 'ADMIN'] },
  { name: 'Messages', path: '/messages', icon: 'fas fa-comment-dots', roles: ['CLIENT', 'USER', 'ADMIN'] },
  { name: 'Announcements', path: '/announcements', icon: 'fas fa-bullhorn', roles: ['CLIENT', 'USER', 'ADMIN'] },
  { name: 'Reviews', path: '/reviews', icon: 'fas fa-star', roles: ['USER', 'ADMIN'] },
]

const navItems = computed(() =>
  allNavItems.filter(item => item.roles.includes(role.value))
)

const isSidebarOpen = ref(true)
const isMobile = ref(false)
const isUserMenuOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)
const route = useRoute()

// --- Notifications (live from the backend; refreshed on a light interval) ---
interface Notif {
  id: number
  title: string
  body: string
  type: string
  entityType: string | null
  entityId: number | null
  isRead: boolean
  createdAt: string
}
const notifications = ref<Notif[]>([])
const isNotifOpen = ref(false)
const notifRef = ref<HTMLElement | null>(null)
const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)

const notifRoute = (n: Notif) => {
  switch (n.entityType) {
    case 'Announcement': return '/announcements'
    case 'Task': return '/tasks'
    case 'Message': return '/messages'
    case 'Project': return '/projects'
    default: return '/dashboard'
  }
}

const timeAgo = (iso: string) => {
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return 'just now'
  if (min < 60) return `${min}m ago`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}h ago`
  const day = Math.floor(hr / 24)
  if (day < 7) return `${day}d ago`
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

async function loadNotifications() {
  try {
    const res = await useGetNotifications()
    notifications.value = Array.isArray(res) ? (res as Notif[]) : []
  } catch {
    notifications.value = []
  }
}

async function openNotif(n: Notif) {
  if (!n.isRead) {
    n.isRead = true // optimistic; the PATCH below makes it stick
    try { await useMarkNotificationRead(n.id) } catch { /* keep the local state */ }
  }
  isNotifOpen.value = false
  router.push(notifRoute(n))
}

async function markAllNotifsRead() {
  try {
    await useMarkAllNotificationsRead()
    notifications.value.forEach(n => n.isRead = true)
  } catch { /* best effort */ }
}

let notifTimer: number | null = null

const userName = computed(() => localStorage.getItem('userName') || 'User')
const userInitial = computed(() => userName.value.charAt(0))

const MOBILE_BREAKPOINT = 768

const checkMobile = () => {
  const wasMobile = isMobile.value
  isMobile.value = window.innerWidth < MOBILE_BREAKPOINT

  // Only adjust sidebar state when crossing the breakpoint threshold
  if (isMobile.value && !wasMobile) {
    isSidebarOpen.value = false
  } else if (!isMobile.value && wasMobile) {
    isSidebarOpen.value = true
  }
}

const onDocumentClick = (e: MouseEvent) => {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target as Node)) {
    isUserMenuOpen.value = false
  }
  if (notifRef.value && !notifRef.value.contains(e.target as Node)) {
    isNotifOpen.value = false
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  document.addEventListener('click', onDocumentClick)
  loadNotifications()
  notifTimer = window.setInterval(loadNotifications, 60000)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  document.removeEventListener('click', onDocumentClick)
  if (notifTimer !== null) window.clearInterval(notifTimer)
})

const isActive = (path: string) => {
  return route.path === path || route.path.startsWith(path + '/')
}

const roleLabel = computed(() =>
  role.value === 'CLIENT' ? 'Client' : role.value === 'USER' ? 'User' : 'Admin'
)

const logout = () => {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userRole')
  localStorage.removeItem('userName')
  localStorage.removeItem('userId')
  router.push('/auth/login')
}
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Mobile overlay -->
    <div
      v-if="isMobile && isSidebarOpen"
      class="fixed inset-0 bg-black/35 z-20"
      @click="isSidebarOpen = false"
    />

    <!-- Sidebar -->
    <aside
      :class="[
        'fixed left-0 top-0 h-full bg-white border-r border-gray-200 transition-all duration-300 z-30 overflow-hidden',
        isSidebarOpen ? 'w-64' : 'w-16',
        isMobile && !isSidebarOpen ? '-translate-x-full' : '',
      ]"
    >
      <!-- Logo -->
      <div class="h-16 flex items-center justify-center border-b border-gray-200">
        <h1 class="text-lg font-bold text-emerald-600" :class="{ 'hidden': !isSidebarOpen }">
          SECPhils
        </h1>
      </div>

      <!-- Navigation -->
      <nav class="mt-4">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="[
            'flex items-center px-4 py-3 text-gray-700 hover:bg-emerald-50 hover:text-emerald-600 transition-colors',
            isActive(item.path) ? 'bg-emerald-50 text-emerald-600 border-r-2 border-emerald-600' : '',
            !isSidebarOpen && !isMobile ? 'justify-center' : '',
          ]"
          @click="isMobile && (isSidebarOpen = false)"
        >
          <i :class="item.icon" class="text-xl w-6 text-center" />
          <span v-if="isSidebarOpen" class="ml-3 text-sm font-medium">{{ item.name }}</span>
        </RouterLink>
      </nav>
    </aside>

    <!-- Main content -->
    <div :class="[
      isMobile ? '' : (isSidebarOpen ? 'ml-64' : 'ml-16'),
      isMobile && isSidebarOpen ? 'ml-64' : '',
    ]">
      <!-- Header -->
      <header class="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-4 sticky top-0 z-10">
        <!-- Toggle button -->
        <button
          @click="isSidebarOpen = !isSidebarOpen"
          class="p-2 rounded-lg hover:bg-gray-100 transition-colors"
        >
          <i class="fas fa-bars text-lg" />
        </button>

        <!-- Right side -->
        <div class="flex items-center gap-4">
          <!-- Notification bell (live: unread count from /notifications, 60s poll) -->
          <div class="relative" ref="notifRef">
            <button
              class="p-2 rounded-lg hover:bg-gray-100 transition-colors relative text-gray-500"
              @click="isNotifOpen = !isNotifOpen"
            >
              <i class="fas fa-bell text-lg" />
              <span
                v-if="unreadCount > 0"
                class="absolute top-0.5 right-0.5 min-w-4 h-4 px-1 flex items-center justify-center text-[10px] font-bold text-white bg-red-500 rounded-full"
              >
                {{ unreadCount }}
              </span>
            </button>

            <div
              v-if="isNotifOpen"
              class="absolute right-0 mt-2 w-80 max-h-96 overflow-y-auto bg-white border border-gray-200 rounded-lg shadow-xl z-50"
            >
              <div class="flex items-center justify-between px-4 py-2 border-b border-gray-100">
                <span class="text-sm font-semibold text-gray-900">Notifications</span>
                <button
                  v-if="unreadCount > 0"
                  @click="markAllNotifsRead"
                  class="text-xs text-emerald-600 hover:text-emerald-700"
                >
                  Mark all read
                </button>
              </div>
              <div v-if="notifications.length === 0" class="px-4 py-6 text-center text-sm text-gray-500">
                You're all caught up.
              </div>
              <button
                v-for="n in notifications"
                :key="n.id"
                @click="openNotif(n)"
                :class="['block w-full text-left px-4 py-3 border-b border-gray-50 hover:bg-gray-50 transition-colors',
                  !n.isRead ? 'bg-emerald-50/50' : '']"
              >
                <div class="flex items-start gap-2">
                  <span
                    :class="['mt-1.5 w-1.5 h-1.5 rounded-full shrink-0',
                      !n.isRead ? 'bg-emerald-600' : 'bg-transparent']"
                  />
                  <div class="min-w-0">
                    <p class="text-sm font-medium text-gray-900 truncate">{{ n.title }}</p>
                    <p class="text-xs text-gray-600 line-clamp-2">{{ n.body }}</p>
                    <p class="text-[11px] text-gray-400 mt-1">{{ timeAgo(n.createdAt) }}</p>
                  </div>
                </div>
              </button>
            </div>
          </div>

          <!-- User menu -->
          <div ref="userMenuRef" class="relative flex items-center gap-2">
            <button
              @click="isUserMenuOpen = !isUserMenuOpen"
              class="flex items-center gap-2 rounded-lg px-1.5 py-1 hover:bg-gray-100 transition-colors"
            >
              <div class="w-8 h-8 rounded-full bg-emerald-600 flex items-center justify-center text-white text-sm font-medium">
                {{ userInitial }}
              </div>
              <div class="hidden sm:block text-left">
                <p class="text-sm font-medium text-gray-700 leading-tight">{{ userName }}</p>
                <p class="text-xs text-gray-500 leading-tight">{{ roleLabel }}</p>
              </div>
              <i class="fas fa-chevron-down text-xs text-gray-400" />
            </button>

            <!-- Popup menu -->
            <div
              v-if="isUserMenuOpen"
              class="absolute right-0 top-full mt-2 w-48 bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-50"
            >
              <RouterLink
                to="/settings"
                class="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 hover:text-emerald-600 transition-colors"
                @click="isUserMenuOpen = false"
              >
                <i class="fas fa-cog w-4 text-center" />
                Settings
              </RouterLink>
              <RouterLink
                v-if="role === 'ADMIN'"
                to="/admin"
                class="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 hover:text-emerald-600 transition-colors"
                @click="isUserMenuOpen = false"
              >
                <i class="fas fa-tools w-4 text-center" />
                Admin
              </RouterLink>
              <div class="border-t border-gray-100 my-1" />
              <button
                @click="logout"
                class="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors"
              >
                <i class="fas fa-sign-out-alt w-4 text-center" />
                Logout
              </button>
            </div>
          </div>
        </div>
      </header>

      <!-- Page content -->
      <main class="p-4 sm:p-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
