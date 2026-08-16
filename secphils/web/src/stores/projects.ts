import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useProjectsStore = defineStore('projects', () => {
  const projects = ref([
    { id: 1, name: 'Manufacturing Process Optimization', client: 'ABC Manufacturing', status: 'In Progress', progress: 65, dueDate: '2026-09-15' },
    { id: 2, name: 'Energy Sector Compliance Audit', client: 'XYZ Energy Corp', status: 'Planning', progress: 20, dueDate: '2026-10-01' },
    { id: 3, name: 'Supply Chain Feasibility Study', client: 'Global Logistics Inc', status: 'Completed', progress: 100, dueDate: '2026-08-01' },
  ])

  const activeProjects = computed(() => projects.value.filter(p => p.status === 'In Progress'))
  const completedProjects = computed(() => projects.value.filter(p => p.status === 'Completed'))

  function addProject(project: Record<string, unknown>) {
    projects.value.push({ id: Date.now(), ...project } as typeof projects.value[0])
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

  return { projects, activeProjects, completedProjects, addProject, updateProject, deleteProject }
})
