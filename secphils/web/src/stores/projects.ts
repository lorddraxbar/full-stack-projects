import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useGetProjects } from '@/services/api'
import { projectStatusLabel } from '@/lib/labels'

// Backend ProjectResponse -> store shape (keeps dependents like
// TasksView / TaskDetailModal working without changes).
interface ProjectRow {
  id: number
  name: string
  client: string
  status: string
  progress: number
  dueDate: string
}

function mapProject(p: any): ProjectRow {
  return {
    id: p.id,
    name: p.name,
    client: p.companyName || '—',
    status: projectStatusLabel(p.status),
    progress: p.progress ?? 0,
    dueDate: p.dueDate || '',
  }
}

export const useProjectsStore = defineStore('projects', () => {
  const projects = ref<ProjectRow[]>([])
  const loaded = ref(false)

  const activeProjects = computed(() => projects.value.filter(p => p.status === 'In Progress'))
  const completedProjects = computed(() => projects.value.filter(p => p.status === 'Completed'))

  async function loadProjects() {
    try {
      const data = await useGetProjects()
      const content = Array.isArray(data) ? data : data?.content ?? []
      projects.value = content.map(mapProject)
    } catch {
      // Leave existing data on failure; views surface their own errors.
    } finally {
      loaded.value = true
    }
  }

  function addProject(project: Record<string, unknown>) {
    projects.value.push({ id: Date.now(), ...project } as ProjectRow)
  }

  function updateProject(id: number, updates: Record<string, unknown>) {
    const index = projects.value.findIndex(p => p.id === id)
    if (index !== -1) {
      projects.value[index] = { ...projects.value[index], ...updates }
    }
  }

  function deleteProject(id: number) {
    projects.value = projects.value.filter(p => p.id !== id)
  }

  return { projects, loaded, activeProjects, completedProjects, loadProjects, addProject, updateProject, deleteProject }
})
