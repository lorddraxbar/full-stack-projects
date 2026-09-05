import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { usePortalName } from '../composables/useBrand'

const { portalName } = usePortalName()

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'MarketingLanding',
    component: () => import('@/views/MarketingLandingPage.vue'),
    meta: { public: true, title: 'Strategic Engineering Consultancy - Philippines' },
  },
  {
    path: '/legal/terms',
    name: 'LegalTerms',
    component: () => import('@/views/LegalPage.vue'),
    props: { kind: 'terms' },
    meta: { public: true, title: 'Terms of Service - Strategic Engineering Consultancy' },
  },
  {
    path: '/legal/privacy',
    name: 'LegalPrivacy',
    component: () => import('@/views/LegalPage.vue'),
    props: { kind: 'privacy' },
    meta: { public: true, title: 'Privacy Policy - Strategic Engineering Consultancy' },
  },
  {
    path: '/auth',
    component: () => import('@/layouts/AuthLayout.vue'),
    children: [
      {
        path: 'login',
        name: 'Login',
        component: () => import('@/views/auth/LoginView.vue'),
        meta: { guest: true },
      },
      {
        path: 'set-password',
        name: 'SetPassword',
        component: () => import('@/views/auth/SetPasswordView.vue'),
        meta: { guest: true },
      },
      {
        path: 'sso/callback',
        name: 'SSOCallback',
        component: () => import('@/views/auth/SSOCallbackView.vue'),
        meta: { guest: true },
      },
    ],
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        // Client dashboard removed (2026-09-05) — /dashboard is now staff/admin
        // only; the roles guard below routes CLIENT to their default page.
        meta: { roles: ['USER', 'ADMIN'] },
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/projects/ProjectsView.vue'),
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/projects/ProjectDetailView.vue'),
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('@/views/documents/DocumentsView.vue'),
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/messages/MessagesView.vue'),
      },
      {
        path: 'announcements',
        name: 'Announcements',
        component: () => import('@/views/announcements/AnnouncementsView.vue'),
      },
      {
        path: 'reviews',
        name: 'Reviews',
        component: () => import('@/views/reviews/ReviewsView.vue'),
        // Staff-only (clients submit reviews, they don't moderate): mirror
        // the Reviews entry in MainLayout nav.
        meta: { roles: ['USER', 'ADMIN'] },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
      },
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('@/views/admin/AdminView.vue'),
        meta: { requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Clients have no dashboard anymore — their landing page is /projects.
// Staff (USER) and ADMIN keep /dashboard. Used by every auth-guard bounce.
function homeFor(role: string | null): string {
  return role === 'CLIENT' ? '/projects' : '/dashboard'
}

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('accessToken')
  const userRole = localStorage.getItem('userRole')

  if (to.name === 'MarketingLanding' && token) {
    next({ path: homeFor(userRole) })
  } else if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.meta.guest && token) {
    next({ path: homeFor(userRole) })
  } else if (to.meta.requiresAdmin && userRole !== 'ADMIN') {
    next({ path: homeFor(userRole) })
  } else if (to.meta.roles && (!userRole || !to.meta.roles.includes(userRole))) {
    // Staff-only pages (Reviews, and now Dashboard): a missing or
    // non-matching role lands on the role's default page rather than on
    // a page it shouldn't see.
    next({ path: homeFor(userRole) })
  } else {
    next()
  }
})

router.afterEach((to) => {
  document.title = (to.meta.title as string) || portalName.value
})

export default router
