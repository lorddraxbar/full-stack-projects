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

const { isClient, isProvider, isAdmin } = useRole()
const router = useRouter()
const searchQuery = ref('')
const selectedStatus = ref('ALL')
const showWizard = ref(false)

const projects = ref([
  { id: 1, name: 'Manufacturing Process Optimization', client: 'ABC Manufacturing', serviceType: 'Process Consulting', status: 'In Progress', progress: 65, dueDate: '2026-09-15', team: 4, assignee: 'John Doe', rep: 'Maria Santos' },
  { id: 2, name: 'Energy Sector Compliance Audit', client: 'XYZ Energy Corp', serviceType: 'Compliance Audit', status: 'Planning', progress: 20, dueDate: '2026-10-01', team: 3, assignee: 'Bob Wilson', rep: 'R. Dela Cruz' },
  { id: 3, name: 'Supply Chain Feasibility Study', client: 'Global Logistics Inc', serviceType: 'Feasibility Study', status: 'Completed', progress: 100, dueDate: '2026-08-01', team: 5, assignee: 'Jane Smith', rep: 'T. Reyes' },
  { id: 4, name: 'Water Treatment Plant Design', client: 'Municipal Water Authority', serviceType: 'Engineering Design', status: 'In Progress', progress: 45, dueDate: '2026-11-30', team: 6, assignee: 'Jane Smith', rep: 'City Engineer R. Lim' },
  { id: 5, name: 'Renewable Energy Assessment', client: 'Green Power Solutions', serviceType: 'Energy Assessment', status: 'On Hold', progress: 10, dueDate: '2026-12-15', team: 2, assignee: 'Unassigned', rep: 'K. Tan' },
])

const statusColors: Record<string, string> = {
  'In Progress': 'bg-blue-100 text-blue-800',
  'Planning': 'bg-yellow-100 text-yellow-800',
  'Completed': 'bg-green-100 text-green-800',
  'On Hold': 'bg-red-100 text-red-800',
}

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

const handleWizardSubmit = (data: WizardData) => {
  console.log('Wizard submitted:', data)
  // TODO: Call API to submit wizard
  // useCreateProject(data).then(() => {
  //   // Refresh projects list
  // })
}

onMounted(() => {
  // TODO: Fetch projects from API
  // useGetProjects().then(data => { projects.value = data })
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
            <option value="In Progress">In Progress</option>
            <option value="Planning">Planning</option>
            <option value="Completed">Completed</option>
            <option value="On Hold">On Hold</option>
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
              <span v-if="isProvider">
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
