<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGetUsers, useCreateUser, useDeactivateUser, useActivateUser, useHardDeleteUser, useResendInvite, useGetCompanies, useUpdateUser, useGetSystemSettings, useUpdateSystemSettings } from '../../services/api'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const currentUserId = computed(() => {
  const stored = localStorage.getItem('userId')
  if (stored) return Number(stored)
  return (authStore.user as any)?.id ?? null
})

const activeTab = ref('dashboard')

// ---------- Dashboard ----------
const dashboardStats = ref({
  totalClients: 24,
  activeProjects: 18,
  totalRevenue: 2400000,
  projectedRevenue: 3200000,
  backendStatus: 'HEALTHY',
  databaseStatus: 'HEALTHY',
  lastBackup: '2026-08-15 02:00:00',
})

const auditLogs = ref([
  { id: 1, timestamp: '2026-08-15 10:30:00', user: 'Jane Smith', action: 'LOGIN', entity: 'USER', details: 'User logged in', ipAddress: '192.168.1.100' },
  { id: 2, timestamp: '2026-08-15 10:25:00', user: 'John Doe', action: 'CREATE', entity: 'PROJECT', details: 'Created project "Energy Audit"', ipAddress: '192.168.1.101' },
  { id: 3, timestamp: '2026-08-15 10:20:00', user: 'Jane Smith', action: 'UPDATE', entity: 'DOCUMENT', details: 'Updated document v2.1', ipAddress: '192.168.1.100' },
  { id: 4, timestamp: '2026-08-15 10:15:00', user: 'Bob Wilson', action: 'DELETE', entity: 'TASK', details: 'Deleted task #45', ipAddress: '192.168.1.102' },
])

// ---------- User Management (real API) ----------
interface PortalUser {
  id: number
  email: string
  fullName: string
  role: string
  isActive: boolean
  deactivatedAt: string | null
  lastLogin: string | null
  companyId: number | null
  companyName: string | null
}

const clientUsers = ref<PortalUser[]>([])
const usersLoading = ref(false)
const usersError = ref('')

// Companies (for assigning a client/staff member to a company)
const companies = ref<{ id: number; name: string }[]>([])
const loadCompanies = async () => {
  try {
    const data = await useGetCompanies()
    companies.value = data.map((c: any) => ({ id: c.id, name: c.name }))
  } catch {
    companies.value = []
  }
}

const loadUsers = async () => {
  usersLoading.value = true
  usersError.value = ''
  try {
    const data = await useGetUsers()
    clientUsers.value = data
  } catch (err: any) {
    usersError.value = err.response?.data?.message || 'Failed to load users'
  } finally {
    usersLoading.value = false
  }
}

const addUserForm = ref({ firstName: '', lastName: '', email: '', role: 'CLIENT', companyId: null as number | null })
const addUserSaving = ref(false)
const addUserMessage = ref<{ ok: boolean; text: string } | null>(null)

const addUser = async () => {
  const f = addUserForm.value
  if (!f.firstName.trim() || !f.lastName.trim() || !f.email.trim()) return
  addUserSaving.value = true
  addUserMessage.value = null
  try {
    await useCreateUser({
      firstName: f.firstName.trim(),
      lastName: f.lastName.trim(),
      email: f.email.trim(),
      role: f.role,
      companyId: f.companyId || null,
    })
    addUserMessage.value = { ok: true, text: 'User created. An invite link has been emailed to them — they set their own password on first use.' }
    addUserForm.value = { firstName: '', lastName: '', email: '', role: 'CLIENT', companyId: null }
    await loadUsers()
  } catch (err: any) {
    addUserMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to create user' }
  } finally {
    addUserSaving.value = false
  }
}

const resendInvite = async (user: PortalUser) => {
  try {
    const data = await useResendInvite(user.id)
    alert(data.message || 'Invite link re-sent')
  } catch (err: any) {
    alert(err.response?.data?.message || 'Failed to resend invite')
  }
}

const deactivateUser = async (user: PortalUser) => {
  try {
    await useDeactivateUser(user.id)
    await loadUsers()
  } catch (err: any) {
    alert(err.response?.data?.message || 'Failed to deactivate user')
  }
}

const activateUser = async (user: PortalUser) => {
  try {
    await useActivateUser(user.id)
    await loadUsers()
  } catch (err: any) {
    alert(err.response?.data?.message || 'Failed to activate user')
  }
}

// ---------- Hard delete ----------
const hardDeleteTarget = ref<PortalUser | null>(null)
const hardDeletePassword = ref('')
const hardDeleteBusy = ref(false)
const hardDeleteError = ref('')

const daysDeactivated = (user: PortalUser) => {
  if (!user.deactivatedAt) return 0
  return Math.floor((Date.now() - new Date(user.deactivatedAt).getTime()) / 86400000)
}

const isEligibleForHardDelete = (user: PortalUser) =>
  !user.isActive && daysDeactivated(user) >= 7

const openHardDelete = (user: PortalUser) => {
  hardDeleteTarget.value = user
  hardDeletePassword.value = ''
  hardDeleteError.value = ''
}

const confirmHardDelete = async () => {
  const user = hardDeleteTarget.value
  if (!user) return
  hardDeleteBusy.value = true
  hardDeleteError.value = ''
  try {
    await useHardDeleteUser(user.id, hardDeletePassword.value)
    hardDeleteTarget.value = null
    await loadUsers()
  } catch (err: any) {
    hardDeleteError.value = err.response?.data?.message || 'Failed to delete user'
  } finally {
    hardDeleteBusy.value = false
  }
}

// ---------- Edit User ----------
interface EditForm {
  email: string
  firstName: string
  lastName: string
  role: string
  companyId: number | null
  isActive: boolean
  password: string
}
const editTarget = ref<PortalUser | null>(null)
const editForm = ref<EditForm>({ email: '', firstName: '', lastName: '', role: 'CLIENT', companyId: null, isActive: true, password: '' })
const editSaving = ref(false)
const editError = ref('')
const editMessage = ref('')

const openEdit = (user: PortalUser) => {
  editTarget.value = user
  editForm.value = {
    email: user.email,
    firstName: user.fullName.split(' ')[0] || '',
    lastName: user.fullName.split(' ').slice(1).join(' ') || '',
    role: user.role,
    companyId: user.companyId ?? null,
    isActive: user.isActive,
    password: '',
  }
  editError.value = ''
  editMessage.value = ''
}

const saveEdit = async () => {
  const user = editTarget.value
  if (!user) return
  const f = editForm.value
  if (!f.email.trim() || !f.firstName.trim() || !f.lastName.trim()) return
  editSaving.value = true
  editError.value = ''
  editMessage.value = ''
  try {
    await useUpdateUser(user.id, {
      email: f.email.trim(),
      firstName: f.firstName.trim(),
      lastName: f.lastName.trim(),
      role: f.role,
      companyId: f.companyId,
      isActive: f.isActive,
      ...(f.password.trim() ? { password: f.password.trim() } : {}),
    })
    editMessage.value = 'User updated.'
    await loadUsers()
    editTarget.value = null
  } catch (err: any) {
    editError.value = err.response?.data?.message || 'Failed to update user'
  } finally {
    editSaving.value = false
  }
}

onMounted(() => {
  loadUsers()
  loadCompanies()
  loadSystemSettings()
})

// ---------- Company Settings: Company Profile ----------
const companyProfile = ref({
  name: 'SECPhils Consulting',
  tagline: 'Expertise you can trust',
  description: 'SECPhils provides management consulting, compliance, and engineering services to Philippine enterprises.',
  industrySectors: 'Manufacturing, Energy, Finance, Government',
  headquarters: '60F Tower, 2000 Shaw Boulevard, Makati City, Philippines',
  phone: '+63 2 8888 1234',
  email: 'contact@secphils.com',
  website: 'https://www.secphils.com',
  socialLinks: 'https://www.linkedin.com/company/secphils',
  taxNumber: 'BTW-000-123-456-789',
  bankingDetails: 'BDO · Acct ****4821 · SECPhils Consulting Inc.',
  operationalFields: 'Preferred Language, Work Location, Project Reference Number',
  logo: null as string | null,
  brandPrimary: '#2563eb',
  brandSecondary: '#64748b',
})
const saveCompanyProfile = () => alert('Company profile saved!')

// ---------- Company Settings: Team Management ----------
const staffMembers = ref([
  { id: 1, name: 'John Doe', email: 'john@secphils.com', role: 'Project Manager', status: 'Active', projects: ['Energy Audit', 'ISO 9001 Certification', 'Market Research Study'], permissions: ['viewOwnProjects', 'uploadDocuments'] },
  { id: 2, name: 'Jane Smith', email: 'jane@secphils.com', role: 'Consultant', status: 'Active', projects: ['Financial Modeling'], permissions: ['viewOwnProjects', 'uploadDocuments'] },
  { id: 3, name: 'Bob Wilson', email: 'bob@secphils.com', role: 'Engineer', status: 'Deactivated', projects: [], permissions: ['viewOwnProjects'] },
])
const addStaffForm = ref({ name: '', email: '', role: 'Consultant' })
const addStaffMember = () => {
  if (!addStaffForm.value.name.trim() || !addStaffForm.value.email.trim()) return
  staffMembers.value.push({
    id: Date.now(),
    name: addStaffForm.value.name,
    email: addStaffForm.value.email,
    role: addStaffForm.value.role,
    status: 'Active',
    projects: [],
    permissions: ['viewOwnProjects'],
  })
  addStaffForm.value = { name: '', email: '', role: 'Consultant' }
  alert('Team member added.')
}
const toggleStaffStatus = (m: (typeof staffMembers.value)[0]) => {
  m.status = m.status === 'Active' ? 'Deactivated' : 'Active'
}

// ---------- Company Settings: Role & Permission Management ----------
const roles = ref([
  { id: 1, name: 'Project Manager', userType: 'USER', description: 'Manages assigned projects end-to-end', permissions: ['viewOwnProjects', 'uploadDocuments', 'manageAnnouncements'] },
  { id: 2, name: 'Engineer', userType: 'USER', description: 'Executes technical work on projects', permissions: ['viewOwnProjects', 'uploadDocuments'] },
  { id: 3, name: 'Client Admin', userType: 'CLIENT', description: 'Manages client company profile and team', permissions: ['viewCompanyProjects', 'inviteTeam', 'uploadDocuments'] },
  { id: 4, name: 'Administrator', userType: 'ADMIN', description: 'Full system access', permissions: ['all'] },
])
const permissionOptions = [
  'viewOwnProjects', 'viewCompanyProjects', 'uploadDocuments', 'manageAnnouncements',
  'inviteTeam', 'manageProjects', 'manageUsers', 'all',
]
const newRoleForm = ref({ name: '', userType: 'USER', description: '', permissions: ['viewOwnProjects'] as string[] })
const createRole = () => {
  if (!newRoleForm.value.name.trim()) return
  roles.value.push({
    id: Date.now(),
    name: newRoleForm.value.name,
    userType: newRoleForm.value.userType,
    description: newRoleForm.value.description,
    permissions: [...newRoleForm.value.permissions],
  })
  newRoleForm.value = { name: '', userType: 'USER', description: '', permissions: ['viewOwnProjects'] }
  alert('Role created.')
}
const toggleRolePermission = (role: { permissions: string[] }, perm: string) => {
  const i = role.permissions.indexOf(perm)
  if (i === -1) role.permissions.push(perm)
  else role.permissions.splice(i, 1)
}

// ---------- Service Catalog ----------
const services = ref([
  { id: 1, name: 'Energy Audit', description: 'Comprehensive facility energy assessment and optimization roadmap.', category: 'Consulting', status: 'Active', rate: 'Php 150,000 flat' },
  { id: 2, name: 'ISO 9001 Certification', description: 'Gap analysis, implementation support, and audit preparation.', category: 'Compliance', status: 'Active', rate: 'Php 1,200 / hr' },
  { id: 3, name: 'Market Research Study', description: 'Market sizing, competitor benchmarking, and entry strategy.', category: 'Research', status: 'Active', rate: 'Php 250,000 flat' },
  { id: 4, name: 'Legacy Systems Review', description: 'Archived. Replaced by Digital Transformation Assessment.', category: 'Consulting', status: 'Archived', rate: '—' },
])
const newServiceForm = ref({ name: '', description: '', category: 'Consulting', rate: '' })
const addService = () => {
  if (!newServiceForm.value.name.trim()) return
  services.value.push({
    id: Date.now(),
    name: newServiceForm.value.name,
    description: newServiceForm.value.description,
    category: newServiceForm.value.category,
    status: 'Active',
    rate: newServiceForm.value.rate || '—',
  })
  newServiceForm.value = { name: '', description: '', category: 'Consulting', rate: '' }
  alert('Service added to catalog.')
}
const toggleServiceStatus = (s: (typeof services.value)[0]) => {
  s.status = s.status === 'Active' ? 'Archived' : 'Active'
}

// ---------- Project Configuration ----------
const workflowSteps = ref(['Not Started', 'In Progress', 'On Hold', 'Completed'])
const newStep = ref('')
const addWorkflowStep = () => {
  if (!newStep.value.trim()) return
  workflowSteps.value.push(newStep.value)
  newStep.value = ''
}
const removeWorkflowStep = (step: string) => {
  workflowSteps.value = workflowSteps.value.filter(s => s !== step)
}

const statusConfig = ref([
  { name: 'Not Started', color: '#9ca3af', description: 'Project created, work not begun' },
  { name: 'In Progress', color: '#2563eb', description: 'Active work underway' },
  { name: 'On Hold', color: '#f59e0b', description: 'Paused pending client or resource availability' },
  { name: 'Completed', color: '#10b981', description: 'All deliverables approved' },
])

const dropdownCategories = ref([
  { name: 'Project Status', values: ['Not Started', 'In Progress', 'On Hold', 'Completed'] },
  { name: 'Document Category', values: ['Deliverable', 'Client-Submitted', 'Requested'] },
  { name: 'Announcement Category', values: ['Project Update', 'Company News', 'Maintenance'] },
  { name: 'Task Status', values: ['To Do', 'In Progress', 'Done'] },
  { name: 'Priority', values: ['Low', 'Medium', 'High'] },
  { name: 'Service Category', values: ['Consulting', 'Compliance', 'Research', 'Engineering'] },
  { name: 'Audience', values: ['Project', 'Company'] },
])
const addDropdownValue = (category: (typeof dropdownCategories.value)[0], value: string) => {
  if (value.trim() && !category.values.includes(value)) {
    category.values.push(value)
  }
}
const removeDropdownValue = (category: (typeof dropdownCategories.value)[0], value: string) => {
  category.values = category.values.filter(v => v !== value)
}
const newDropdownValues: Record<string, string> = {}

// ---------- Reviews & Ratings ----------
const reviews = ref([
  { id: 1, customer: 'ABC Manufacturing', project: 'Energy Audit', rating: 5, title: 'Excellent energy savings', body: 'The audit identified 18% potential savings. Clear reporting and responsive team.', status: 'Approved', approvedDate: '2026-07-28 14:20' },
  { id: 2, customer: 'Globex Philippines', project: 'Market Research Study', rating: 4, title: 'Solid research, good pacing', body: 'Deliverables were thorough. Slight delay on the mid-point report but recovered well.', status: 'Pending', approvedDate: null },
  { id: 3, customer: 'Initech Corp', project: 'ISO 9001 Certification', rating: 5, title: 'Passed first audit attempt', body: 'The implementation support was top-notch. We passed the certification audit with zero major nonconformities.', status: 'Pending', approvedDate: null },
  { id: 4, customer: 'Umbrella Health', project: 'Compliance Review', rating: 2, title: 'Communication gaps', body: 'Final report quality was fine but mid-project communication was lacking.', status: 'Pending', approvedDate: null },
])
const setReviewStatus = (r: (typeof reviews.value)[0], status: string) => {
  r.status = status
  r.approvedDate = status === 'Approved' ? new Date().toISOString().slice(0, 16).replace('T', ' ') : null
}

// ---------- Communication Center ----------
const announcementForm = ref({ title: '', body: '', audience: 'Company' })
const communicationLogs = ref([
  { id: 1, title: 'Quarterly maintenance window', audience: 'Company', date: '2026-08-10', author: 'Jane Smith', channel: 'Email + In-App' },
  { id: 2, title: 'Energy Audit kickoff confirmed', audience: 'Project: Energy Audit', date: '2026-08-05', author: 'John Doe', channel: 'In-App' },
  { id: 3, title: 'New document retention policy', audience: 'Company', date: '2026-07-22', author: 'Jane Smith', channel: 'Email' },
])
const publishAnnouncement = () => {
  if (!announcementForm.value.title.trim() || !announcementForm.value.body.trim()) return
  communicationLogs.value.unshift({
    id: Date.now(),
    title: announcementForm.value.title,
    audience: announcementForm.value.audience,
    date: new Date().toISOString().slice(0, 10),
    author: 'You',
    channel: 'Email + In-App',
  })
  announcementForm.value = { title: '', body: '', audience: 'Company' }
  alert('Announcement published.')
}

// ---------- System Settings (real API) ----------
const systemSettings = ref({
  portalName: 'SECPhils Portal',
  maintenanceMode: false,
  inviteBaseUrl: '',
  securityPolicies: {
    passwordMinLength: 12,
    require2fa: false,
    sessionTimeoutMinutes: 30,
    maxLoginAttempts: 5,
  },
})
const systemSettingsMessage = ref<{ ok: boolean; text: string } | null>(null)

const loadSystemSettings = async () => {
  try {
    const data = await useGetSystemSettings()
    if (data) {
      systemSettings.value.portalName = data.portalName ?? systemSettings.value.portalName
      systemSettings.value.maintenanceMode = !!data.maintenanceMode
      systemSettings.value.inviteBaseUrl = data.inviteBaseUrl ?? ''
    }
  } catch {
    // keep defaults if the settings endpoint is unavailable
  }
}

const saveSystemSettings = async () => {
  systemSettingsMessage.value = null
  try {
    await useUpdateSystemSettings({
      portalName: systemSettings.value.portalName,
      maintenanceMode: systemSettings.value.maintenanceMode,
      inviteBaseUrl: systemSettings.value.inviteBaseUrl.trim(),
    })
    systemSettingsMessage.value = { ok: true, text: 'System settings saved.' }
  } catch (err: any) {
    systemSettingsMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save settings' }
  }
}
const emailTemplates = ref([
  { id: 1, name: 'Welcome Email', subject: 'Welcome to the SECPhils Portal', body: 'Hi {{name}},\n\nYour account is ready. Sign in to view your assigned projects.\n\n— SECPhils Team' },
  { id: 2, name: 'Team Invitation', subject: 'You have been invited to {{company}}', body: 'Hi {{name}},\n\n{{inviter}} has invited you to join {{company}} on the SECPhils Portal.\n\nSetup link: {{setupLink}}\n\n— SECPhils Team' },
  { id: 3, name: 'Project Update', subject: 'Update on {{project}}', body: 'Hi {{name}},\n\nNew update on {{project}}: {{updateText}}\n\n— SECPhils Team' },
])
const integrations = ref([
  { id: 1, name: 'Gmail / Google Workspace', type: 'Email', status: 'Connected', detail: 'notifications@secphils.com' },
  { id: 2, name: 'Slack', type: 'Notifications', status: 'Disconnected', detail: '—' },
  { id: 3, name: 'Microsoft Teams', type: 'Notifications', status: 'Disconnected', detail: '—' },
  { id: 4, name: 'DocuSign', type: 'Documents', status: 'Connected', detail: 'secphils@docusign.net' },
])
const toggleIntegration = (i: (typeof integrations.value)[0]) => {
  i.status = i.status === 'Connected' ? 'Disconnected' : 'Connected'
  i.detail = i.status === 'Connected' ? i.detail === '—' ? 'Connected' : i.detail : '—'
}

// ---------- Tabs ----------
const tabItems = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'users', label: 'Users' },
  { id: 'company', label: 'Company Settings' },
  { id: 'services', label: 'Service Catalog' },
  { id: 'projectConfig', label: 'Project Config' },
  { id: 'reviews', label: 'Reviews' },
  { id: 'communications', label: 'Communications' },
  { id: 'system', label: 'System' },
  { id: 'audit', label: 'Audit Logs' },
]
const isActiveTab = (tab: string) => activeTab.value === tab

const starRating = (n: number) => '★'.repeat(n) + '☆'.repeat(5 - n)
const emailPlaceholderVars = ['name', 'company', 'project', 'inviter', 'setupLink', 'updateText'].map(v => '{{' + v + '}}')
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Admin Panel</h1>
      <p class="text-gray-600 mt-1">System administration and configuration</p>
    </div>

    <!-- Tabs -->
    <div class="border-b border-gray-200 mb-6">
      <nav class="-mb-px flex gap-2 overflow-x-auto">
        <button
          v-for="tab in tabItems"
          :key="tab.id"
          @click="activeTab = tab.id"
          :class="[
            'py-3 px-4 border-b-2 font-medium text-sm whitespace-nowrap transition-colors',
            isActiveTab(tab.id)
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300',
          ]"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- ================= DASHBOARD ================= -->
    <div v-if="isActiveTab('dashboard')" class="space-y-6">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="bg-white rounded-lg shadow p-6">
          <p class="text-sm text-gray-600">Total Clients</p>
          <p class="text-3xl font-bold text-gray-900 mt-2">{{ dashboardStats.totalClients }}</p>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <p class="text-sm text-gray-600">Active Projects</p>
          <p class="text-3xl font-bold text-gray-900 mt-2">{{ dashboardStats.activeProjects }}</p>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <p class="text-sm text-gray-600">Total Revenue</p>
          <p class="text-3xl font-bold text-gray-900 mt-2">${{ (dashboardStats.totalRevenue / 1000000).toFixed(1) }}M</p>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <p class="text-sm text-gray-600">Projected Revenue</p>
          <p class="text-3xl font-bold text-gray-900 mt-2">${{ (dashboardStats.projectedRevenue / 1000000).toFixed(1) }}M</p>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">System Health</h2>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Backend</span>
              <span class="px-2 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">
                {{ dashboardStats.backendStatus }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Database</span>
              <span class="px-2 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">
                {{ dashboardStats.databaseStatus }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Last Backup</span>
              <span class="text-sm text-gray-600">{{ dashboardStats.lastBackup }}</span>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Recent Activity</h2>
          <div class="space-y-3">
            <div
              v-for="log in auditLogs.slice(0, 3)"
              :key="log.id"
              class="flex items-start justify-between py-2 border-b border-gray-100 last:border-0"
            >
              <div>
                <p class="text-sm font-medium text-gray-900">{{ log.action }} {{ log.entity }}</p>
                <p class="text-xs text-gray-600">{{ log.details }}</p>
              </div>
              <span class="text-xs text-gray-500">{{ log.timestamp.split(' ')[1] }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= USERS ================= -->
    <div v-if="isActiveTab('users')" class="space-y-6">
      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900">User Management</h2>
            <p class="text-sm text-gray-600 mt-1">All portal accounts — provider staff, admins, and clients. Create, and deactivate users here.</p>
          </div>
          <button @click="loadUsers" class="text-sm text-blue-600 hover:text-blue-700 font-medium">
            <i class="fas fa-rotate mr-1" />Refresh
          </button>
        </div>
        <div v-if="usersError" class="m-6 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          {{ usersError }}
        </div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Role</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Company</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Last Login</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-if="usersLoading && clientUsers.length === 0">
                <td colspan="7" class="px-6 py-8 text-center text-sm text-gray-500">Loading users…</td>
              </tr>
              <tr v-else-if="clientUsers.length === 0">
                <td colspan="7" class="px-6 py-8 text-center text-sm text-gray-500">No users found.</td>
              </tr>
              <tr v-for="user in clientUsers" :key="user.id" class="hover:bg-gray-50">
                <td class="px-6 py-4">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-sm font-medium">
                      {{ user.fullName.charAt(0) }}
                    </div>
                    <span class="font-medium text-gray-900">{{ user.fullName }}</span>
                  </div>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ user.email }}</td>
                <td class="px-6 py-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-medium rounded',
                    user.role === 'ADMIN' ? 'bg-purple-100 text-purple-800' :
                    user.role === 'USER' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800'
                  ]">{{ user.role }}</span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ user.companyName || '—' }}</td>
                <td class="px-6 py-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-medium rounded-full',
                    user.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                  ]">
                    {{ user.isActive ? 'Active' : 'Deactivated' }}
                  </span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600 whitespace-nowrap">{{ user.lastLogin ? user.lastLogin.replace('T', ' ').slice(0, 16) : 'Never' }}</td>
                <td class="px-6 py-4">
                  <div class="flex items-center gap-3 whitespace-nowrap">
                    <button
                      v-if="user.id !== currentUserId"
                      @click="openEdit(user)"
                      class="text-gray-700 hover:text-gray-900 text-sm font-medium"
                    >
                      Edit
                    </button>
                    <button
                      v-if="user.isActive && user.id !== currentUserId"
                      @click="deactivateUser(user)"
                      class="text-red-600 hover:text-red-700 text-sm font-medium"
                    >
                      Deactivate
                    </button>
                    <button
                      v-if="!user.isActive && user.id !== currentUserId"
                      @click="activateUser(user)"
                      class="text-green-600 hover:text-green-700 text-sm font-medium"
                    >
                      Activate
                    </button>
                    <button
                      v-if="!user.isActive && user.id !== currentUserId"
                      @click="resendInvite(user)"
                      class="text-blue-600 hover:text-blue-700 text-sm font-medium"
                    >
                      Resend Invite
                    </button>
                    <button
                      v-if="user.id !== currentUserId"
                      @click="openHardDelete(user)"
                      class="text-red-700 hover:text-red-800 text-sm font-medium underline decoration-dotted"
                    >
                      Delete
                    </button>
                    <span v-if="user.id === currentUserId" class="text-xs text-gray-400">(you)</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-1">Add User</h2>
        <p class="text-sm text-gray-600 mb-4">Creates a real account and emails the user a <strong>set-your-own-password</strong> link. Choose <strong>ADMIN</strong> for a provider admin, <strong>USER</strong> for provider staff, or <strong>CLIENT</strong> for a client-side account.</p>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">First Name</label>
            <input v-model="addUserForm.firstName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Juan" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
            <input v-model="addUserForm.lastName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Dela Cruz" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input v-model="addUserForm.email" type="email" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="juan@secphils.com" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <select v-model="addUserForm.role" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option value="ADMIN">ADMIN — Provider Admin</option>
              <option value="USER">USER — Provider Staff</option>
              <option value="CLIENT">CLIENT — Client Account</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Company</label>
            <select v-model="addUserForm.companyId" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option :value="null">— No company —</option>
              <option v-for="c in companies" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <p class="text-xs text-gray-500 mt-1">Assign the client's company (or the staff member's company). Leave blank to assign later.</p>
          </div>
        </div>
        <div v-if="addUserMessage" :class="[
          'mt-4 p-3 rounded-lg text-sm',
          addUserMessage.ok ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
        ]">
          {{ addUserMessage.text }}
        </div>
        <div class="mt-4 flex justify-end">
          <button
            @click="addUser"
            :disabled="addUserSaving || !addUserForm.firstName.trim() || !addUserForm.lastName.trim() || !addUserForm.email.trim()"
            class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ addUserSaving ? 'Creating…' : '+ Create User & Send Invite' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= COMPANY SETTINGS ================= -->
    <div v-if="isActiveTab('company')" class="space-y-6">
      <!-- Company Profile -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-2">Company Profile</h2>
        <p class="text-sm text-gray-600 mb-6">Consultancy profile shown across the portal for client-facing information.</p>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Company Name</label>
            <input v-model="companyProfile.name" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Tagline / Slogan</label>
            <input v-model="companyProfile.tagline" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Business Description</label>
            <textarea v-model="companyProfile.description" rows="2" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Industry Sector(s)</label>
            <input v-model="companyProfile.industrySectors" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Headquarters Address</label>
            <input v-model="companyProfile.headquarters" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Phone Number(s)</label>
            <input v-model="companyProfile.phone" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email Addresses</label>
            <input v-model="companyProfile.email" type="email" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Website URL</label>
            <input v-model="companyProfile.website" type="url" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Social Media Links</label>
            <input v-model="companyProfile.socialLinks" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Tax / Registration Number</label>
            <input v-model="companyProfile.taxNumber" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Banking / Payment Details</label>
            <input v-model="companyProfile.bankingDetails" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Operational Data Fields</label>
            <input v-model="companyProfile.operationalFields" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Primary Brand Color</label>
            <input v-model="companyProfile.brandPrimary" type="color" class="w-16 h-9 border border-gray-300 rounded-lg cursor-pointer bg-white" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Secondary Brand Color</label>
            <input v-model="companyProfile.brandSecondary" type="color" class="w-16 h-9 border border-gray-300 rounded-lg cursor-pointer bg-white" />
          </div>
        </div>

        <div class="mt-6 flex justify-end">
          <button @click="saveCompanyProfile" class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium">
            Save Company Profile
          </button>
        </div>
      </div>

      <!-- Team Management -->
      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="text-lg font-semibold text-gray-900">Team Management</h2>
            <p class="text-sm text-gray-600 mt-1">Internal user/staff accounts, roles, and project assignments.</p>
          </div>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Staff Name</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Role</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Assigned Projects</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="member in staffMembers" :key="member.id" class="hover:bg-gray-50">
                <td class="px-6 py-4">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-full bg-indigo-600 flex items-center justify-center text-white text-sm font-medium">
                      {{ member.name.charAt(0) }}
                    </div>
                    <span class="font-medium text-gray-900">{{ member.name }}</span>
                  </div>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ member.email }}</td>
                <td class="px-6 py-4">
                  <span class="px-2 py-1 bg-gray-100 text-gray-800 text-xs font-medium rounded">{{ member.role }}</span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ member.projects.length ? member.projects.join(', ') : '—' }}</td>
                <td class="px-6 py-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-medium rounded-full',
                    member.status === 'Active' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                  ]">
                    {{ member.status }}
                  </span>
                </td>
                <td class="px-6 py-4">
                  <button class="text-blue-600 hover:text-blue-700 text-sm font-medium mr-3">Edit</button>
                  <button @click="toggleStaffStatus(member)" class="text-red-600 hover:text-red-700 text-sm font-medium">
                    {{ member.status === 'Active' ? 'Deactivate' : 'Activate' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="p-6 border-t border-gray-200">
          <h3 class="text-sm font-semibold text-gray-900 mb-3">Add Team Member</h3>
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
              <input v-model="addStaffForm.name" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input v-model="addStaffForm.email" type="email" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
              <select v-model="addStaffForm.role" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option v-for="r in roles" :key="r.id" :value="r.name">{{ r.name }}</option>
              </select>
            </div>
            <div class="flex items-end">
              <button @click="addStaffMember" class="w-full bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
                + Add Member
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Role & Permission Management -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Role &amp; Permission Management</h2>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div v-for="role in roles" :key="role.id" class="border border-gray-200 rounded-lg p-4">
            <div class="flex items-center justify-between mb-2">
              <h3 class="font-semibold text-gray-900">{{ role.name }}</h3>
              <span class="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded">{{ role.userType }}</span>
            </div>
            <p class="text-sm text-gray-600 mb-3">{{ role.description }}</p>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="perm in permissionOptions"
                :key="perm"
                @click="toggleRolePermission(role, perm)"
                :class="[
                  'px-2 py-1 text-xs font-medium rounded-full border transition-colors',
                  role.permissions.includes(perm)
                    ? 'bg-green-100 border-green-300 text-green-800'
                    : 'bg-gray-50 border-gray-200 text-gray-500 hover:bg-gray-100',
                ]"
              >
                {{ perm.replace(/_/g, ' ') }}
              </button>
            </div>
          </div>
        </div>

        <div class="mt-6 pt-6 border-t border-gray-200">
          <h3 class="text-sm font-semibold text-gray-900 mb-3">Create New Role</h3>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Role Name</label>
              <input v-model="newRoleForm.name" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">User Type</label>
              <select v-model="newRoleForm.userType" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option>CLIENT</option>
                <option>USER</option>
                <option>ADMIN</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input v-model="newRoleForm.description" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="perm in permissionOptions"
              :key="'new-' + perm"
              @click="toggleRolePermission(newRoleForm, perm)"
              :class="[
                'px-2 py-1 text-xs font-medium rounded-full border transition-colors',
                newRoleForm.permissions.includes(perm)
                  ? 'bg-green-100 border-green-300 text-green-800'
                  : 'bg-gray-50 border-gray-200 text-gray-500 hover:bg-gray-100',
              ]"
            >
              {{ perm.replace(/_/g, ' ') }}
            </button>
          </div>
          <div class="mt-4 flex justify-end">
            <button @click="createRole" class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
              + Create Role
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= SERVICE CATALOG ================= -->
    <div v-if="isActiveTab('services')" class="space-y-6">
      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Service Catalog</h2>
          <p class="text-sm text-gray-600 mt-1">Services offered to clients. Archived services are hidden from project setup.</p>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Service</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Description</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Price / Rate</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="service in services" :key="service.id" class="hover:bg-gray-50">
                <td class="px-6 py-4 font-medium text-gray-900">{{ service.name }}</td>
                <td class="px-6 py-4 text-sm text-gray-600 max-w-xs">{{ service.description }}</td>
                <td class="px-6 py-4">
                  <span class="px-2 py-1 bg-gray-100 text-gray-800 text-xs font-medium rounded">{{ service.category }}</span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ service.rate }}</td>
                <td class="px-6 py-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-medium rounded-full',
                    service.status === 'Active' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                  ]">
                    {{ service.status }}
                  </span>
                </td>
                <td class="px-6 py-4">
                  <button class="text-blue-600 hover:text-blue-700 text-sm font-medium mr-3">Edit</button>
                  <button @click="toggleServiceStatus(service)" class="text-red-600 hover:text-red-700 text-sm font-medium">
                    {{ service.status === 'Active' ? 'Archive' : 'Restore' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Add Service</h2>
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Service Name</label>
            <input v-model="newServiceForm.name" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <input v-model="newServiceForm.description" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <select v-model="newServiceForm.category" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option>Consulting</option>
              <option>Compliance</option>
              <option>Research</option>
              <option>Engineering</option>
            </select>
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Price / Rate (optional)</label>
            <input v-model="newServiceForm.rate" type="text" placeholder="e.g. Php 1,000 / hr or Php 150,000 flat" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
        </div>
        <div class="mt-4 flex justify-end">
          <button @click="addService" class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
            + Add Service
          </button>
        </div>
      </div>
    </div>

    <!-- ================= PROJECT CONFIG ================= -->
    <div v-if="isActiveTab('projectConfig')" class="space-y-6">
      <!-- Workflow + Status Config -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Workflow Steps</h2>
          <p class="text-sm text-gray-600 mb-4">Standard project workflow sequence.</p>
          <div class="space-y-2">
            <div
              v-for="(step, i) in workflowSteps"
              :key="step"
              class="flex items-center justify-between py-2 px-3 bg-gray-50 rounded"
            >
              <span class="text-sm text-gray-700">
                <span class="text-gray-400 mr-2">{{ i + 1 }}.</span>{{ step }}
              </span>
              <div class="flex gap-2">
                <button
                  v-if="i > 0"
                  @click="() => { workflowSteps.splice(i - 1, 0, workflowSteps.splice(i, 1)[0]) }"
                  class="text-blue-600 hover:text-blue-700 text-xs"
                >
                  <i class="fas fa-arrow-up" />
                </button>
                <button
                  v-if="i < workflowSteps.length - 1"
                  @click="() => { workflowSteps.splice(i + 1, 0, workflowSteps.splice(i, 1)[0]) }"
                  class="text-blue-600 hover:text-blue-700 text-xs"
                >
                  <i class="fas fa-arrow-down" />
                </button>
                <button @click="removeWorkflowStep(step)" class="text-red-600 hover:text-red-700 text-xs">
                  <i class="fas fa-trash" />
                </button>
              </div>
            </div>
          </div>
          <div class="mt-4 flex gap-2">
            <input
              v-model="newStep"
              type="text"
              placeholder="New step name"
              class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
            />
            <button @click="addWorkflowStep" class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
              + Add
            </button>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Project Statuses</h2>
          <p class="text-sm text-gray-600 mb-4">Status labels, colors, and descriptions.</p>
          <div class="space-y-3">
            <div v-for="status in statusConfig" :key="status.name" class="flex items-center gap-3">
              <input v-model="status.color" type="color" class="w-8 h-8 border border-gray-300 rounded cursor-pointer bg-white" />
              <div class="flex-1">
                <input
                  v-model="status.name"
                  type="text"
                  class="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
                />
                <input
                  v-model="status.description"
                  type="text"
                  class="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-xs text-gray-500"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Dropdown Value Management -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-2">Dropdown Value Management</h2>
        <p class="text-sm text-gray-600 mb-6">All static dropdown values used throughout the portal.</p>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div v-for="category in dropdownCategories" :key="category.name" class="border border-gray-200 rounded-lg p-4">
            <h3 class="font-semibold text-gray-900 text-sm mb-3">{{ category.name }}</h3>
            <div class="space-y-2">
              <div v-for="value in category.values" :key="value" class="flex items-center justify-between py-1.5 px-2 bg-gray-50 rounded">
                <span class="text-sm text-gray-700">{{ value }}</span>
                <button @click="removeDropdownValue(category, value)" class="text-red-500 hover:text-red-700 text-xs">
                  <i class="fas fa-times" />
                </button>
              </div>
            </div>
            <div class="mt-3 flex gap-2">
              <input
                :value="newDropdownValues[category.name] || ''"
                @input="newDropdownValues[category.name] = ($event.target as HTMLInputElement).value"
                @keyup.enter="addDropdownValue(category, newDropdownValues[category.name] || ''); newDropdownValues[category.name] = ''"
                type="text"
                placeholder="Add value"
                class="flex-1 px-2 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
              />
              <button
                @click="addDropdownValue(category, newDropdownValues[category.name] || ''); newDropdownValues[category.name] = ''"
                class="bg-blue-50 text-blue-600 px-3 py-1.5 rounded-lg hover:bg-blue-100 transition-colors text-xs font-medium"
              >
                + Add
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= REVIEWS ================= -->
    <div v-if="isActiveTab('reviews')" class="bg-white rounded-lg shadow">
      <div class="p-6 border-b border-gray-200">
        <h2 class="text-lg font-semibold text-gray-900">Reviews &amp; Ratings</h2>
        <p class="text-sm text-gray-600 mt-1">Moderate client reviews before they are published.</p>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Customer</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Project</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Rating</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Review</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Approved Date</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr v-for="review in reviews" :key="review.id" class="hover:bg-gray-50 align-top">
              <td class="px-6 py-4 font-medium text-gray-900">{{ review.customer }}</td>
              <td class="px-6 py-4 text-sm text-gray-600">{{ review.project }}</td>
              <td class="px-6 py-4 text-amber-500 text-sm whitespace-nowrap">{{ starRating(review.rating) }}</td>
              <td class="px-6 py-4 max-w-xs">
                <p class="text-sm font-medium text-gray-900">{{ review.title }}</p>
                <p class="text-sm text-gray-600 mt-1">{{ review.body }}</p>
              </td>
              <td class="px-6 py-4">
                <span :class="[
                  'px-2 py-1 text-xs font-medium rounded-full',
                  review.status === 'Approved' ? 'bg-green-100 text-green-800'
                  : review.status === 'Rejected' ? 'bg-red-100 text-red-800'
                  : 'bg-yellow-100 text-yellow-800'
                ]">
                  {{ review.status }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-600 whitespace-nowrap">{{ review.approvedDate || '—' }}</td>
              <td class="px-6 py-4 whitespace-nowrap">
                <template v-if="review.status === 'Pending'">
                  <button @click="setReviewStatus(review, 'Approved')" class="text-green-600 hover:text-green-700 text-sm font-medium mr-3">Approve</button>
                  <button @click="setReviewStatus(review, 'Rejected')" class="text-red-600 hover:text-red-700 text-sm font-medium">Reject</button>
                </template>
                <template v-else>
                  <button @click="setReviewStatus(review, 'Pending')" class="text-blue-600 hover:text-blue-700 text-sm font-medium">Re-open</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ================= COMMUNICATION CENTER ================= -->
    <div v-if="isActiveTab('communications')" class="space-y-6">
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Publish Announcement</h2>
        <div class="space-y-4 max-w-2xl">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Title</label>
            <input v-model="announcementForm.title" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Body</label>
            <textarea v-model="announcementForm.body" rows="4" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Audience</label>
            <select v-model="announcementForm.audience" class="w-full max-w-xs px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option>Company</option>
              <option>Project: Energy Audit</option>
              <option>Project: ISO 9001 Certification</option>
              <option>Project: Market Research Study</option>
            </select>
          </div>
          <div class="flex justify-end">
            <button @click="publishAnnouncement" class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium">
              <i class="fas fa-bullhorn mr-1" /> Publish
            </button>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Communication Logs</h2>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Title</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Audience</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Author</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Channel</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="log in communicationLogs" :key="log.id" class="hover:bg-gray-50">
                <td class="px-6 py-4 text-sm text-gray-600 whitespace-nowrap">{{ log.date }}</td>
                <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ log.title }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ log.audience }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ log.author }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ log.channel }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- ================= SYSTEM SETTINGS ================= -->
    <div v-if="isActiveTab('system')" class="space-y-6">
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-6">General &amp; Security</h2>
        <div class="space-y-6">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Portal Name</label>
            <input
              v-model="systemSettings.portalName"
              type="text"
              class="w-full max-w-md px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Invite Link Base URL</label>
            <input
              v-model="systemSettings.inviteBaseUrl"
              type="url"
              placeholder="https://portal.secphils.com"
              class="w-full max-w-md px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p class="text-xs text-gray-500 mt-1">
              The host used in the "Set your password" link sent to invited users. Leave blank to use the URL the admin is currently on (dynamic).
            </p>
          </div>

          <div class="flex items-center gap-2">
            <input
              id="maintenance"
              v-model="systemSettings.maintenanceMode"
              type="checkbox"
              class="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label for="maintenance" class="text-sm text-gray-700">Enable Maintenance Mode</label>
          </div>

          <div class="border-t border-gray-200 pt-6">
            <h3 class="font-medium text-gray-900 mb-4">Security Policies</h3>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-lg">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Min Password Length</label>
                <input
                  v-model="systemSettings.securityPolicies.passwordMinLength"
                  type="number"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Session Timeout (min)</label>
                <input
                  v-model="systemSettings.securityPolicies.sessionTimeoutMinutes"
                  type="number"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Max Login Attempts</label>
                <input
                  v-model="systemSettings.securityPolicies.maxLoginAttempts"
                  type="number"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div class="flex items-center gap-2 mt-6">
                <input
                  id="require2fa"
                  v-model="systemSettings.securityPolicies.require2fa"
                  type="checkbox"
                  class="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                />
                <label for="require2fa" class="text-sm text-gray-700">Require 2FA</label>
              </div>
            </div>
          </div>

          <div v-if="systemSettingsMessage" :class="[
            'p-3 rounded-lg text-sm',
            systemSettingsMessage.ok ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
          ]">
            {{ systemSettingsMessage.text }}
          </div>

          <div class="flex justify-end">
            <button @click="saveSystemSettings" class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium">
              Save Settings
            </button>
          </div>
        </div>
      </div>

      <!-- Email Templates -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Email Templates</h2>
        <p class="text-sm text-gray-600 mb-4">Available variables: {{ emailPlaceholderVars.join('  ') }}</p>
        <div class="space-y-4">
          <div v-for="template in emailTemplates" :key="template.id" class="border border-gray-200 rounded-lg p-4">
            <input
              v-model="template.name"
              type="text"
              class="font-semibold text-gray-900 w-full px-2 py-1 border-b border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500 rounded-t-lg bg-transparent mb-2"
            />
            <input
              v-model="template.subject"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm mb-2"
              placeholder="Subject"
            />
            <textarea
              v-model="template.body"
              rows="4"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm font-mono"
            />
          </div>
        </div>
        <div class="mt-4 flex justify-end">
          <button @click="saveSystemSettings" class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
            Save Templates
          </button>
        </div>
      </div>

      <!-- Integrations -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Integrations</h2>
        <div class="space-y-3">
          <div v-for="integration in integrations" :key="integration.id" class="flex items-center justify-between py-3 px-4 border border-gray-200 rounded-lg">
            <div>
              <p class="font-medium text-gray-900">{{ integration.name }}</p>
              <p class="text-sm text-gray-500">{{ integration.type }} · {{ integration.detail }}</p>
            </div>
            <button
              @click="toggleIntegration(integration)"
              :class="[
                'px-4 py-1.5 text-sm font-medium rounded-lg transition-colors',
                integration.status === 'Connected'
                  ? 'bg-red-50 text-red-600 hover:bg-red-100'
                  : 'bg-green-50 text-green-700 hover:bg-green-100',
              ]"
            >
              {{ integration.status === 'Connected' ? 'Disconnect' : 'Connect' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= AUDIT LOGS ================= -->
    <div v-if="isActiveTab('audit')" class="bg-white rounded-lg shadow overflow-hidden">
      <div class="p-6 border-b border-gray-200">
        <h2 class="text-lg font-semibold text-gray-900">Audit Logs</h2>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Timestamp</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">User</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Action</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entity</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Details</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">IP Address</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr
              v-for="log in auditLogs"
              :key="log.id"
              class="hover:bg-gray-50"
            >
              <td class="px-6 py-4 text-sm text-gray-600">{{ log.timestamp }}</td>
              <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ log.user }}</td>
              <td class="px-6 py-4">
                <span class="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded">
                  {{ log.action }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-600">{{ log.entity }}</td>
              <td class="px-6 py-4 text-sm text-gray-600">{{ log.details }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ log.ipAddress }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Hard delete confirmation modal -->
    <div v-if="hardDeleteTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h3 class="text-lg font-semibold text-gray-900">
          <i class="fas fa-triangle-exclamation text-red-600 mr-2" />Delete {{ hardDeleteTarget.fullName }} permanently?
        </h3>
        <p class="mt-2 text-sm text-gray-600">
          This removes <strong>{{ hardDeleteTarget.email }}</strong> from the database. This action cannot be undone.
        </p>
        <div v-if="hardDeleteTarget.isActive" class="mt-3 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
          This user is still <strong>active</strong> — no 7-day deactivation period applies. Enter your admin password to delete immediately.
        </div>
        <div v-else-if="!isEligibleForHardDelete(hardDeleteTarget)" class="mt-3 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
          Deactivated only {{ daysDeactivated(hardDeleteTarget) }} day(s) ago — the 7-day window has not elapsed. Enter your admin password to delete immediately.
        </div>
        <div v-else class="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          Deactivated {{ daysDeactivated(hardDeleteTarget) }} days ago — eligible for permanent deletion.
        </div>
        <div class="mt-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Your admin password</label>
          <input
            v-model="hardDeletePassword"
            type="password"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
            placeholder="Confirm with your password"
            @keyup.enter="confirmHardDelete"
          />
          <div v-if="hardDeleteError" class="mt-2 text-sm text-red-600">{{ hardDeleteError }}</div>
        </div>
        <div class="mt-5 flex justify-end gap-3">
          <button
            @click="hardDeleteTarget = null"
            class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            @click="confirmHardDelete"
            :disabled="hardDeleteBusy || !hardDeletePassword"
            class="px-4 py-2 rounded-lg bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ hardDeleteBusy ? 'Deleting…' : 'Delete Permanently' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Edit user modal -->
    <div v-if="editTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h3 class="text-lg font-semibold text-gray-900">
          <i class="fas fa-pen text-blue-600 mr-2" />Edit {{ editTarget.fullName }}
        </h3>
        <p class="mt-1 text-sm text-gray-500">{{ editTarget.email }}</p>
        <div class="mt-4 space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">First Name</label>
              <input v-model="editForm.firstName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
              <input v-model="editForm.lastName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input v-model="editForm.email" type="email" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
              <select v-model="editForm.role" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="ADMIN">ADMIN — Provider Admin</option>
                <option value="USER">USER — Provider Staff</option>
                <option value="CLIENT">CLIENT — Client Account</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Company</label>
              <select v-model="editForm.companyId" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option :value="null">— No company —</option>
                <option v-for="c in companies" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">New Password (optional)</label>
            <input v-model="editForm.password" type="password" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Leave blank to keep current" />
          </div>
          <div class="flex items-center gap-2">
            <input
              id="editIsActive"
              v-model="editForm.isActive"
              type="checkbox"
              class="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label for="editIsActive" class="text-sm text-gray-700">Account is active</label>
          </div>
          <div v-if="editError" class="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{{ editError }}</div>
          <div v-if="editMessage" class="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-700">{{ editMessage }}</div>
        </div>
        <div class="mt-5 flex justify-end gap-3">
          <button
            @click="editTarget = null"
            class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50"
          >
            Close
          </button>
          <button
            @click="saveEdit"
            :disabled="editSaving || !editForm.firstName.trim() || !editForm.lastName.trim() || !editForm.email.trim()"
            class="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ editSaving ? 'Saving…' : 'Save Changes' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
