<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import router from '../router'
import { useRole, type UserRole } from '../composables/useRole'

const { role, isPreview, setRole } = useRole()

const allNavItems = [
  { name: 'Dashboard', path: '/dashboard', icon: 'fas fa-chart-bar', roles: ['CLIENT', 'PROVIDER', 'ADMIN'] },
  { name: 'Projects', path: '/projects', icon: 'fas fa-folder', roles: ['CLIENT', 'PROVIDER', 'ADMIN'] },
  { name: 'Tasks', path: '/tasks', icon: 'fas fa-check-square', roles: ['PROVIDER', 'ADMIN'] },
  { name: 'Documents', path: '/documents', icon: 'fas fa-file-alt', roles: ['CLIENT', 'PROVIDER', 'ADMIN'] },
  { name: 'Messages', path: '/messages', icon: 'fas fa-comment-dots', roles: ['CLIENT', 'PROVIDER', 'ADMIN'] },
  { name: 'Announcements', path: '/announcements', icon: 'fas fa-bullhorn', roles: ['CLIENT', 'PROVIDER', 'ADMIN'] },
  { name: 'Reviews', path: '/reviews', icon: 'fas fa-star', roles: ['ADMIN'] },
  { name: 'Settings', path: '/settings', icon: 'fas fa-cog', roles: ['CLIENT', 'PROVIDER', 'ADMIN'] },
  { name: 'Admin', path: '/admin', icon: 'fas fa-tools', roles: ['ADMIN'] },
]

const navItems = computed(() =>
  allNavItems.filter(item => item.roles.includes(role.value))
)

const isSidebarOpen = ref(true)
const isMobile = ref(false)
const route = useRoute()

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

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

const isActive = (path: string) => {
  return route.path === path || route.path.startsWith(path + '/')
}

const exitPreview = () => {
  localStorage.removeItem('previewMode')
  localStorage.removeItem('userName')
  localStorage.removeItem('userRole')
  router.push('/auth/login')
}

const roleLabel = computed(() =>
  role.value === 'CLIENT' ? 'Client' : role.value === 'PROVIDER' ? 'Provider' : 'Admin'
)

const switchPreviewRole = (newRole: UserRole) => {
  setRole(newRole)
  router.push('/dashboard')
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
        <h1 class="text-lg font-bold text-blue-600" :class="{ 'hidden': !isSidebarOpen }">
          SecPhils
        </h1>
      </div>

      <!-- Navigation -->
      <nav class="mt-4">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="[
            'flex items-center px-4 py-3 text-gray-700 hover:bg-blue-50 hover:text-blue-600 transition-colors',
            isActive(item.path) ? 'bg-blue-50 text-blue-600 border-r-2 border-blue-600' : '',
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
          <!-- Preview role switcher -->
          <div v-if="isPreview" class="flex items-center gap-2">
            <span class="text-xs text-gray-500 hidden sm:block">Preview as</span>
            <select
              :value="role"
              @change="switchPreviewRole(($event.target as HTMLSelectElement).value as UserRole)"
              class="px-2 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="CLIENT">Client</option>
              <option value="PROVIDER">Provider</option>
              <option value="ADMIN">Admin</option>
            </select>
            <button
              @click="exitPreview"
              class="px-3 py-1.5 bg-red-50 text-red-600 border border-red-200 rounded-lg hover:bg-red-100 transition-colors text-sm font-medium"
            >
              <i class="fas fa-times mr-1" /> Exit Preview
            </button>
          </div>

          <!-- Notification bell -->
          <button class="p-2 rounded-lg hover:bg-gray-100 transition-colors relative">
            <i class="fas fa-bell text-lg" />
            <span class="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full" />
          </button>

          <!-- User menu -->
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-sm font-medium">
              {{ userInitial }}
            </div>
            <div class="hidden sm:block">
              <p class="text-sm font-medium text-gray-700 leading-tight">{{ userName }}</p>
              <p class="text-xs text-gray-500 leading-tight">{{ roleLabel }}</p>
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
