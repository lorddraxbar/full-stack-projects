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
import { useGetMe, useGetProjects, useCreateProject, useCreateCompany, useUpdateCompany } from '@/services/api'
import { projectStatusLabel, PROJECT_STATUS_COLORS } from '@/lib/labels'

const { isClient } = useRole()
const router = useRouter()
const searchQuery = ref('')
const selectedStatus = ref('ALL')
const showWizard = ref(false)
const loading = ref(false)
const loadError = ref('')

// Backend ProjectResponse -> display shape (only fields the API actually returns)
interface ProjectRow {
  id: number
  name: string
  client: string
  serviceType: string
  status: string
  progress: number
}

const projects = ref<ProjectRow[]>([])
const meCompanyId = ref<number | null>(null)
const noCompany = ref(false)

function mapProject(p: any): ProjectRow {
  return {
    id: p.id,
    name: p.name,
    client: p.companyName || '—',
    serviceType: p.serviceName || '—',
    status: projectStatusLabel(p.status),
    progress: p.progress ?? 0,
  }
}

async function loadProjects() {
  loading.value = true
  loadError.value = ''
  try {
    const params: Record<string, unknown> = {}
    if (isClient.value) {
      if (noCompany.value) {
        projects.value = []
        return
      }
      // Server-side scope: clients only see their own company's projects.
      params.companyId = meCompanyId.value
    }
    const data = await useGetProjects(params)
    const content = Array.isArray(data) ? data : data?.content ?? []
    projects.value = content.map(mapProject)
  } catch (e: any) {
    loadError.value = e?.response?.data?.message || 'Failed to load projects'
  } finally {
    loading.value = false
  }
}

async function init() {
  if (isClient.value) {
    try {
      // GET /users/me returns the UserResponse body directly (no envelope).
      const me = (await useGetMe()) as any
      if (me?.companyId != null) meCompanyId.value = me.companyId
      else noCompany.value = true
    } catch {
      noCompany.value = true
    }
  }
  await loadProjects()
}

const statusOptions = [
  { value: 'ALL', label: 'All Status' },
  { value: 'NOT_STARTED', label: 'Not Started' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'ON_HOLD', label: 'On Hold' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'ARCHIVED', label: 'Archived' },
]

const statusColors: Record<string, string> = PROJECT_STATUS_COLORS

const heading = computed(() => (isClient.value ? 'My Projects' : 'All Projects'))
const subheading = computed(() =>
  isClient.value
    ? 'Projects assigned to your company.'
    : 'Manage and track all your projects.'
)

const filteredProjects = computed(() => {
  let result = projects.value

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      p =>
        p.name.toLowerCase().includes(query) ||
        p.client.toLowerCase().includes(query)
    )
  }

  if (selectedStatus.value !== 'ALL') {
    result = result.filter(p => p.status === projectStatusLabel(selectedStatus.value))
  }

  return result
})

const goToProject = (id: number) => {
  router.push(`/projects/${id}`)
}

// Wizard orchestration:
// - existing customer -> PUT /companies/{id} when a new rep was picked
//                        then POST /projects against the selected companyId
// - new customer      -> POST /companies (rep email becomes the company contact)
//                        then POST /projects against the returned companyId
const handleWizardSubmit = async (data: WizardData) => {
  try {
    let companyId = data.companyId
    if (data.scenario === 'new') {
      const company = await useCreateCompany({
        name: data.company.name,
        location: data.company.location || null,
        owner: data.company.owner || null,
        description: data.company.description || null,
        email: data.rep.email || null,
      })
      companyId = (company as any).id
    } else if (companyId != null && data.rep.userId != null) {
      // Persist the client user picked in the wizard as the company's authorized rep.
      await useUpdateCompany(companyId, {
        name: data.company.name,
        authorizedRepId: data.rep.userId,
      })
    }
    await useCreateProject({
      companyId,
      serviceId: data.project.serviceId,
      name: data.project.name,
      scope: data.project.scope,
    })
    showWizard.value = false
    await loadProjects()
  } catch (e: any) {
    const status = e?.response?.status
    loadError.value =
      e?.response?.data?.message ||
      (status === 409
        ? 'A company with that name already exists.'
        : 'Failed to create the project')
  }
}

onMounted(init)
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

    <div v-if="loadError" class="mb-4 bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
      {{ loadError }}
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
            class="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
          >
            <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>
      </CardContent>
    </Card>

    <!-- Projects List -->
    <Card>
      <CardContent class="p-0">
        <div v-if="loading && projects.length === 0" class="p-12 text-center text-gray-500">
          Loading projects…
        </div>
        <div v-else-if="noCompany && isClient" class="p-12 text-center">
          <p class="text-gray-600">
            No company is linked to your account yet.
          </p>
          <p class="text-gray-500 text-sm mt-1">
            Contact a SECPhils administrator to get your workspace set up.
          </p>
        </div>
        <div v-else-if="filteredProjects.length === 0" class="p-12 text-center">
          <p class="text-gray-600">No projects found matching your criteria.</p>
        </div>
        <div v-else class="divide-y divide-gray-200">
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
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-2 text-sm text-gray-600 mb-3">
              <span>
                <i class="fas fa-building text-xs mr-1 text-gray-400" />{{ project.client }}
              </span>
              <span>
                <i class="fas fa-tag text-xs mr-1 text-gray-400" />{{ project.serviceType }}
              </span>
            </div>
            <div class="flex items-center gap-3 text-sm">
              <span class="text-gray-600 w-24 shrink-0">{{ project.progress }}% complete</span>
              <div class="flex-1 bg-gray-200 rounded-full h-2">
                <div
                  class="bg-emerald-600 h-2 rounded-full transition-all"
                  :style="{ width: project.progress + '%' }"
                />
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- New Project Wizard -->
    <NewProjectWizard v-model:open="showWizard" @submit="handleWizardSubmit" />
  </div>
</template>
