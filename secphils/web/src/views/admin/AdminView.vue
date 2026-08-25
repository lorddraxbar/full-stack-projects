<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGetUsers, useCreateUser, useDeactivateUser, useActivateUser, useHardDeleteUser, useResendInvite, useGetCompanies, useGetCompany, useCreateCompany, useUpdateCompany, useUpdateUser, useGetSystemSettings, useUpdateSystemSettings, useTestStorage, useGetMe, useUpdateMe, useGetRoles, useCreateRole, useUpdateRole, useDeleteRole, useGetPermissions, useGetServices, useCreateService, useUpdateService, useDeactivateService, useActivateService, useHardDeleteService, useGetServiceCategories, useCreateServiceCategory, useUpdateServiceCategory, useDeleteServiceCategory, useGetAdminStats, useGetAuditLogs, useGetAnnouncements, useCreateAnnouncement, useDeleteAnnouncement, useGetProjects, useGetDropdowns, useCreateDropdownCategory, useUpdateDropdownCategory, useDeleteDropdownCategory, useCreateDropdownValue, useUpdateDropdownValue, useDeleteDropdownValue, type DropdownCategoryItem, type DropdownValueItem, type ServiceItem, type ServicePayload, type ServiceCategoryItem, type ServiceCategoryPayload } from '../../services/api'
import { useAuthStore } from '../../stores/auth'
import RowActionsMenu, { type RowAction } from '../../components/RowActionsMenu.vue'

const authStore = useAuthStore()
const currentUserId = computed(() => {
  const stored = localStorage.getItem('userId')
  if (stored) return Number(stored)
  return (authStore.user as any)?.id ?? null
})

// The provider company = the currently-logged-in admin's own company.
// Defaults USER/ADMIN accounts to it on create and edit.
const providerCompanyId = ref<number | null>(null)
const loadProviderCompany = async () => {
  try {
    const me = await useGetMe()
    providerCompanyId.value = (me as any)?.companyId ?? null
  } catch {
    providerCompanyId.value = null
  }
}

const activeTab = ref('dashboard')

// ---------- Dashboard ----------
const dashboardStats = ref({
  totalClients: 0,
  activeProjects: 0,
  completedProjects: 0,
  totalRevenue: 0,
  pendingReviews: 0,
  backendStatus: 'UNKNOWN',
  databaseStatus: 'UNKNOWN',
  lastBackup: '—',
})
const loadDashboard = async () => {
  try {
    const s = await useGetAdminStats()
    dashboardStats.value = {
      totalClients: s.totalClients ?? 0,
      activeProjects: s.activeProjects ?? 0,
      completedProjects: s.completedProjects ?? 0,
      totalRevenue: s.totalRevenue ?? 0,
      pendingReviews: s.pendingReviews ?? 0,
      backendStatus: s.backendStatus || 'UNKNOWN',
      databaseStatus: s.database?.status || 'UNKNOWN',
      lastBackup: s.lastSettingsUpdate || '—',
    }
  } catch {
    // leave defaults on error; health is already UNKNOWN
  }
}

interface AuditLogRow {
  id: number
  timestamp: string
  user: string
  action: string
  entity: string
  details: string
  ipAddress: string
}
const auditLogs = ref<AuditLogRow[]>([])
const auditLoading = ref(false)
const auditSearch = ref('')

// Newest 200 are loaded in one shot; the audit tab filters them client-side
// (the backend filter is exact-match, which is clunky for free search).
const loadAuditLogs = async () => {
  auditLoading.value = true
  try {
    const raw = await useGetAuditLogs({ limit: 200 }) as any[]
    auditLogs.value = (raw || []).map((l) => ({
      id: Number(l.id),
      timestamp: String(l.createdAt ?? ''),
      user: l.userName || l.userId || '—',
      action: l.action || '—',
      entity: l.entityType || '—',
      details: l.details || '',
      ipAddress: l.ipAddress || '—',
    }))
  } catch {
    auditLogs.value = []
  } finally {
    auditLoading.value = false
  }
}

const filteredAuditLogs = computed(() => {
  const q = auditSearch.value.trim().toLowerCase()
  if (!q) return auditLogs.value
  return auditLogs.value.filter((l) =>
    [l.action, l.entity, l.user, l.details, l.ipAddress]
      .some((f) => String(f).toLowerCase().includes(q))
  )
})

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

// User table filter: space-separated terms, all must match (name/email/role/company/status)
const userFilter = ref('')
const filteredUsers = computed(() => {
  const query = userFilter.value.trim().toLowerCase()
  if (!query) return clientUsers.value
  const terms = query.split(/\s+/)
  return clientUsers.value.filter((u) => {
    const haystack = [
      u.fullName,
      u.email,
      u.role,
      u.companyName || '',
      u.isActive ? 'active' : 'deactivated',
    ].join(' ').toLowerCase()
    return terms.every((t) => haystack.includes(t))
  })
})

const addUserForm = ref({ firstName: '', lastName: '', email: '', role: 'CLIENT', companyId: null as number | null })
const addUserSaving = ref(false)
const addUserMessage = ref<{ ok: boolean; text: string } | null>(null)
const showAddUser = ref(false)

const closeAddUser = () => {
  showAddUser.value = false
  addUserMessage.value = null
  addUserForm.value = { firstName: '', lastName: '', email: '', role: 'CLIENT', companyId: null }
}

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
    await loadUsers()
    closeAddUser()
  } catch (err: any) {
    addUserMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to create user' }
  } finally {
    addUserSaving.value = false
  }
}

// Row actions menu for the users table
const userRowActions = (user: PortalUser): RowAction[] => {
  if (user.id === currentUserId.value) return []
  const actions: RowAction[] = [{ label: 'Edit', onClick: () => openEdit(user) }]
  if (user.isActive) {
    actions.push({ label: 'Deactivate', color: 'text-red-600 hover:text-red-700 hover:bg-red-50', onClick: () => deactivateUser(user) })
  } else {
    actions.push({ label: 'Activate', color: 'text-green-600 hover:text-green-700 hover:bg-green-50', onClick: () => activateUser(user) })
    actions.push({ label: 'Resend Invite', color: 'text-emerald-600 hover:text-emerald-700 hover:bg-emerald-50', onClick: () => resendInvite(user) })
  }
  actions.push({ divider: true, label: '', onClick: () => {} })
  actions.push({ label: 'Delete', color: 'text-red-700 hover:text-red-800 hover:bg-red-50', onClick: () => openHardDelete(user) })
  return actions
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

const onEditRoleChange = () => {
  // Company only applies to CLIENT accounts; default USER/ADMIN to the provider company
  editForm.value.companyId = editForm.value.role === 'CLIENT' ? editForm.value.companyId : providerCompanyId.value
}

const onAddRoleChange = () => {
  // Company only applies to CLIENT accounts; default USER/ADMIN to the provider company
  addUserForm.value.companyId = addUserForm.value.role === 'CLIENT' ? addUserForm.value.companyId : providerCompanyId.value
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

onMounted(async () => {
  await loadProviderCompany()
  loadUsers()
  loadCompanies()
  loadSystemSettings()
  loadProviderCompanyProfile()
  loadRoles()
  loadServices()
  loadServiceCategories()
  loadDashboard()
  loadAuditLogs()
  loadAnnouncements()
  loadProjects()
  loadDropdowns()
})

// ---------- Company Settings: Company Profile ----------
const companyProfile = ref({
  name: '',
  tagline: '',
  description: '',
  industrySectors: '',
  headquarters: '',
  phone: '',
  email: '',
  website: '',
  socialLinks: '',
  taxNumber: '',
  bankingDetails: '',
  operationalFields: '',
  logo: null as string | null,
  brandPrimary: '#29ca8e',
  brandSecondary: '#536976',
})
const companyProfileLoaded = ref(false)
const companyProfileError = ref('')
const savingCompanyProfile = ref(false)

// Color scheme presets — each sets primary + secondary brand colors together.
const colorSchemes = [
  { name: 'Emerald', primary: '#29ca8e', secondary: '#536976' },
  { name: 'Indigo', primary: '#4f46f5', secondary: '#1f2d4d' },
  { name: 'Ocean', primary: '#0e7dd1', secondary: '#123a5e' },
  { name: 'Teal', primary: '#0d9488', secondary: '#1f3f44' },
  { name: 'Violet', primary: '#7b5cd6', secondary: '#3a2f63' },
  { name: 'Slate', primary: '#4b7fdb', secondary: '#1f2a38' },
]
const applyColorScheme = (s: { primary: string; secondary: string }) => {
  companyProfile.value.brandPrimary = s.primary
  companyProfile.value.brandSecondary = s.secondary
}
const activeScheme = (s: { primary: string; secondary: string }) =>
  companyProfile.value.brandPrimary?.toLowerCase() === s.primary.toLowerCase() &&
  companyProfile.value.brandSecondary?.toLowerCase() === s.secondary.toLowerCase()

const loadProviderCompanyProfile = async () => {
  companyProfileError.value = ''
  try {
    const cid = providerCompanyId.value
    if (cid) {
      const c: any = await useGetCompany(cid)
      companyProfile.value = {
        name: c.name ?? '',
        tagline: c.tagline ?? '',
        description: c.description ?? '',
        industrySectors: c.industrySectors ?? '',
        headquarters: c.headquarters ?? '',
        phone: c.phone ?? '',
        email: c.email ?? '',
        website: c.website ?? '',
        socialLinks: c.socialLinks ?? '',
        taxNumber: c.taxNumber ?? '',
        bankingDetails: c.bankingDetails ?? '',
        operationalFields: c.operationalFields ?? '',
        logo: c.logoUrl ?? null,
        brandPrimary: c.brandPrimary ?? '#29ca8e',
        brandSecondary: c.brandSecondary ?? '#536976',
      }
    }
  } catch {
    companyProfileError.value = 'Could not load your company profile.'
  } finally {
    companyProfileLoaded.value = true
  }
}

const saveCompanyProfile = async () => {
  if (savingCompanyProfile.value) return
  if (!companyProfile.value.name.trim()) {
    companyProfileError.value = 'Company name is required.'
    return
  }
  savingCompanyProfile.value = true
  companyProfileError.value = ''
  try {
    const payload: Record<string, unknown> = {
      name: companyProfile.value.name,
      location: null,
      owner: null,
      description: companyProfile.value.description || null,
      tagline: companyProfile.value.tagline || null,
      industrySectors: companyProfile.value.industrySectors || null,
      headquarters: companyProfile.value.headquarters || null,
      phone: companyProfile.value.phone || null,
      email: companyProfile.value.email || null,
      website: companyProfile.value.website || null,
      socialLinks: companyProfile.value.socialLinks || null,
      taxNumber: companyProfile.value.taxNumber || null,
      bankingDetails: companyProfile.value.bankingDetails || null,
      operationalFields: companyProfile.value.operationalFields || null,
      brandPrimary: companyProfile.value.brandPrimary || null,
      brandSecondary: companyProfile.value.brandSecondary || null,
      logoUrl: companyProfile.value.logo || null,
      authorizedRepId: null,
    }
    const cid = providerCompanyId.value
    if (cid) {
      const c: any = await useUpdateCompany(cid, payload)
      providerCompanyId.value = c.id
    } else {
      // No provider company yet — create one and link it to the admin's own account.
      const c: any = await useCreateCompany(payload)
      providerCompanyId.value = c.id
      await useUpdateMe({ companyId: c.id })
    }
    await loadProviderCompanyProfile()
  } catch (e: any) {
    companyProfileError.value = e?.response?.data?.message || 'Failed to save company profile.'
  } finally {
    savingCompanyProfile.value = false
  }
}

// ---------- Company Settings: Role & Permission Management ----------
interface RoleItem {
  id: number
  name: string
  description: string
  userType: string
  isSystem: boolean
  permissionIds: number[]
  assignedUserCount: number
}
const roles = ref<RoleItem[]>([])
const permissions = ref<{ id: number; name: string; description: string }[]>([])
const rolesLoading = ref(false)
const rolesError = ref('')

const loadRoles = async () => {
  rolesLoading.value = true
  rolesError.value = ''
  try {
    const [r, p] = await Promise.all([useGetRoles(), useGetPermissions()])
    roles.value = r as RoleItem[]
    permissions.value = p as { id: number; name: string; description: string }[]
  } catch {
    rolesError.value = 'Could not load roles and permissions.'
  } finally {
    rolesLoading.value = false
  }
}

// Table filter (same convention as User Management: space-separated terms, all must match)
const roleFilter = ref('')

const userTypeOptions = ['CLIENT', 'USER', 'ADMIN']
const filteredRoles = computed(() => {
  const query = roleFilter.value.trim().toLowerCase()
  if (!query) return roles.value
  const terms = query.split(/\s+/)
  return roles.value.filter((r) => {
    const hayPerms = permissions.value.filter((p) => r.permissionIds.includes(p.id)).map((p) => p.name)
    const haystack = [
      r.name,
      r.userType,
      r.description || '',
      ...hayPerms,
      r.assignedUserCount > 0 ? `in use ${r.assignedUserCount}` : 'available',
    ].join(' ').toLowerCase()
    return terms.every((t) => haystack.includes(t))
  })
})

// Role modal (shared by Add / Edit)
const showRoleModal = ref(false)
const roleModalMode = ref<'create' | 'edit'>('create')
const roleModalTarget = ref<RoleItem | null>(null)
const roleForm = ref({ name: '', userType: 'USER', description: '', permissionIds: [] as number[] })
const roleSaving = ref(false)
const roleMessage = ref<{ ok: boolean; text: string } | null>(null)

const resetRoleForm = () => {
  roleForm.value = { name: '', userType: 'USER', description: '', permissionIds: [] }
}

const openCreateRole = () => {
  roleModalMode.value = 'create'
  roleModalTarget.value = null
  resetRoleForm()
  roleMessage.value = null
  showRoleModal.value = true
}

const openEditRole = (role: RoleItem) => {
  roleModalMode.value = 'edit'
  roleModalTarget.value = role
  roleForm.value = {
    name: role.name,
    userType: role.userType,
    description: role.description || '',
    permissionIds: [...role.permissionIds],
  }
  roleMessage.value = null
  showRoleModal.value = true
}

const closeRoleModal = () => {
  showRoleModal.value = false
  roleMessage.value = null
  resetRoleForm()
}

const toggleRolePermission = (permId: number) => {
  const i = roleForm.value.permissionIds.indexOf(permId)
  if (i === -1) roleForm.value.permissionIds.push(permId)
  else roleForm.value.permissionIds.splice(i, 1)
}

const saveRole = async () => {
  const f = roleForm.value
  if (roleSaving.value) return
  if (!f.name.trim()) return
  roleSaving.value = true
  roleMessage.value = null
  try {
    const payload = {
      name: f.name.trim(),
      userType: f.userType,
      description: f.description.trim(),
      permissionIds: f.permissionIds,
    }
    if (roleModalMode.value === 'edit' && roleModalTarget.value) {
      await useUpdateRole(roleModalTarget.value.id, payload)
    } else {
      await useCreateRole(payload)
    }
    await loadRoles()
    closeRoleModal()
  } catch (e: any) {
    roleMessage.value = { ok: false, text: e?.response?.data?.message || 'Failed to save role.' }
  } finally {
    roleSaving.value = false
  }
}

const deleteRole = async (role: RoleItem) => {
  if (!confirm(`Delete role "${role.name}"? This cannot be undone.`)) return
  try {
    await useDeleteRole(role.id)
    await loadRoles()
  } catch (e: any) {
    alert(e?.response?.data?.message || 'Failed to delete role.')
  }
}

const roleRowActions = (role: RoleItem): RowAction[] => {
  const inUse = role.assignedUserCount > 0
  const actions: RowAction[] = [{ label: 'Edit', onClick: () => openEditRole(role) }]
  actions.push({ divider: true, label: '', onClick: () => {} })
  if (role.isSystem) {
    actions.push({ label: 'Delete', disabled: true, disabledHint: 'System roles cannot be deleted', onClick: () => {} })
  } else if (inUse) {
    actions.push({
      label: 'Delete',
      disabled: true,
      disabledHint: `In use by ${role.assignedUserCount} account${role.assignedUserCount === 1 ? '' : 's'} — reassign them first`,
      onClick: () => {},
    })
  } else {
    actions.push({ label: 'Delete', color: 'text-red-700 hover:text-red-800 hover:bg-red-50', onClick: () => deleteRole(role) })
  }
  return actions
}

// ---------- Service Catalog (wired to the backend) ----------
const services = ref<ServiceItem[]>([])
const serviceListLoaded = ref(false)
const serviceListError = ref('')

// Service categories are first-class records (service_categories table). Their
// names/icons/order drive the "Our Services" tabs on the landing page.
const serviceCategories = ref<ServiceCategoryItem[]>([])
const categoryListError = ref('')

const loadServices = async () => {
  serviceListError.value = ''
  try {
    services.value = await useGetServices()
  } catch (err: any) {
    serviceListError.value = err.response?.data?.message || 'Failed to load services'
  } finally {
    serviceListLoaded.value = true
  }
}
const loadServiceCategories = async () => {
  categoryListError.value = ''
  try {
    serviceCategories.value = await useGetServiceCategories()
  } catch (err: any) {
    categoryListError.value = err.response?.data?.message || 'Failed to load service categories'
  }
}

// Service form
interface ServiceFormState {
  name: string
  description: string
  categoryId: number | null
  icon: string
  sortOrder: number
}
const showServiceForm = ref(false)
const editingService = ref<ServiceItem | null>(null)
const serviceForm = ref<ServiceFormState>({ name: '', description: '', categoryId: null, icon: 'fa-solid fa-briefcase', sortOrder: 0 })
const serviceFormMessage = ref<{ ok: boolean; text: string } | null>(null)
const serviceFormSaving = ref(false)

const openAddService = () => {
  editingService.value = null
  serviceForm.value = { name: '', description: '', categoryId: serviceCategories.value[0]?.id ?? null, icon: 'fa-solid fa-briefcase', sortOrder: 0 }
  serviceFormMessage.value = null
  showServiceForm.value = true
}
const openEditService = (s: ServiceItem) => {
  editingService.value = s
  serviceForm.value = {
    name: s.name,
    description: s.description || '',
    categoryId: s.categoryId ?? null,
    icon: s.icon || 'fa-solid fa-briefcase',
    sortOrder: s.sortOrder ?? 0,
  }
  serviceFormMessage.value = null
  showServiceForm.value = true
}
const closeServiceForm = () => {
  showServiceForm.value = false
  editingService.value = null
  serviceFormMessage.value = null
}
const saveService = async () => {
  const f = serviceForm.value
  if (!f.name.trim()) return
  serviceFormSaving.value = true
  serviceFormMessage.value = null
  try {
    const payload: ServicePayload = {
      name: f.name.trim(),
      description: (f.description || '').trim(),
      categoryId: f.categoryId ?? undefined,
      icon: f.icon || undefined,
      sortOrder: f.sortOrder ?? 0,
    }
    if (editingService.value) {
      payload.isActive = editingService.value.isActive
      await useUpdateService(editingService.value.id, payload)
    } else {
      payload.isActive = true
      await useCreateService(payload)
    }
    closeServiceForm()
    await loadServices()
  } catch (err: any) {
    serviceFormMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save service' }
  } finally {
    serviceFormSaving.value = false
  }
}
const archiveService = async (s: ServiceItem) => {
  try {
    await useDeactivateService(s.id)
    await loadServices()
  } catch (err: any) {
    alert(err.response?.data?.message || 'Failed to archive service')
  }
}
const restoreService = async (s: ServiceItem) => {
  try {
    await useActivateService(s.id)
    await loadServices()
  } catch (err: any) {
    alert(err.response?.data?.message || 'Failed to restore service')
  }
}

// Hard delete — mirrors the Users Management flow: available after the 7-day
// deactivation window, or immediately with a password-confirmed prompt.
const serviceHardDeleteTarget = ref<ServiceItem | null>(null)
const serviceHardDeletePassword = ref('')
const serviceHardDeleteBusy = ref(false)
const serviceHardDeleteError = ref('')
const daysSinceDeactivated = (s: ServiceItem) => {
  if (!s.deactivatedAt) return 0
  return Math.floor((Date.now() - new Date(s.deactivatedAt).getTime()) / 86400000)
}
const isServiceEligibleForHardDelete = (s: ServiceItem) => s.deactivatedAt != null && daysSinceDeactivated(s) >= 7
const openServiceHardDelete = (s: ServiceItem) => {
  serviceHardDeleteTarget.value = s
  serviceHardDeletePassword.value = ''
  serviceHardDeleteError.value = ''
}
const confirmServiceHardDelete = async () => {
  const s = serviceHardDeleteTarget.value
  if (!s) return
  serviceHardDeleteBusy.value = true
  serviceHardDeleteError.value = ''
  try {
    await useHardDeleteService(s.id, serviceHardDeletePassword.value)
    serviceHardDeleteTarget.value = null
    await loadServices()
    await loadServiceCategories()
  } catch (err: any) {
    serviceHardDeleteError.value = err.response?.data?.message || 'Failed to delete service'
  } finally {
    serviceHardDeleteBusy.value = false
  }
}
// Mirrors the Users Management row menu: Edit, then state-dependent
// Archive/Restore, then Delete (always available — active rows delete
// immediately with the admin's password, archived rows after 7 days).
const serviceRowActions = (s: ServiceItem): RowAction[] => {
  const actions: RowAction[] = [{ label: 'Edit', onClick: () => openEditService(s) }]
  if (s.isActive) {
    actions.push({ label: 'Archive', color: 'text-red-600 hover:text-red-700 hover:bg-red-50', onClick: () => archiveService(s) })
  } else {
    actions.push({ label: 'Restore', color: 'text-green-600 hover:text-green-700 hover:bg-green-50', onClick: () => restoreService(s) })
  }
  actions.push({ divider: true, label: '', onClick: () => {} })
  actions.push({ label: 'Delete', color: 'text-red-700 hover:text-red-800 hover:bg-red-50', onClick: () => openServiceHardDelete(s) })
  return actions
}

// ---------- Service Categories (admin-managed) ----------
interface CategoryFormState {
  name: string
  icon: string
  sortOrder: number
}
const showCategoryForm = ref(false)
const editingCategory = ref<ServiceCategoryItem | null>(null)
const categoryForm = ref<CategoryFormState>({ name: '', icon: 'fa-solid fa-briefcase', sortOrder: 0 })
const categoryFormMessage = ref<{ ok: boolean; text: string } | null>(null)
const categoryFormSaving = ref(false)
const categoryDeleteTarget = ref<ServiceCategoryItem | null>(null)

const openAddCategory = () => {
  editingCategory.value = null
  categoryForm.value = { name: '', icon: 'fa-solid fa-briefcase', sortOrder: (serviceCategories.value.at(-1)?.sortOrder ?? 0) + 1 }
  categoryFormMessage.value = null
  showCategoryForm.value = true
}
const openEditCategory = (c: ServiceCategoryItem) => {
  editingCategory.value = c
  categoryForm.value = { name: c.name, icon: c.icon || 'fa-solid fa-briefcase', sortOrder: c.sortOrder ?? 0 }
  categoryFormMessage.value = null
  showCategoryForm.value = true
}
const closeCategoryForm = () => {
  showCategoryForm.value = false
  editingCategory.value = null
  categoryFormMessage.value = null
}
const saveCategory = async () => {
  const f = categoryForm.value
  if (!f.name.trim()) return
  categoryFormSaving.value = true
  categoryFormMessage.value = null
  try {
    const payload: ServiceCategoryPayload = {
      name: f.name.trim(),
      icon: f.icon || undefined,
      sortOrder: f.sortOrder ?? 0,
    }
    if (editingCategory.value) {
      await useUpdateServiceCategory(editingCategory.value.id, payload)
    } else {
      await useCreateServiceCategory(payload)
    }
    closeCategoryForm()
    await loadServiceCategories()
    await loadServices()
  } catch (err: any) {
    categoryFormMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save category' }
  } finally {
    categoryFormSaving.value = false
  }
}
const deleteCategory = async () => {
  const c = categoryDeleteTarget.value
  if (!c) return
  try {
    await useDeleteServiceCategory(c.id)
    categoryDeleteTarget.value = null
    await loadServiceCategories()
  } catch (err: any) {
    alert(err.response?.data?.message || 'Failed to delete category')
  }
}
const categoryRowActions = (c: ServiceCategoryItem): RowAction[] => {
  const actions: RowAction[] = [{ label: 'Edit', onClick: () => openEditCategory(c) }]
  actions.push({ divider: true, label: '', onClick: () => {} })
  actions.push({
    label: (c.serviceCount ?? 0) > 0 ? 'Delete (blocked)' : 'Delete',
    color: 'text-red-600 hover:text-red-700 hover:bg-red-50',
    onClick: () => {
      if ((c.serviceCount ?? 0) > 0) {
        alert(`Cannot delete "${c.name}": it still has ${c.serviceCount} service(s). Move or delete those first.`)
      } else {
        categoryDeleteTarget.value = c
      }
    },
  })
  return actions
}

// ---------- Project Configuration ----------
// Workflow steps + status config are presentation-only (colors/labels have no
// persistence endpoint) and stay session-scoped.
const workflowSteps = ref(['Not Started', 'In Progress', 'On Hold', 'Completed'])
const newStep = ref('')
const addWorkflowStep = () => {
  if (!newStep.value.trim()) return
  workflowSteps.value.push(newStep.value.trim())
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

// Dropdown categories are persisted via /dropdowns (full CRUD).
const dropdownCategories = ref<DropdownCategoryItem[]>([])
const dropdownLoading = ref(false)
const loadDropdowns = async () => {
  dropdownLoading.value = true
  try {
    dropdownCategories.value = await useGetDropdowns()
  } catch {
    dropdownCategories.value = []
  } finally {
    dropdownLoading.value = false
  }
}
const newDropdownValues: Record<number, string> = {}
const addDropdownCategory = (category: DropdownCategoryItem) => {
  dropdownCategories.value.push(category)
}
const newDropdownCategory = ref({ name: '', description: '' })
const creatingDropdownCategory = ref(false)
const addNewDropdownCategory = async () => {
  const name = newDropdownCategory.value.name.trim()
  if (!name) return
  creatingDropdownCategory.value = true
  try {
    const created = await useCreateDropdownCategory({ name, description: newDropdownCategory.value.description.trim() || undefined })
    addDropdownCategory(created)
    newDropdownCategory.value = { name: '', description: '' }
  } catch (e) {
    alert('Failed to create category: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  } finally {
    creatingDropdownCategory.value = false
  }
}
const removeDropdownCategory = async (category: DropdownCategoryItem) => {
  try {
    await useDeleteDropdownCategory(category.id)
    await loadDropdowns()
  } catch (e) {
    alert('Failed to delete category: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  }
}
const addDropdownValue = async (category: DropdownCategoryItem, value: string) => {
  const v = value.trim()
  if (!v) return
  try {
    await useCreateDropdownValue({ categoryId: category.id, value: v, sortOrder: (category.values?.length ?? 0) })
    await loadDropdowns()
  } catch (e) {
    alert('Failed to add value: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  }
}
const removeDropdownValue = async (_category: DropdownCategoryItem, dv: DropdownValueItem) => {
  try {
    await useDeleteDropdownValue(dv.id)
    await loadDropdowns()
  } catch (e) {
    alert('Failed to delete value: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  }
}
// Inline rename (the "Update" half of full CRUD) — no extra UI state.
const renameDropdownCategory = async (cat: DropdownCategoryItem) => {
  const name = prompt('Rename category', cat.name)?.trim()
  if (!name || name === cat.name) return
  try {
    await useUpdateDropdownCategory(cat.id, { name })
    await loadDropdowns()
  } catch (e) {
    alert('Failed to rename category: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  }
}
const renameDropdownValue = async (v: DropdownValueItem) => {
  const value = prompt('Rename value', v.displayLabel || v.value)?.trim()
  if (!value) return
  try {
    await useUpdateDropdownValue(v.id, { value })
    await loadDropdowns()
  } catch (e) {
    alert('Failed to rename value: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  }
}

// ---------- Communication Center ----------
interface AnnouncementRow {
  id: number
  title: string
  audience: string
  date: string
  author: string
  channel: string
  category: string
  isPublished: boolean
  projectId: number | null
}
const announcementForm = ref({ title: '', body: '', audience: 'COMPANY', category: 'PROJECT_UPDATE', projectId: null as number | null })
const publishing = ref(false)
const communicationLogs = ref<AnnouncementRow[]>([])
const communicationLoading = ref(false)
const projectOptions = ref<{ id: number; name: string }[]>([])

const fmtAnnDate = (s: string | null | undefined) => {
  if (!s) return '—'
  const d = new Date(s)
  return isNaN(d.getTime()) ? String(s).slice(0, 10)
    : d.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
const audienceDisplay = (a: string | null | undefined) => {
  if (a === 'COMPANY') return 'Company'
  if (a === 'PROJECT') return 'Project'
  return a || 'Company'
}

const loadProjects = async () => {
  try {
    const data = (await useGetProjects()) as any[]
    projectOptions.value = (data || []).map((p) => ({ id: Number(p.id), name: p.name || `Project #${p.id}` }))
  } catch {
    projectOptions.value = []
  }
}

const loadAnnouncements = async () => {
  communicationLoading.value = true
  try {
    const raw = (await useGetAnnouncements()) as any[]
    communicationLogs.value = (raw || []).map((a) => ({
      id: Number(a.id),
      title: a.title || '',
      audience: a.audience || 'COMPANY',
      date: fmtAnnDate(a.createdAt),
      author: a.createdByName || '—',
      channel: a.isPublished === false ? 'Draft' : 'Email + In-App',
      category: a.category || '',
      isPublished: a.isPublished !== false,
      projectId: a.projectId ? Number(a.projectId) : null,
    })).sort((x, y) => y.id - x.id)
  } catch {
    communicationLogs.value = []
  } finally {
    communicationLoading.value = false
  }
}

const publishAnnouncement = async () => {
  const { title, body, audience, category, projectId } = announcementForm.value
  if (!title.trim() || !body.trim()) return
  if (audience === 'PROJECT' && !projectId) {
    alert('Please select a project for a project announcement.')
    return
  }
  publishing.value = true
  try {
    await useCreateAnnouncement({
      title: title.trim(),
      body: body.trim(),
      audience,
      category,
      ...(audience === 'PROJECT' ? { projectId } : {}),
      isPublished: true,
    })
    announcementForm.value = { title: '', body: '', audience: 'COMPANY', category: 'PROJECT_UPDATE', projectId: null }
    await loadAnnouncements()
  } catch (e) {
    alert('Failed to publish announcement: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  } finally {
    publishing.value = false
  }
}

const deleteAnnouncement = async (log: AnnouncementRow) => {
  if (!confirm(`Delete announcement "${log.title}"? This cannot be undone.`)) return
  try {
    await useDeleteAnnouncement(log.id)
    await loadAnnouncements()
  } catch (e) {
    alert('Failed to delete announcement: ' + ((e as any)?.response?.data?.message || (e as Error).message))
  }
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
const integrationsMessage = ref<{ ok: boolean; text: string } | null>(null)

// The three JSONB columns are stored in the DB as serialized JSON strings and
// returned verbatim by GET /admin/settings. Parse defensively on load,
// serialize back on save.
const parseJson = <T>(raw: unknown, fallback: T): T => {
  if (typeof raw === 'string' && raw.trim()) {
    try { return JSON.parse(raw) as T } catch { return fallback }
  }
  return fallback
}

// ---------- Object Storage (S3 / S3-compatible) ----------
interface StorageForm {
  provider: string
  region: string
  bucket: string
  accessKey: string
  secretKey: string   // '********' when a secret is already stored (masked by the API)
  endpoint: string
  publicBaseUrl: string
  folder: string
  maxUploadMb: number
}
const DEFAULT_STORAGE: StorageForm = {
  provider: 'AWS S3', region: 'us-east-1', bucket: '', accessKey: '', secretKey: '',
  endpoint: '', publicBaseUrl: '', folder: 'documents', maxUploadMb: 25,
}
const storageForm = ref<StorageForm>({ ...DEFAULT_STORAGE })
const storageMessage = ref<{ ok: boolean; text: string } | null>(null)
const testingStorage = ref(false)
const storageConnected = ref(false)

const loadStorageSettings = (data: any) => {
  const stored = parseJson<any>(data?.storage, null)
  storageForm.value = {
    provider: stored?.provider || DEFAULT_STORAGE.provider,
    region: stored?.region || DEFAULT_STORAGE.region,
    bucket: stored?.bucket || '',
    accessKey: stored?.accessKey || '',
    secretKey: stored?.secretKey || '',
    endpoint: stored?.endpoint || '',
    publicBaseUrl: stored?.publicBaseUrl || '',
    folder: stored?.folder || DEFAULT_STORAGE.folder,
    maxUploadMb: Number(stored?.maxUploadMb) || DEFAULT_STORAGE.maxUploadMb,
  }
  storageConnected.value = !!(stored?.bucket && stored?.accessKey)
}

const storagePayload = () => ({
  provider: storageForm.value.provider,
  region: storageForm.value.region.trim(),
  bucket: storageForm.value.bucket.trim(),
  accessKey: storageForm.value.accessKey.trim(),
  secretKey: storageForm.value.secretKey.trim(),
  endpoint: storageForm.value.endpoint.trim(),
  publicBaseUrl: storageForm.value.publicBaseUrl.trim(),
  folder: storageForm.value.folder.trim(),
  maxUploadMb: Number(storageForm.value.maxUploadMb) || 25,
})

const testStorageConnection = async () => {
  storageMessage.value = null
  testingStorage.value = true
  try {
    const res = await useTestStorage(storagePayload())
    storageMessage.value = { ok: !!res.ok, text: res.message }
    storageConnected.value = !!res.ok
  } catch (err: any) {
    storageMessage.value = { ok: false, text: err.response?.data?.message || 'Connection test failed' }
  } finally {
    testingStorage.value = false
  }
}

const saveStorageSettings = async () => {
  storageMessage.value = null
  try {
    await useUpdateSystemSettings({ storage: JSON.stringify(storagePayload()) })
    storageMessage.value = { ok: true, text: 'Object storage settings saved.' }
    storageConnected.value = !!(storageForm.value.bucket && storageForm.value.accessKey)
  } catch (err: any) {
    storageMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save storage settings' }
  }
}

// ---------- Google Sign-In (SSO) ----------
interface SsoForm {
  enabled: boolean
  clientId: string
  clientSecret: string
  redirectUri: string
  domainRestriction: string
}
const DEFAULT_SSO: SsoForm = {
  enabled: false,
  clientId: '',
  clientSecret: '',
  redirectUri: '',
  domainRestriction: '',
}
const ssoForm = ref<SsoForm>({ ...DEFAULT_SSO })
const ssoMessage = ref<{ ok: boolean; text: string } | null>(null)
const ssoSaving = ref(false)

const loadSsoSettings = (data: any) => {
  const stored = parseJson<any>(data?.googleSso, null)
  ssoForm.value = {
    enabled: !!stored?.enabled,
    clientId: stored?.clientId || '',
    clientSecret: stored?.clientSecret || '',
    redirectUri: stored?.redirectUri || '',
    domainRestriction: stored?.domainRestriction || '',
  }
}

const ssoPayload = (): Record<string, unknown> => ({
  enabled: ssoForm.value.enabled,
  clientId: ssoForm.value.clientId.trim(),
  clientSecret: ssoForm.value.clientSecret.trim(),
  redirectUri: ssoForm.value.redirectUri.trim(),
  domainRestriction: ssoForm.value.domainRestriction.trim(),
})

const saveSsoSettings = async () => {
  ssoMessage.value = null
  if (ssoForm.value.enabled && !ssoForm.value.clientId.trim()) {
    ssoMessage.value = { ok: false, text: 'Client ID is required when Google sign-in is enabled.' }
    return
  }
  if (ssoForm.value.enabled && !ssoForm.value.redirectUri.trim()) {
    ssoMessage.value = { ok: false, text: 'Redirect URI is required when Google sign-in is enabled.' }
    return
  }
  ssoSaving.value = true
  try {
    await useUpdateSystemSettings({ googleSso: JSON.stringify(ssoPayload()) })
    ssoMessage.value = { ok: true, text: 'Google sign-in settings saved.' }
  } catch (err: any) {
    ssoMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save Google sign-in settings' }
  } finally {
    ssoSaving.value = false
  }
}

const loadSystemSettings = async () => {
  try {
    const data: any = await useGetSystemSettings()
    if (!data) return
    systemSettings.value.portalName = data.portalName ?? systemSettings.value.portalName
    systemSettings.value.maintenanceMode = !!data.maintenanceMode
    systemSettings.value.inviteBaseUrl = data.inviteBaseUrl ?? ''
    systemSettings.value.securityPolicies = parseJson<any>(data.securityPolicies, systemSettings.value.securityPolicies)
    emailTemplates.value = parseJson<any[]>(data.emailTemplates, DEFAULT_EMAIL_TEMPLATES.map(t => ({ ...t })))
    integrations.value = parseJson<any[]>(data.integrations, DEFAULT_INTEGRATIONS.map(t => ({ ...t })))
    loadStorageSettings(data)
    loadSsoSettings(data)
  } catch {
    // keep defaults if the settings endpoint is unavailable
  }
}

const saveGeneralSettings = async () => {
  systemSettingsMessage.value = null
  try {
    await useUpdateSystemSettings({
      portalName: systemSettings.value.portalName,
      maintenanceMode: systemSettings.value.maintenanceMode,
      inviteBaseUrl: systemSettings.value.inviteBaseUrl.trim(),
      securityPolicies: JSON.stringify(systemSettings.value.securityPolicies),
    })
    systemSettingsMessage.value = { ok: true, text: 'System settings saved.' }
  } catch (err: any) {
    systemSettingsMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save settings' }
  }
}

const saveEmailTemplates = async () => {
  systemSettingsMessage.value = null
  try {
    await useUpdateSystemSettings({ emailTemplates: JSON.stringify(emailTemplates.value) })
    systemSettingsMessage.value = { ok: true, text: 'Email templates saved.' }
  } catch (err: any) {
    systemSettingsMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save email templates' }
  }
}

const saveIntegrations = async () => {
  integrationsMessage.value = null
  try {
    await useUpdateSystemSettings({ integrations: JSON.stringify(integrations.value) })
    integrationsMessage.value = { ok: true, text: 'Integrations saved.' }
  } catch (err: any) {
    integrationsMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save integrations' }
  }
}

const DEFAULT_EMAIL_TEMPLATES = [
  { id: 1, name: 'Welcome Email', subject: 'Welcome to the SECPhils Portal', body: 'Hi {{name}},\n\nYour account is ready. Sign in to view your assigned projects.\n\n— SECPhils Team' },
  { id: 2, name: 'Team Invitation', subject: 'You have been invited to {{company}}', body: 'Hi {{name}},\n\n{{inviter}} has invited you to join {{company}} on the SECPhils Portal.\n\nSetup link: {{setupLink}}\n\n— SECPhils Team' },
  { id: 3, name: 'Project Update', subject: 'Update on {{project}}', body: 'Hi {{name}},\n\nNew update on {{project}}: {{updateText}}\n\n— SECPhils Team' },
]
const DEFAULT_INTEGRATIONS = [
  { id: 1, name: 'Gmail / Google Workspace', type: 'Email', status: 'Connected', detail: 'notifications@secphils.com' },
  { id: 2, name: 'Slack', type: 'Notifications', status: 'Disconnected', detail: '—' },
  { id: 3, name: 'Microsoft Teams', type: 'Notifications', status: 'Disconnected', detail: '—' },
  { id: 4, name: 'DocuSign', type: 'Documents', status: 'Connected', detail: 'secphils@docusign.net' },
]
const emailTemplates = ref<any[]>(DEFAULT_EMAIL_TEMPLATES.map(t => ({ ...t })))
const integrations = ref<any[]>(DEFAULT_INTEGRATIONS.map(t => ({ ...t })))

// Optimistic toggle + immediate persist; rolls back on failure.
const toggleIntegration = async (i: (typeof integrations.value)[0]) => {
  const prev = { status: i.status, detail: i.detail }
  i.status = i.status === 'Connected' ? 'Disconnected' : 'Connected'
  i.detail = i.status === 'Connected' ? (i.detail === '—' ? 'Connected' : i.detail) : '—'
  integrationsMessage.value = null
  try {
    await useUpdateSystemSettings({ integrations: JSON.stringify(integrations.value) })
    integrationsMessage.value = { ok: true, text: 'Integrations saved.' }
  } catch (err: any) {
    i.status = prev.status
    i.detail = prev.detail
    integrationsMessage.value = { ok: false, text: err.response?.data?.message || 'Failed to save integrations' }
  }
}

// ---------- Tabs ----------
const tabItems = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'users', label: 'Users' },
  { id: 'company', label: 'Company Settings' },
  { id: 'services', label: 'Service Catalog' },
  { id: 'projectConfig', label: 'Project Config' },
  { id: 'communications', label: 'Communications' },
  { id: 'system', label: 'System' },
  { id: 'audit', label: 'Audit Logs' },
]
const isActiveTab = (tab: string) => activeTab.value === tab

const emailPlaceholderVars = ['name', 'company', 'project', 'inviter', 'setupLink', 'updateText'].map(v => '{{' + v + '}}')

// Health badge color by status value.
const healthBadge = (status: string) => {
  const s = (status || '').toUpperCase()
  if (s === 'HEALTHY' || s === 'OK' || s === 'UP') return 'bg-green-100 text-green-800'
  if (s === 'DEGRADED' || s === 'WARNING') return 'bg-amber-100 text-amber-800'
  if (s === 'UNKNOWN') return 'bg-gray-100 text-gray-600'
  return 'bg-red-100 text-red-800'
}
// Safe time-of-day extract (handles "2026-08-15 10:30" or ISO "2026-08-15T10:30").
const timeOfDay = (ts: string) => {
  if (!ts) return ''
  const idx = ts.indexOf(' ') >= 0 ? ts.indexOf(' ') : ts.indexOf('T')
  return idx >= 0 ? ts.slice(idx + 1) : ts
}
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
              ? 'border-emerald-600 text-emerald-600'
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
          <p class="text-sm text-gray-600">Pending Reviews</p>
          <p class="text-3xl font-bold text-gray-900 mt-2">{{ dashboardStats.pendingReviews }}</p>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">System Health</h2>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Backend</span>
              <span :class="['px-2 py-1 text-xs font-medium rounded-full', healthBadge(dashboardStats.backendStatus)]">
                {{ dashboardStats.backendStatus }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Database</span>
              <span :class="['px-2 py-1 text-xs font-medium rounded-full', healthBadge(dashboardStats.databaseStatus)]">
                {{ dashboardStats.databaseStatus }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-gray-700">Last Settings Update</span>
              <span class="text-sm text-gray-600">{{ dashboardStats.lastBackup }}</span>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Recent Activity</h2>
          <div class="space-y-3">
            <p v-if="auditLogs.length === 0" class="text-sm text-gray-500">No recent activity recorded.</p>
            <div
              v-for="log in auditLogs.slice(0, 3)"
              :key="log.id"
              class="flex items-start justify-between py-2 border-b border-gray-100 last:border-0"
            >
              <div>
                <p class="text-sm font-medium text-gray-900">{{ log.action }} {{ log.entity }}</p>
                <p class="text-xs text-gray-600">{{ log.details }}</p>
              </div>
              <span class="text-xs text-gray-500">{{ timeOfDay(log.timestamp) }}</span>
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
          <div class="flex items-center gap-3">
            <button
              @click="showAddUser = true"
              class="px-3 py-1.5 bg-emerald-600 text-white text-sm font-medium rounded-lg hover:bg-emerald-700 transition-colors"
            >
              <i class="fas fa-user-plus mr-1" />Add
            </button>
            <button @click="loadUsers" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">
              <i class="fas fa-rotate mr-1" />Refresh
            </button>
          </div>
        </div>
        <div class="px-6 py-4">
          <div class="relative">
            <i class="fas fa-magnifying-glass absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none"></i>
            <input
              v-model="userFilter"
              type="text"
              placeholder="Filter users — type one or more terms separated by spaces (e.g. admin client acme)"
              class="w-full pl-9 pr-9 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
            <button
              v-if="userFilter"
              @click="userFilter = ''"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              aria-label="Clear filter"
            >
              <i class="fas fa-xmark"></i>
            </button>
          </div>
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
                <th class="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-if="usersLoading && clientUsers.length === 0">
                <td colspan="7" class="px-6 py-8 text-center text-sm text-gray-500">Loading users…</td>
              </tr>
              <tr v-else-if="clientUsers.length === 0">
                <td colspan="7" class="px-6 py-8 text-center text-sm text-gray-500">No users found.</td>
              </tr>
              <tr v-else-if="filteredUsers.length === 0">
                <td colspan="7" class="px-6 py-8 text-center text-sm text-gray-500">No users match your filter.</td>
              </tr>
              <tr v-for="user in filteredUsers" :key="user.id" class="hover:bg-gray-50">
                <td class="px-6 py-4">
                  <div class="flex items-center gap-3">
                    <div class="w-8 h-8 rounded-full bg-emerald-600 flex items-center justify-center text-white text-sm font-medium">
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
                    user.role === 'USER' ? 'bg-emerald-100 text-emerald-800' : 'bg-gray-100 text-gray-800'
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
                <td class="px-3 py-4 text-right whitespace-nowrap">
                  <RowActionsMenu v-if="user.id !== currentUserId" :actions="userRowActions(user)" />
                  <span v-else class="text-xs text-gray-400">(you)</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- ================= COMPANY SETTINGS ================= -->
    <div v-if="isActiveTab('company')" class="space-y-6">
      <!-- Company Profile -->
      <div class="bg-white rounded-lg shadow p-6">
        <div class="flex items-center justify-between gap-3 mb-2">
          <h2 class="text-lg font-semibold text-gray-900">Company Profile</h2>
          <span v-if="!companyProfileLoaded" class="text-xs text-gray-400">Loading profile…</span>
        </div>
        <p class="text-sm text-gray-600 mb-6">Consultancy profile shown across the portal for client-facing information.</p>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Company Name</label>
            <input v-model="companyProfile.name" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Tagline / Slogan</label>
            <input v-model="companyProfile.tagline" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Business Description</label>
            <textarea v-model="companyProfile.description" rows="2" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Headquarters Address</label>
            <input v-model="companyProfile.headquarters" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Phone Number(s)</label>
            <input v-model="companyProfile.phone" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email Addresses</label>
            <input v-model="companyProfile.email" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="one@secphils.com, two@secphils.com" />
            <p class="mt-1 text-xs text-gray-400">Separate multiple addresses with commas. Website contact forms are delivered to all of them.</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Website URL</label>
            <input v-model="companyProfile.website" type="url" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Social Media Links</label>
            <input v-model="companyProfile.socialLinks" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>

          <!-- Color Scheme (brand colors) -->
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Color Scheme</label>
            <p class="text-sm text-gray-500 mb-3">Brand colors shown on your public website and legal pages.</p>

            <div class="flex flex-wrap items-center gap-3">
              <button
                v-for="s in colorSchemes"
                :key="s.name"
                type="button"
                :title="s.name"
                :class="['flex items-center gap-2 px-3 py-1.5 rounded-lg border transition-colors', activeScheme(s) ? 'border-emerald-600 bg-emerald-50' : 'border-gray-300 hover:border-gray-400']"
                @click="applyColorScheme(s)"
              >
                <span class="w-5 h-5 rounded-full" :style="{ background: s.primary }"></span>
                <span class="w-5 h-5 rounded-full" :style="{ background: s.secondary }"></span>
                <span class="text-xs font-medium text-gray-700">{{ s.name }}</span>
              </button>
            </div>

            <div class="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Primary Brand Color</label>
                <div class="flex items-center gap-2">
                  <input v-model="companyProfile.brandPrimary" type="color" class="w-12 h-9 border border-gray-300 rounded-lg cursor-pointer bg-white p-1" />
                  <input v-model="companyProfile.brandPrimary" type="text" class="w-28 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm uppercase" />
                </div>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Secondary Brand Color</label>
                <div class="flex items-center gap-2">
                  <input v-model="companyProfile.brandSecondary" type="color" class="w-12 h-9 border border-gray-300 rounded-lg cursor-pointer bg-white p-1" />
                  <input v-model="companyProfile.brandSecondary" type="text" class="w-28 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm uppercase" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="mt-6 flex items-center justify-between gap-4">
          <p v-if="companyProfileError" class="text-sm text-red-600">{{ companyProfileError }}</p>
          <p v-else-if="!providerCompanyId" class="text-sm text-amber-600">No company on file yet — saving will create one for your organization.</p>
          <button
            @click="saveCompanyProfile"
            :disabled="savingCompanyProfile"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50"
          >
            {{ savingCompanyProfile ? 'Saving…' : 'Save Company Profile' }}
          </button>
        </div>
      </div>

      <!-- Role & Permission Management (mirrors User Management) -->
      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900">Role &amp; Permission Management</h2>
            <p class="text-sm text-gray-600 mt-1">Which permissions each role carries. System roles cannot be deleted and their names are locked.</p>
          </div>
          <div class="flex items-center gap-3">
            <button
              @click="openCreateRole"
              class="px-3 py-1.5 bg-emerald-600 text-white text-sm font-medium rounded-lg hover:bg-emerald-700 transition-colors"
            >
              <i class="fas fa-plus mr-1" />Add Role
            </button>
            <button @click="loadRoles" class="text-sm text-emerald-600 hover:text-emerald-700 font-medium">
              <i class="fas fa-rotate mr-1" />Refresh
            </button>
          </div>
        </div>
        <div class="px-6 py-4">
          <div class="relative">
            <i class="fas fa-magnifying-glass absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none"></i>
            <input
              v-model="roleFilter"
              type="text"
              placeholder="Filter roles — type one or more terms separated by spaces (e.g. admin system)"
              class="w-full pl-9 pr-9 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
            <button
              v-if="roleFilter"
              @click="roleFilter = ''"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              aria-label="Clear filter"
            >
              <i class="fas fa-xmark"></i>
            </button>
          </div>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Role</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">User Type</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Permissions</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-if="rolesLoading && roles.length === 0">
                <td colspan="5" class="px-6 py-8 text-center text-sm text-gray-500">Loading roles…</td>
              </tr>
              <tr v-else-if="rolesError">
                <td colspan="5" class="px-6 py-8 text-center text-sm text-red-600">{{ rolesError }}</td>
              </tr>
              <tr v-else-if="roles.length === 0">
                <td colspan="5" class="px-6 py-8 text-center text-sm text-gray-500">No roles found.</td>
              </tr>
              <tr v-else-if="filteredRoles.length === 0">
                <td colspan="5" class="px-6 py-8 text-center text-sm text-gray-500">No roles match your filter.</td>
              </tr>
              <tr v-for="role in filteredRoles" :key="role.id" class="hover:bg-gray-50 align-top">
                <td class="px-6 py-4">
                  <div class="flex items-center gap-2">
                    <span class="font-medium text-gray-900">{{ role.name }}</span>
                    <span v-if="role.isSystem" class="px-2 py-0.5 bg-purple-100 text-purple-800 text-xs font-medium rounded-full">System</span>
                  </div>
                  <p class="text-xs text-gray-500 mt-1">{{ role.description || '—' }}</p>
                </td>
                <td class="px-6 py-4">
                  <span class="px-2 py-1 bg-emerald-100 text-emerald-800 text-xs font-medium rounded">{{ role.userType }}</span>
                </td>
                <td class="px-6 py-4">
                  <div class="flex flex-wrap gap-1.5 max-w-md">
                    <template v-for="perm in permissions" :key="perm.id">
                      <span
                        v-if="role.permissionIds.includes(perm.id)"
                        :title="perm.description"
                        class="px-2 py-0.5 bg-green-100 border border-green-300 text-green-800 text-xs font-medium rounded-full"
                      >
                        {{ perm.name }}
                      </span>
                    </template>
                    <span v-if="role.permissionIds.length === 0" class="text-xs text-gray-400">No permissions</span>
                  </div>
                </td>
                <td class="px-6 py-4">
                  <span
                    :class="[
                      'px-2 py-0.5 text-xs font-medium rounded-full',
                      role.isSystem
                        ? 'bg-purple-100 text-purple-800'
                        : role.assignedUserCount > 0
                          ? 'bg-amber-100 text-amber-800'
                          : 'bg-green-100 text-green-800',
                    ]"
                  >
                    {{ role.isSystem ? 'System' : role.assignedUserCount > 0 ? `In Use (${role.assignedUserCount})` : 'Available' }}
                  </span>
                </td>
                <td class="px-6 py-4 text-right whitespace-nowrap">
                  <RowActionsMenu :actions="roleRowActions(role)" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Create / Edit Role Modal (mirrors User Management modals) -->
      <div v-if="showRoleModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50" @click.self="closeRoleModal">
        <div class="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 max-h-[85vh] flex flex-col">
          <div class="p-6 border-b border-gray-200">
            <h3 class="text-lg font-semibold text-gray-900">{{ roleModalMode === 'create' ? 'Create New Role' : 'Edit Role' }}</h3>
            <p class="text-sm text-gray-500 mt-1">
              {{ roleModalMode === 'create' ? 'Define a new role and the permissions it carries.' : `Edit "${roleForm.name}" and its permissions.` }}
              <template v-if="roleModalMode === 'edit' && roleModalTarget?.isSystem"> Names on system roles are locked.</template>
            </p>
          </div>
          <div class="p-6 space-y-4 overflow-y-auto">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Role Name <span class="text-red-500">*</span></label>
                <input
                  v-model="roleForm.name"
                  type="text"
                  :disabled="roleModalMode === 'edit' && roleModalTarget?.isSystem"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 disabled:bg-gray-100 disabled:text-gray-500"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">User Type</label>
                <select v-model="roleForm.userType" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500">
                  <option v-for="t in userTypeOptions" :key="t" :value="t">{{ t }}</option>
                </select>
              </div>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <input
                v-model="roleForm.description"
                type="text"
                placeholder="What is this role used for?"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="block text-sm font-medium text-gray-700">Permissions</label>
                <span class="text-xs text-gray-500">{{ roleForm.permissionIds.length }} selected</span>
              </div>
              <div class="border border-gray-200 rounded-lg p-3 flex flex-wrap gap-1.5 max-h-48 overflow-y-auto">
                <button
                  v-for="perm in permissions"
                  :key="perm.id"
                  @click="toggleRolePermission(perm.id)"
                  :title="perm.description"
                  :class="[
                    'px-2 py-0.5 text-xs font-medium rounded-full border transition-colors',
                    roleForm.permissionIds.includes(perm.id)
                      ? 'bg-green-100 border-green-300 text-green-800'
                      : 'bg-gray-50 border-gray-200 text-gray-500 hover:bg-gray-100',
                  ]"
                >
                  {{ perm.name }}
                </button>
              </div>
            </div>
            <div v-if="roleMessage" class="text-sm" :class="roleMessage.ok ? 'text-green-600' : 'text-red-600'">{{ roleMessage.text }}</div>
          </div>
          <div class="p-6 border-t border-gray-200 flex justify-end gap-3">
            <button @click="closeRoleModal" class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button
              @click="saveRole"
              :disabled="roleSaving || !roleForm.name.trim()"
              class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
            >
              {{ roleSaving ? 'Saving…' : roleModalMode === 'create' ? 'Create Role' : 'Save Changes' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= SERVICE CATALOG ================= -->
    <div v-if="isActiveTab('services')" class="space-y-6">
      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900">Service Catalog</h2>
            <p class="text-sm text-gray-600 mt-1">Services offered to clients on the public site. Archived services are hidden from the landing page.</p>
          </div>
          <button
            @click="openAddService"
            class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 transition-colors whitespace-nowrap shrink-0"
          >
            + Add Service
          </button>
        </div>

        <div v-if="serviceListError" class="p-4 mb-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700 mx-5">
          {{ serviceListError }}
        </div>

        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Service</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Description</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th class="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="service in services" :key="service.id" class="hover:bg-gray-50">
                <td class="px-6 py-4">
                  <div class="flex items-center gap-2.5 font-medium text-gray-900">
                    <i v-if="service.icon" :class="service.icon" class="w-4 text-center text-gray-400"></i>
                    {{ service.name }}
                  </div>
                </td>
                <td class="px-6 py-4">
                  <span class="px-2 py-1 bg-gray-100 text-gray-800 text-xs font-medium rounded">{{ service.category }}</span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600 max-w-xs line-clamp-2">{{ service.description || '—' }}</td>
                <td class="px-6 py-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-medium rounded-full',
                    service.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                  ]">
                    {{ service.isActive ? 'Active' : 'Archived' }}
                  </span>
                </td>
                <td class="px-3 py-4 text-right whitespace-nowrap">
                  <RowActionsMenu :actions="serviceRowActions(service)" />
                </td>
              </tr>
              <tr v-if="services.length === 0">
                <td colspan="5" class="px-6 py-10 text-center text-sm text-gray-500">
                  {{ serviceListLoaded ? 'No services in the catalog yet. Use "+ Add Service" to create one.' : 'Loading services…' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Add / Edit Service modal -->
      <div v-if="showServiceForm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
        <div class="bg-white rounded-lg shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto p-6">
          <h3 class="text-lg font-semibold text-gray-900">
            <i class="fas fa-briefcase text-emerald-600 mr-2" />{{ editingService ? 'Edit Service' : 'Add Service' }}
          </h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ editingService
              ? 'Update the service listed under its category on the landing page.'
              : 'Add a service to the catalog. It appears on the landing page under the chosen category tab.' }}
          </p>
          <div class="mt-4 space-y-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Service Name *</label>
              <input v-model="serviceForm.name" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="e.g. Environmental Compliance Certificate (ECC)" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Category</label>
              <div class="relative">
                <select v-model="serviceForm.categoryId" class="w-full pl-3 pr-9 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 appearance-none">
                  <option v-for="c in serviceCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
                <i class="fas fa-chevron-down absolute right-3 top-1/2 -translate-y-1/2 text-xs transition-colors pointer-events-none"></i>
              </div>
              <p class="mt-1 text-xs text-gray-500">The category drives which tab the service shows under on the landing page.</p>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <textarea v-model="serviceForm.description" rows="3" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"></textarea>
              <p class="mt-1 text-xs text-gray-500">Shown under the service name on the landing page. Use multiple lines for longer write-ups.</p>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Icon (Font Awesome)</label>
                <input v-model="serviceForm.icon" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="fa-solid fa-leaf" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Display Order</label>
                <input v-model.number="serviceForm.sortOrder" type="number" min="0" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
              </div>
            </div>
            <div v-if="serviceFormMessage" :class="[
              'p-3 rounded-lg text-sm',
              serviceFormMessage.ok ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
            ]">
              {{ serviceFormMessage.text }}
            </div>
          </div>
          <div class="mt-5 flex justify-end gap-3">
            <button
              @click="closeServiceForm"
              class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50"
            >
              Close
            </button>
            <button
              @click="saveService"
              :disabled="serviceFormSaving || !serviceForm.name.trim()"
              class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ serviceFormSaving ? 'Saving…' : editingService ? 'Save Changes' : 'Add Service' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Service Categories management -->
      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900">Service Categories</h2>
            <p class="text-sm text-gray-600 mt-1">These become the tabs in the "Our Services" section on the landing page. Rename a category to rename its tab; each tab can hold multiple services.</p>
          </div>
          <button
            @click="openAddCategory"
            class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 transition-colors whitespace-nowrap shrink-0"
          >
            + Add Category
          </button>
        </div>

        <div v-if="categoryListError" class="p-4 mb-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700 mx-5">
          {{ categoryListError }}
        </div>

        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Landing Tab Icon</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Display Order</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Services</th>
                <th class="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="cat in serviceCategories" :key="cat.id" class="hover:bg-gray-50">
                <td class="px-6 py-4">
                  <div class="font-medium text-gray-900">{{ cat.name }}</div>
                  <p class="text-xs text-gray-500">Tab: "Our Services → {{ cat.name }}"</p>
                </td>
                <td class="px-6 py-4">
                  <i v-if="cat.icon" :class="cat.icon" class="w-5 text-center text-gray-500"></i>
                  <span v-else class="text-xs text-gray-400">Default</span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-700">{{ cat.sortOrder }}</td>
                <td class="px-6 py-4">
                  <span class="px-2 py-1 bg-gray-100 text-gray-800 text-xs font-medium rounded">{{ cat.serviceCount }}</span>
                </td>
                <td class="px-3 py-4 text-right whitespace-nowrap">
                  <RowActionsMenu :actions="categoryRowActions(cat)" />
                </td>
              </tr>
              <tr v-if="serviceCategories.length === 0">
                <td colspan="5" class="px-6 py-10 text-center text-sm text-gray-500">
                  No service categories yet. Use "+ Add Category" to create the first landing tab.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Add / Edit Category modal -->
    <div v-if="showCategoryForm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-lg p-6">
        <h3 class="text-lg font-semibold text-gray-900">
          <i class="fas fa-layer-group text-emerald-600 mr-2" />{{ editingCategory ? 'Edit Category' : 'Add Category' }}
        </h3>
        <p class="mt-1 text-sm text-gray-500">
          {{ editingCategory
            ? 'Renaming updates the landing "Our Services" tab for every service in this category.'
            : 'Creates a new landing "Our Services" tab. Assign services to it from the Service Catalog.' }}
        </p>
        <div class="mt-4 space-y-3">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Category Name *</label>
            <input v-model="categoryForm.name" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="e.g. Environmental Consulting" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Tab Icon (Font Awesome)</label>
              <input v-model="categoryForm.icon" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="fa-solid fa-leaf" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Display Order</label>
              <input v-model.number="categoryForm.sortOrder" type="number" min="0" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
            </div>
          </div>
          <div v-if="categoryFormMessage" :class="[
            'p-3 rounded-lg text-sm',
            categoryFormMessage.ok ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
          ]">
            {{ categoryFormMessage.text }}
          </div>
        </div>
        <div class="mt-5 flex justify-end gap-3">
          <button
            @click="closeCategoryForm"
            class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            @click="saveCategory"
            :disabled="categoryFormSaving || !categoryForm.name.trim()"
            class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ categoryFormSaving ? 'Saving…' : editingCategory ? 'Save Changes' : 'Add Category' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Hard delete service modal (mirrors Users Management) -->
    <div v-if="serviceHardDeleteTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h3 class="text-lg font-semibold text-gray-900">
          <i class="fas fa-triangle-exclamation text-red-600 mr-2" />Delete {{ serviceHardDeleteTarget.name }} permanently?
        </h3>
        <p class="mt-2 text-sm text-gray-600">
          This removes the service from the database and the landing page. This action cannot be undone.
        </p>
        <div v-if="serviceHardDeleteTarget.isActive" class="mt-3 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
          This service is still <strong>active</strong> — no 7-day deactivation period applies. Enter your admin password to delete immediately.
        </div>
        <div v-else-if="!isServiceEligibleForHardDelete(serviceHardDeleteTarget)" class="mt-3 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
          Deactivated only {{ daysSinceDeactivated(serviceHardDeleteTarget) }} day(s) ago — the 7-day window has not elapsed. Enter your admin password to delete immediately.
        </div>
        <div v-else class="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          Deactivated {{ daysSinceDeactivated(serviceHardDeleteTarget) }} days ago — eligible for permanent deletion.
        </div>
        <div class="mt-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Your admin password</label>
          <input
            v-model="serviceHardDeletePassword"
            type="password"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
            placeholder="Confirm with your password"
            @keyup.enter="confirmServiceHardDelete"
          />
          <div v-if="serviceHardDeleteError" class="mt-2 text-sm text-red-600">{{ serviceHardDeleteError }}</div>
        </div>
        <div class="mt-5 flex justify-end gap-3">
          <button
            @click="serviceHardDeleteTarget = null"
            class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            @click="confirmServiceHardDelete"
            :disabled="serviceHardDeleteBusy || !serviceHardDeletePassword"
            class="px-4 py-2 rounded-lg bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ serviceHardDeleteBusy ? 'Deleting…' : 'Delete Permanently' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Delete category confirmation modal -->
    <div v-if="categoryDeleteTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h3 class="text-lg font-semibold text-gray-900">
          <i class="fas fa-trash text-red-600 mr-2" />Delete "{{ categoryDeleteTarget.name }}"?
        </h3>
        <p class="mt-2 text-sm text-gray-600">
          This category has no services. Deleting it removes its landing tab. This action cannot be undone.
        </p>
        <div class="mt-5 flex justify-end gap-3">
          <button
            @click="categoryDeleteTarget = null"
            class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            @click="deleteCategory"
            class="px-4 py-2 rounded-lg bg-red-600 text-white text-sm font-medium hover:bg-red-700"
          >
            Delete
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
                  class="text-emerald-600 hover:text-emerald-700 text-xs"
                >
                  <i class="fas fa-arrow-up" />
                </button>
                <button
                  v-if="i < workflowSteps.length - 1"
                  @click="() => { workflowSteps.splice(i + 1, 0, workflowSteps.splice(i, 1)[0]) }"
                  class="text-emerald-600 hover:text-emerald-700 text-xs"
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
              class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
            <button @click="addWorkflowStep" class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium">
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
                  class="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                />
                <input
                  v-model="status.description"
                  type="text"
                  class="w-full px-3 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-xs text-gray-500"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Dropdown Value Management -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-2">Dropdown Value Management</h2>
        <p class="text-sm text-gray-600 mb-4">All static dropdown values used throughout the portal.</p>

        <!-- Create category -->
        <div class="flex flex-wrap items-center gap-2 mb-6">
          <input
            v-model="newDropdownCategory.name"
            type="text"
            placeholder="New category name"
            class="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm w-48"
          />
          <input
            v-model="newDropdownCategory.description"
            type="text"
            placeholder="Description (optional)"
            class="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm w-56"
          />
          <button
            :disabled="creatingDropdownCategory"
            @click="addNewDropdownCategory"
            class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
          >
            + Add Category
          </button>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div v-if="dropdownCategories.length === 0" class="col-span-full text-sm text-gray-500">
            {{ dropdownLoading ? 'Loading dropdowns…' : 'No dropdown categories. Add one above.' }}
          </div>
          <div v-for="category in dropdownCategories" :key="category.id" class="border border-gray-200 rounded-lg p-4">
            <div class="flex items-start justify-between mb-1">
              <h3 class="font-semibold text-gray-900 text-sm">{{ category.name }}</h3>
              <div class="flex items-center gap-2">
                <button @click="renameDropdownCategory(category)" class="text-emerald-600 hover:text-emerald-800 text-xs" title="Rename category">
                  <i class="fas fa-pen" />
                </button>
                <button @click="removeDropdownCategory(category)" class="text-red-500 hover:text-red-700 text-xs" title="Delete category">
                  <i class="fas fa-trash" />
                </button>
              </div>
            </div>
            <p v-if="category.description" class="text-xs text-gray-500 mb-2">{{ category.description }}</p>
            <div class="space-y-2 mt-2">
              <div v-if="!category.values || category.values.length === 0" class="text-xs text-gray-400 py-1">No values yet.</div>
              <div v-for="value in category.values" :key="value.id" class="flex items-center justify-between py-1.5 px-2 bg-gray-50 rounded">
                <span class="text-sm text-gray-700">{{ value.displayLabel || value.value }}</span>
                <div class="flex items-center gap-1.5">
                  <button @click="renameDropdownValue(value)" class="text-emerald-600 hover:text-emerald-800 text-xs" title="Rename value">
                    <i class="fas fa-pen" />
                  </button>
                  <button @click="removeDropdownValue(category, value)" class="text-red-500 hover:text-red-700 text-xs" title="Delete value">
                    <i class="fas fa-times" />
                  </button>
                </div>
              </div>
            </div>
            <div class="mt-3 flex gap-2">
              <input
                :value="newDropdownValues[category.id] || ''"
                @input="newDropdownValues[category.id] = ($event.target as HTMLInputElement).value"
                @keyup.enter="addDropdownValue(category, newDropdownValues[category.id] || ''); newDropdownValues[category.id] = ''"
                type="text"
                placeholder="Add value"
                class="flex-1 px-2 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
              />
              <button
                @click="addDropdownValue(category, newDropdownValues[category.id] || ''); newDropdownValues[category.id] = ''"
                class="bg-emerald-50 text-emerald-600 px-3 py-1.5 rounded-lg hover:bg-emerald-100 transition-colors text-xs font-medium"
              >
                + Add
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>


    <!-- ================= COMMUNICATION CENTER ================= -->
    <div v-if="isActiveTab('communications')" class="space-y-6">
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Publish Announcement</h2>
        <div class="space-y-4 max-w-2xl">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Title</label>
            <input v-model="announcementForm.title" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Body</label>
            <textarea v-model="announcementForm.body" rows="4" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Audience</label>
              <select v-model="announcementForm.audience" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value="COMPANY">Company-wide</option>
                <option value="PROJECT">Project</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Category</label>
              <select v-model="announcementForm.category" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value="PROJECT_UPDATE">Project Update</option>
                <option value="COMPANY_NEWS">Company News</option>
                <option value="MAINTENANCE">Maintenance</option>
              </select>
            </div>
          </div>
          <div v-if="announcementForm.audience === 'PROJECT'">
            <label class="block text-sm font-medium text-gray-700 mb-1">Project</label>
            <select v-model="announcementForm.projectId" class="w-full max-w-xs px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option :value="null" disabled>Select a project…</option>
              <option v-for="p in projectOptions" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </div>
          <div class="flex justify-end">
            <button :disabled="publishing" @click="publishAnnouncement" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50">
              <i class="fas fa-bullhorn mr-1" /> {{ publishing ? 'Publishing…' : 'Publish' }}
            </button>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Communication Logs</h2>
        </div>
        <div class="overflow-x-auto">
          <p v-if="communicationLogs.length === 0" class="px-6 py-6 text-sm text-gray-500">
            {{ communicationLoading ? 'Loading announcements…' : 'No announcements yet. Publish one above.' }}
          </p>
          <table v-else class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Title</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Audience</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Author</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Channel</th>
                <th class="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="log in communicationLogs" :key="log.id" class="hover:bg-gray-50">
                <td class="px-6 py-4 text-sm text-gray-600 whitespace-nowrap">{{ log.date }}</td>
                <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ log.title }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">
                  <span :class="['px-2 py-1 text-xs font-medium rounded-full', log.audience === 'COMPANY' ? 'bg-purple-100 text-purple-800' : 'bg-teal-100 text-teal-800']">
                    {{ audienceDisplay(log.audience) }}
                  </span>
                  <span v-if="log.audience === 'PROJECT' && log.projectId" class="ml-1 text-xs text-gray-500">
                    ({{ projectOptions.find(p => p.id === log.projectId)?.name || `Project #${log.projectId}` }})
                  </span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ log.author }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ log.channel }}</td>
                <td class="px-3 py-4 text-right whitespace-nowrap">
                  <button @click="deleteAnnouncement(log)" class="text-red-500 hover:text-red-700 text-xs" title="Delete announcement">
                    <i class="fas fa-trash" />
                  </button>
                </td>
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
              class="w-full max-w-md px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Invite Link Base URL</label>
            <input
              v-model="systemSettings.inviteBaseUrl"
              type="url"
              placeholder="https://portal.secphils.com"
              class="w-full max-w-md px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
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
              class="w-4 h-4 text-emerald-600 border-gray-300 rounded focus:ring-emerald-500"
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
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Session Timeout (min)</label>
                <input
                  v-model="systemSettings.securityPolicies.sessionTimeoutMinutes"
                  type="number"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Max Login Attempts</label>
                <input
                  v-model="systemSettings.securityPolicies.maxLoginAttempts"
                  type="number"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div class="flex items-center gap-2 mt-6">
                <input
                  id="require2fa"
                  v-model="systemSettings.securityPolicies.require2fa"
                  type="checkbox"
                  class="w-4 h-4 text-emerald-600 border-gray-300 rounded focus:ring-emerald-500"
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
            <button @click="saveGeneralSettings" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium">
              Save Settings
            </button>
          </div>
        </div>
      </div>

      <!-- Object Storage (S3 / S3-compatible) -->
      <div class="bg-white rounded-lg shadow p-6">
        <div class="flex items-center justify-between mb-1">
          <h2 class="text-lg font-semibold text-gray-900">Object Storage</h2>
          <span
            class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium"
            :class="storageConnected
              ? 'bg-emerald-100 text-emerald-700'
              : 'bg-amber-100 text-amber-700'"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="storageConnected ? 'bg-emerald-500' : 'bg-amber-500'"></span>
            {{ storageConnected ? 'Configured' : 'Not configured' }}
          </span>
        </div>
        <p class="text-sm text-gray-600 mb-4">
          Document files are stored in an S3 bucket. Leave <em>endpoint</em> empty for AWS S3;
          set it for any S3-compatible service (MinIO, Cloudflare R2, DigitalOcean Spaces).
          The secret key is stored securely and never returned in plaintext — leave it
          unchanged to keep the existing one.
        </p>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Provider</label>
            <select
              v-model="storageForm.provider"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option value="AWS S3">AWS S3</option>
              <option value="MinIO">MinIO</option>
              <option value="Cloudflare R2">Cloudflare R2</option>
              <option value="DigitalOcean Spaces">DigitalOcean Spaces</option>
              <option value="Other">Other (S3-compatible)</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Region</label>
            <input
              v-model="storageForm.region"
              type="text"
              placeholder="us-east-1"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Bucket *</label>
            <input
              v-model="storageForm.bucket"
              type="text"
              placeholder="secphils-documents"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Endpoint</label>
            <input
              v-model="storageForm.endpoint"
              type="text"
              placeholder="https://minio.internal:9000 (blank = AWS S3)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Access Key *</label>
            <input
              v-model="storageForm.accessKey"
              type="text"
              placeholder="AKIA..."
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Secret Key *</label>
            <input
              v-model="storageForm.secretKey"
              type="password"
              placeholder="•••••••••••••••• (unchanged = keep existing)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Public Base URL</label>
            <input
              v-model="storageForm.publicBaseUrl"
              type="text"
              placeholder="https://cdn.example.com (optional, for public links)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Object Folder Prefix</label>
            <input
              v-model="storageForm.folder"
              type="text"
              placeholder="documents (optional subfolder inside the bucket)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Max Upload (MB)</label>
            <input
              v-model.number="storageForm.maxUploadMb"
              type="number"
              min="1"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
        </div>

        <div v-if="storageMessage" class="mt-4 text-sm" :class="storageMessage.ok ? 'text-emerald-700' : 'text-red-700'">
          {{ storageMessage.text }}
        </div>

        <div class="mt-6 flex items-center justify-end gap-3">
          <button
            @click="testStorageConnection"
            :disabled="testingStorage"
            class="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors text-sm font-medium disabled:opacity-50"
          >
            {{ testingStorage ? 'Testing…' : 'Test Connection' }}
          </button>
          <button
            @click="saveStorageSettings"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium"
          >
            Save Object Storage
          </button>
        </div>
      </div>

      <!-- Google Sign-In (SSO) -->
      <div class="bg-white rounded-lg shadow p-6">
        <div class="flex items-center justify-between mb-1">
          <h2 class="text-lg font-semibold text-gray-900">Google Sign-In (SSO)</h2>
          <span
            class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium"
            :class="ssoForm.enabled
              ? 'bg-emerald-100 text-emerald-700'
              : 'bg-gray-100 text-gray-600'"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="ssoForm.enabled ? 'bg-emerald-500' : 'bg-gray-400'"></span>
            {{ ssoForm.enabled ? 'Enabled' : 'Disabled' }}
          </span>
        </div>
        <p class="text-sm text-gray-600 mb-4">
          Let users sign in with their Google account. Create an OAuth 2.0 Client ID in
          Google Cloud Console and add its redirect URI as an authorized redirect.
          The client secret is stored securely and never returned in plaintext — leave it
          unchanged to keep the existing one.
        </p>
        <div class="mb-5">
          <label class="flex items-center gap-2 text-sm font-medium text-gray-700 cursor-pointer">
            <input v-model="ssoForm.enabled" type="checkbox" class="h-4 w-4 rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />
            Allow users to sign in with Google
          </label>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">OAuth Client ID *</label>
            <input
              v-model="ssoForm.clientId"
              type="text"
              placeholder="xxxxxxxx.apps.googleusercontent.com"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">OAuth Client Secret</label>
            <input
              v-model="ssoForm.clientSecret"
              type="password"
              placeholder="•••••••••••••••• (unchanged = keep existing)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Redirect URI *</label>
            <input
              v-model="ssoForm.redirectUri"
              type="text"
              placeholder="https://secphils.example.com/auth/sso/callback"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Domain Restriction</label>
            <input
              v-model="ssoForm.domainRestriction"
              type="text"
              placeholder="secphils.com (optional — only allow @domain emails)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
        </div>

        <div v-if="ssoMessage" class="mt-4 text-sm" :class="ssoMessage.ok ? 'text-emerald-700' : 'text-red-700'">
          {{ ssoMessage.text }}
        </div>

        <div class="mt-6 flex items-center justify-end">
          <button
            @click="saveSsoSettings"
            :disabled="ssoSaving"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
          >
            {{ ssoSaving ? 'Saving…' : 'Save Google Sign-In' }}
          </button>
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
              class="font-semibold text-gray-900 w-full px-2 py-1 border-b border-gray-200 focus:outline-none focus:ring-2 focus:ring-emerald-500 rounded-t-lg bg-transparent mb-2"
            />
            <input
              v-model="template.subject"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm mb-2"
              placeholder="Subject"
            />
            <textarea
              v-model="template.body"
              rows="4"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm font-mono"
            />
          </div>
        </div>
        <div class="mt-4 flex justify-end">
          <button @click="saveEmailTemplates" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium">
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
        <div class="mt-4 flex items-center justify-end gap-3">
          <p v-if="integrationsMessage" :class="['text-sm', integrationsMessage.ok ? 'text-green-700' : 'text-red-600']">
            {{ integrationsMessage.text }}
          </p>
          <button @click="saveIntegrations" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium">
            Save Integrations
          </button>
        </div>
      </div>
    </div>

    <!-- ================= AUDIT LOGS ================= -->
    <div v-if="isActiveTab('audit')" class="bg-white rounded-lg shadow overflow-hidden">
      <div class="p-6 border-b border-gray-200 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <h2 class="text-lg font-semibold text-gray-900">Audit Logs</h2>
        <div class="flex items-center gap-2">
          <input
            v-model="auditSearch"
            type="search"
            placeholder="Filter by action, user, entity, details…"
            class="w-full sm:w-72 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
          />
          <button @click="loadAuditLogs" :disabled="auditLoading" class="px-3 py-2 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 text-sm font-medium whitespace-nowrap disabled:opacity-50">
            <i class="fas fa-rotate mr-1" /> {{ auditLoading ? 'Refreshing…' : 'Refresh' }}
          </button>
        </div>
      </div>
      <div class="overflow-x-auto">
        <p v-if="auditLogs.length === 0" class="px-6 py-6 text-sm text-gray-500">
          {{ auditLoading ? 'Loading audit logs…' : 'No audit logs recorded yet.' }}
        </p>
        <p v-else-if="filteredAuditLogs.length === 0" class="px-6 py-6 text-sm text-gray-500">
          No logs match "{{ auditSearch }}".
        </p>
        <table v-else class="w-full">
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
              v-for="log in filteredAuditLogs"
              :key="log.id"
              class="hover:bg-gray-50"
            >
              <td class="px-6 py-4 text-sm text-gray-600">{{ log.timestamp }}</td>
              <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ log.user }}</td>
              <td class="px-6 py-4">
                <span class="px-2 py-1 bg-emerald-100 text-emerald-800 text-xs font-medium rounded">
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
          <i class="fas fa-pen text-emerald-600 mr-2" />Edit {{ editTarget.fullName }}
        </h3>
        <p class="mt-1 text-sm text-gray-500">{{ editTarget.email }}</p>
        <div class="mt-4 space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">First Name</label>
              <input v-model="editForm.firstName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
              <input v-model="editForm.lastName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input v-model="editForm.email" type="email" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <div class="relative">
              <select v-model="editForm.role" @change="onEditRoleChange" class="w-full pl-3 pr-9 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 appearance-none">
                <option value="ADMIN">ADMIN — Provider Admin</option>
                <option value="USER">USER — Provider Staff</option>
                <option value="CLIENT">CLIENT — Client Account</option>
              </select>
              <i class="fas fa-chevron-down absolute right-3 top-1/2 -translate-y-1/2 text-xs transition-colors pointer-events-none"></i>
            </div>
          </div>
          <div v-if="editForm.role === 'CLIENT'">
            <label class="block text-sm font-medium text-gray-700 mb-1">Company</label>
            <select v-model="editForm.companyId" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option :value="null">— No company —</option>
              <option v-for="c in companies" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">New Password (optional)</label>
            <input v-model="editForm.password" type="password" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="Leave blank to keep current" />
          </div>
          <div class="flex items-center gap-2">
            <input
              id="editIsActive"
              v-model="editForm.isActive"
              type="checkbox"
              class="w-4 h-4 text-emerald-600 border-gray-300 rounded focus:ring-emerald-500"
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
            class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ editSaving ? 'Saving…' : 'Save Changes' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= ADD USER MODAL ================= -->
    <div v-if="showAddUser" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h3 class="text-lg font-semibold text-gray-900">
          <i class="fas fa-user-plus text-emerald-600 mr-2" />Add User
        </h3>
        <p class="mt-1 text-sm text-gray-500">Creates a real account and emails the user a <strong>set-your-own-password</strong> link. Choose <strong>ADMIN</strong> for a provider admin, <strong>USER</strong> for provider staff, or <strong>CLIENT</strong> for a client-side account.</p>
        <div class="mt-4 space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">First Name</label>
              <input v-model="addUserForm.firstName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="Juan" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
              <input v-model="addUserForm.lastName" type="text" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="Dela Cruz" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input v-model="addUserForm.email" type="email" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="juan@secphils.com" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <div class="relative">
              <select v-model="addUserForm.role" @change="onAddRoleChange" class="w-full pl-3 pr-9 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 appearance-none">
                <option value="ADMIN">ADMIN — Provider Admin</option>
                <option value="USER">USER — Provider Staff</option>
                <option value="CLIENT">CLIENT — Client Account</option>
              </select>
              <i class="fas fa-chevron-down absolute right-3 top-1/2 -translate-y-1/2 text-xs transition-colors pointer-events-none"></i>
            </div>
          </div>
          <div v-if="addUserForm.role === 'CLIENT'">
            <label class="block text-sm font-medium text-gray-700 mb-1">Company</label>
            <select v-model="addUserForm.companyId" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option :value="null">— No company —</option>
              <option v-for="c in companies" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div v-if="addUserMessage" :class="[
            'p-3 rounded-lg text-sm',
            addUserMessage.ok ? 'bg-green-50 border border-green-200 text-green-700' : 'bg-red-50 border border-red-200 text-red-700'
          ]">
            {{ addUserMessage.text }}
          </div>
        </div>
        <div class="mt-5 flex justify-end gap-3">
          <button
            @click="closeAddUser"
            class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50"
          >
            Close
          </button>
          <button
            @click="addUser"
            :disabled="addUserSaving || !addUserForm.firstName.trim() || !addUserForm.lastName.trim() || !addUserForm.email.trim()"
            class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ addUserSaving ? 'Creating…' : 'Create User & Send Invite' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
