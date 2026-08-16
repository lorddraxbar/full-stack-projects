import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export { api }

export async function useLogin(credentials: { email: string; password: string }) {
  const response = await api.post('/auth/login', credentials)
  return response.data
}

export async function useRegister(data: Record<string, unknown>) {
  const response = await api.post('/auth/register', data)
  return response.data
}

export async function useSSOCallback(provider: string, code: string) {
  const response = await api.get(`/auth/sso/${provider}/callback?code=${code}`)
  return response.data
}

export async function useGetProjects() {
  const response = await api.get('/projects')
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

export async function useGetTasks() {
  const response = await api.get('/tasks')
  return response.data
}

export async function useGetDocuments(projectId: number) {
  const response = await api.get(`/projects/${projectId}/documents`)
  return response.data
}

export async function useUploadDocument(projectId: number, formData: FormData) {
  const response = await api.post(`/projects/${projectId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data
}

export async function useGetMessages(projectId: number) {
  const response = await api.get(`/projects/${projectId}/messages`)
  return response.data
}

export async function useSendMessage(projectId: number, data: Record<string, unknown>) {
  const response = await api.post(`/projects/${projectId}/messages`, data)
  return response.data
}

export async function useGetAnnouncements() {
  const response = await api.get('/announcements')
  return response.data
}

export async function useGetReviews() {
  const response = await api.get('/reviews')
  return response.data
}

export async function useSubmitReview(data: Record<string, unknown>) {
  const response = await api.post('/reviews', data)
  return response.data
}

export async function useGetAdminDashboard() {
  const response = await api.get('/admin/dashboard')
  return response.data
}

export async function useGetTeamMembers() {
  const response = await api.get('/admin/team')
  return response.data
}

export async function useAddTeamMember(data: Record<string, unknown>) {
  const response = await api.post('/admin/team', data)
  return response.data
}

export async function useGetAuditLogs() {
  const response = await api.get('/admin/audit-logs')
  return response.data
}

export async function useUpdateSystemSettings(data: Record<string, unknown>) {
  const response = await api.put('/admin/settings', data)
  return response.data
}
