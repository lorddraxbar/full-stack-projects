import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userRole')
      localStorage.removeItem('userName')
      localStorage.removeItem('userId')
      if (!window.location.pathname.startsWith('/auth/login')) {
        window.location.href = '/auth/login'
      }
    }
    return Promise.reject(error)
  }
)

export { api }

// ---------- Auth ----------
export async function useLogin(credentials: { email: string; password: string }) {
  const response = await api.post('/auth/login', credentials)
  return response.data
}

export async function useSSOCallback(provider: string, identity: { email: string; firstName: string; lastName: string }) {
  const response = await api.post(`/auth/sso/${provider}`, identity)
  return response.data
}

// ---------- Users ----------
export async function useGetUsers() {
  const response = await api.get('/users')
  return response.data
}

export async function useGetMe() {
  const response = await api.get('/users/me')
  return response.data
}

export async function useCreateUser(data: { firstName: string; lastName: string; email: string; role: string; companyId?: number | null; password?: string; isActive?: boolean }) {
  const response = await api.post('/users', data)
  return response.data
}

export async function useUpdateUser(id: number, data: Record<string, unknown>) {
  const response = await api.put(`/users/${id}`, data)
  return response.data
}

export async function useDeactivateUser(id: number) {
  const response = await api.delete(`/users/${id}`)
  return response.data
}

export async function useActivateUser(id: number) {
  const response = await api.post(`/users/${id}/activate`)
  return response.data
}

export async function useResendInvite(id: number) {
  const response = await api.post(`/users/${id}/resend-invite`)
  return response.data
}

export async function useSetPassword(token: string, password: string) {
  const response = await api.post('/users/set-password', { token, password })
  return response.data
}

export async function useHardDeleteUser(id: number, password: string) {
  await api.delete(`/users/${id}/hard`, { data: { password } })
}

// ---------- Projects ----------
export async function useGetProjects(params?: { companyId?: number; status?: string; search?: string }) {
  const response = await api.get('/projects', { params })
  return response.data
}

export async function useGetProject(id: number) {
  const response = await api.get(`/projects/${id}`)
  return response.data
}

export async function useCreateProject(data: Record<string, unknown>) {
  const response = await api.post('/projects', data)
  return response.data
}

export async function useUpdateProject(id: number, data: Record<string, unknown>) {
  const response = await api.put(`/projects/${id}`, data)
  return response.data
}

export async function useDeleteProject(id: number) {
  const response = await api.delete(`/projects/${id}`)
  return response.data
}

// ---------- Project team ----------
export async function useGetProjectTeam(projectId: number) {
  const response = await api.get(`/projects/${projectId}/team`)
  return response.data
}

export async function useAddProjectTeamMember(projectId: number, userId: number) {
  const response = await api.post(`/projects/${projectId}/team`, { userId })
  return response.data
}

export async function useRemoveProjectTeamMember(projectId: number, userId: number) {
  const response = await api.delete(`/projects/${projectId}/team/${userId}`)
  return response.data
}

// ---------- Companies ----------
export async function useGetCompanies() {
  const response = await api.get('/companies')
  return response.data
}

export async function useGetCompany(id: number) {
  const response = await api.get(`/companies/${id}`)
  return response.data
}

export async function useCreateCompany(data: Record<string, unknown>) {
  const response = await api.post('/companies', data)
  return response.data
}

export async function useUpdateCompany(id: number, data: Record<string, unknown>) {
  const response = await api.put(`/companies/${id}`, data)
  return response.data
}

// ---------- Roles & Permissions (ADMIN) ----------
export async function useGetRoles() {
  const response = await api.get('/roles')
  return response.data
}

export async function useCreateRole(data: { name: string; userType: string; description?: string; permissionIds?: number[] }) {
  const response = await api.post('/roles', data)
  return response.data
}

export async function useUpdateRole(id: number, data: { name: string; userType: string; description?: string; permissionIds?: number[] }) {
  const response = await api.put(`/roles/${id}`, data)
  return response.data
}

export async function useDeleteRole(id: number) {
  await api.delete(`/roles/${id}`)
}

export async function useGetPermissions() {
  const response = await api.get('/permissions')
  return response.data
}

export async function useUpdateMe(data: Record<string, unknown>) {
  const response = await api.put('/users/me', data)
  return response.data
}

// ---------- Tasks ----------
export async function useGetTasks(params?: { projectId?: number; status?: string; priority?: string; assigneeId?: number }) {
  const response = await api.get('/tasks', { params })
  return response.data
}

export async function useCreateTask(data: Record<string, unknown>) {
  const response = await api.post('/tasks', data)
  return response.data
}

export async function useUpdateTask(id: number, data: Record<string, unknown>) {
  const response = await api.put(`/tasks/${id}`, data)
  return response.data
}

export async function useDeleteTask(id: number) {
  const response = await api.delete(`/tasks/${id}`)
  return response.data
}

// ---------- Documents (backend: /api/v1/documents, JSON body, no file upload) ----------
export async function useGetDocuments(params?: { projectId?: number; category?: string }) {
  const response = await api.get('/documents', { params })
  return response.data
}

export async function useCreateDocument(data: Record<string, unknown>) {
  const response = await api.post('/documents', data)
  return response.data
}

export async function useUpdateDocument(id: number, data: Record<string, unknown>) {
  const response = await api.put(`/documents/${id}`, data)
  return response.data
}

export async function useDeleteDocument(id: number) {
  const response = await api.delete(`/documents/${id}`)
  return response.data
}

// ---------- Messages (backend: /api/v1/messages?projectId=) ----------
export async function useGetMessages(projectId: number) {
  const response = await api.get('/messages', { params: { projectId } })
  return response.data
}

export async function useSendMessage(projectId: number, body: string) {
  const response = await api.post('/messages', { projectId, body })
  return response.data
}

// ---------- Announcements ----------
export async function useGetAnnouncements() {
  const response = await api.get('/announcements')
  return response.data
}

export async function useCreateAnnouncement(data: Record<string, unknown>) {
  const response = await api.post('/announcements', data)
  return response.data
}

export async function useUpdateAnnouncement(id: number, data: Record<string, unknown>) {
  const response = await api.put(`/announcements/${id}`, data)
  return response.data
}

export async function useDeleteAnnouncement(id: number) {
  const response = await api.delete(`/announcements/${id}`)
  return response.data
}

// ---------- Reviews ----------
export async function useGetReviews(params?: { projectId?: number; status?: string }) {
  const response = await api.get('/reviews', { params })
  return response.data
}

export async function useSubmitReview(data: Record<string, unknown>) {
  const response = await api.post('/reviews', data)
  return response.data
}

export async function useUpdateReviewStatus(id: number, status: string) {
  const response = await api.patch(`/reviews/${id}/status`, { status })
  return response.data
}

// ---------- Notifications ----------
export async function useGetNotifications() {
  const response = await api.get('/notifications')
  return response.data
}

export async function useMarkNotificationRead(id: number) {
  const response = await api.patch(`/notifications/${id}/read`)
  return response.data
}

export async function useMarkAllNotificationsRead() {
  const response = await api.patch('/notifications/read-all')
  return response.data
}

// ---------- Admin ----------
export async function useGetAuditLogs(params?: { action?: string; userId?: number; limit?: number }) {
  const response = await api.get('/admin/audit-logs', { params })
  return response.data
}

export async function useGetSystemSettings() {
  const response = await api.get('/admin/settings')
  return response.data
}

export async function useUpdateSystemSettings(data: Record<string, unknown>) {
  const response = await api.put('/admin/settings', data)
  return response.data
}

// ---------- Public landing page ----------
export interface LandingCompany {
  id?: number
  name?: string
  location?: string
  owner?: string
  description?: string
  tagline?: string
  industrySectors?: string
  headquarters?: string
  phone?: string
  email?: string
  website?: string
  socialLinks?: string
  brandPrimary?: string
  brandSecondary?: string
  logoUrl?: string
}

export interface LandingReview {
  id: number
  customerName?: string
  projectName?: string
  rating: number
  title: string
  body: string
  createdAt?: string
}

export interface LandingPayload {
  portalName: string
  tagline: string
  maintenanceMode: boolean
  company?: LandingCompany
  reviews?: LandingReview[]
  services?: LandingService[]
}

export interface LandingService {
  id: number
  name: string
  description?: string
  category?: string
  categoryIcon?: string
  icon?: string
  sortOrder?: number
}

export async function useGetLanding(): Promise<LandingPayload> {
  const response = await api.get('/landing')
  return response.data
}

export interface LandingContactPayload {
  firstName: string
  lastName: string
  email: string
  phone: string
  message: string
}

export async function usePostLandingContact(payload: LandingContactPayload): Promise<{ status: string; recipients: number }> {
  const response = await api.post('/landing/contact', payload)
  return response.data
}

// ---------- Service Catalog (admin) ----------
export interface ServiceItem {
  id: number
  name: string
  description?: string
  category?: string
  categoryId?: number | null
  isActive?: boolean
  icon?: string
  sortOrder?: number
  deactivatedAt?: string | null
  createdAt?: string
}

export interface ServicePayload {
  name: string
  description?: string
  category?: string
  categoryId?: number | null
  isActive?: boolean
  icon?: string
  sortOrder?: number
}

export async function useGetServices(): Promise<ServiceItem[]> {
  const response = await api.get('/services')
  return response.data
}

export async function useCreateService(data: ServicePayload): Promise<ServiceItem> {
  const response = await api.post('/services', data)
  return response.data
}

export async function useUpdateService(id: number, data: ServicePayload): Promise<ServiceItem> {
  const response = await api.put(`/services/${id}`, data)
  return response.data
}

export async function useDeactivateService(id: number): Promise<void> {
  await api.post(`/services/${id}/deactivate`)
}

export async function useActivateService(id: number): Promise<ServiceItem> {
  const response = await api.post(`/services/${id}/activate`)
  return response.data
}

export async function useHardDeleteService(id: number, password: string): Promise<void> {
  await api.delete(`/services/${id}/hard`, { data: { password } })
}

// ---------- Service Categories (admin) ----------
export interface ServiceCategoryItem {
  id: number
  name: string
  icon?: string
  sortOrder?: number
  serviceCount?: number
  createdAt?: string
}

export interface ServiceCategoryPayload {
  name: string
  icon?: string
  sortOrder?: number
}

export async function useGetServiceCategories(): Promise<ServiceCategoryItem[]> {
  const response = await api.get('/service-categories')
  return response.data
}

export async function useCreateServiceCategory(data: ServiceCategoryPayload): Promise<ServiceCategoryItem> {
  const response = await api.post('/service-categories', data)
  return response.data
}

export async function useUpdateServiceCategory(id: number, data: ServiceCategoryPayload): Promise<ServiceCategoryItem> {
  const response = await api.put(`/service-categories/${id}`, data)
  return response.data
}

export async function useDeleteServiceCategory(id: number): Promise<void> {
  await api.delete(`/service-categories/${id}`)
}
