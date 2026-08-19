<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import NewProjectWizard from '@/components/NewProjectWizard.vue'
import type { WizardData } from '@/components/NewProjectWizard.vue'
import { useRole } from '@/composables/useRole'
import { useGetProjects, useCreateProject } from '@/services/api'
import { projectStatusLabel, PROJECT_STATUS_COLORS, formatDate } from '@/lib/labels'

const { isClient, isUser, isAdmin } = useRole()
const router = useRouter()
const searchQuery = ref('')
const selectedStatus = ref('ALL')
const showWizard = ref(false)
const loading = ref(false)
const loadError = ref('')

// Backend ProjectResponse -> display shape
interface ProjectRow {
  id: number
  name: string
  client: string
  serviceType: string
  status: string
  progress: number
  dueDate: string
  rep: string
  assignee: string
  team: number
}

const projects = ref<ProjectRow[]>([])

function mapProject(p: any): ProjectRow {
  return {
    id: p.id,
    name: p.name,
    client: p.companyName || '—',
    serviceType: p.serviceName || '—',
    status: projectStatusLabel(p.status),
    progress: p.progress ?? 0,
    dueDate: formatDate(p.dueDate),
    rep: p.authorizedRepName || '—',
    assignee: p.assigneeName || 'Unassigned',
    team: p.teamSize ?? 0,
  }
}

async function loadProjects() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await useGetProjects()
    const content = Array.isArray(data) ? data : data?.content ?? []
    projects.value = content.map(mapProject)
  } catch (e: any) {
    loadError.value = e?.response?.data?.message || 'Failed to load projects'
  } finally {
    loading.value = false
  }
}

const statusColors: Record<string, string> = PROJECT_STATUS_COLORS

const heading = computed(() =>
  isClient.value ? 'My Projects' : 'All Projects'
)
const subheading = computed(() =>
  isClient.value
    ? 'Projects assigned to your company.'
    : 'Manage and track all your projects.'
)

const filteredProjects = computed(() => {
  let result = projects.value

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(p =>
      p.name.toLowerCase().includes(query) ||
      p.client.toLowerCase().includes(query)
    )
  }

  if (selectedStatus.value !== 'ALL') {
    result = result.filter(p => p.status === selectedStatus.value)
  }

  return result
})

const goToProject = (id: number) => {
  router.push(`/projects/${id}`)
}

const handleWizardSubmit = async (data: WizardData) => {
  try {
    await useCreateProject(data as unknown as Record<string, unknown>)
    showWizard.value = false
    await loadProjects()
  } catch (e: any) {
    loadError.value = e?.response?.data?.message || 'Failed to create project'
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<template>
  <div>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">{{ heading }}</h1>
        <p class="text-gray-600 mt-1">{{ subheading }}</p>
      </div>
      <Button v-if="!isClient" @click="showWizard = true">
        + New Project
      </Button>
    </div>

    <!-- Filters -->
    <Card class="mb-6">
      <CardContent class="p-4">
        <div class="flex flex-col sm:flex-row gap-4">
          <div class="flex-1">
            <Input
              v-model="searchQuery"
              type="text"
              placeholder="Search projects..."
            />
          </div>
          <select
            v-model="selectedStatus"
            class="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">All Status</option>
            <option value="Not Started">Not Started</option>
            <option value="In Progress">In Progress</option>
            <option value="On Hold">On Hold</option>
            <option value="Completed">Completed</option>
          </select>
        </div>
      </CardContent>
    </Card>

    <!-- Projects List -->
    <Card>
      <CardContent class="p-0">
        <div class="divide-y divide-gray-200">
          <div
            v-for="project in filteredProjects"
            :key="project.id"
            @click="goToProject(project.id)"
            class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
          >
            <div class="flex items-center justify-between mb-3">
              <h3 class="font-medium text-gray-900">{{ project.name }}</h3>
              <Badge :class="statusColors[project.status]">
                {{ project.status }}
              </Badge>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2 text-sm text-gray-600 mb-3">
              <span>
                <i class="fas fa-building text-xs mr-1 text-gray-400" />{{ project.client }}
              </span>
              <span>
                <i class="fas fa-tag text-xs mr-1 text-gray-400" />{{ project.serviceType }}
              </span>
              <span v-if="isClient">
                <i class="fas fa-user text-xs mr-1 text-gray-400" />Rep: {{ project.rep }}
              </span>
              <span v-if="isUser">
                <i class="fas fa-user text-xs mr-1 text-gray-400" />Assignee: {{ project.assignee }}
              </span>
              <span v-if="isAdmin">
                <i class="fas fa-users text-xs mr-1 text-gray-400" />Team: {{ project.team }} members
              </span>
            </div>
            <div class="flex items-center justify-between text-sm">
              <span class="text-gray-600">Due: {{ project.dueDate }}</span>
              <span class="text-gray-600">{{ project.progress }}% complete</span>
            </div>
            <div class="mt-2 w-full bg-gray-200 rounded-full h-2">
              <div
                class="bg-blue-600 h-2 rounded-full transition-all"
                :style="{ width: project.progress + '%' }"
              />
            </div>
          </div>
        </div>

        <div v-if="filteredProjects.length === 0" class="p-12 text-center">
          <p class="text-gray-600">No projects found matching your criteria.</p>
        </div>
      </CardContent>
    </Card>

    <!-- New Project Wizard -->
    <NewProjectWizard v-model:open="showWizard" @submit="handleWizardSubmit" />
  </div>
</template>
