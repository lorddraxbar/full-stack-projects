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
import { useGetMe, useGetProjects, useCreateProject, useUpdateProject, useCreateCompany, useUpdateCompany, useUploadDocument } from '@/services/api'
import { projectStatusLabel, PROJECT_STATUS_COLORS, formatPhp, formatDate, formatDateTime, timeAgo } from '@/lib/labels'

const { isClient } = useRole()
const router = useRouter()
const searchQuery = ref('')
const selectedStatus = ref('ALL')
const showWizard = ref(false)
const loading = ref(false)
const loadError = ref('')
const notice = ref('')

// Backend ProjectResponse -> display shape (only fields the API actually returns)
interface ProjectRow {
  id: number
  name: string
  client: string
  serviceType: string
  status: string
  progress: number
  totalCost: number | null
  latestUpdate: string | null
  latestUpdatedAt: string | null
  createdAt: string | null
  completedAt: string | null
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
    totalCost: p.totalCost != null ? Number(p.totalCost) : null,
    latestUpdate: p.latestUpdateBody || null,
    latestUpdatedAt: p.latestUpdateAt || null,
    createdAt: p.createdAt || null,
    completedAt: p.completedAt || null,
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

  if (selectedStatus.value !== 'ALL') {
    result = result.filter(p => p.status === projectStatusLabel(selectedStatus.value))
  }

  // Standard search: every space-separated term must appear somewhere in the
  // row's displayed fields (name, client, service, status, cost, latest update,
  // created/completed dates). Same multi-term AND convention as Admin → Users.
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    const terms = q.split(/\s+/)
    result = result.filter(p => {
      const haystack = [
        p.name,
        p.client,
        p.serviceType,
        p.status,
        formatPhp(p.totalCost),
        p.latestUpdate || '',
        formatDate(p.createdAt),
        p.completedAt ? formatDate(p.completedAt) : '',
      ].join(' ').toLowerCase()
      return terms.every(t => haystack.includes(t))
    })
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
  notice.value = ''
  try {
    let companyId = data.companyId
    if (data.scenario === 'new') {
      const company = await useCreateCompany({
        name: data.company.name,
        location: data.company.location || null,
        owner: data.company.owner || null,
        ownerPhone: data.company.ownerPhone || null,
        description: data.company.description || null,
        email: data.rep.email || null,
        // New-customer flow: create + invite the authorized representative's
        // CLIENT account (they become the company's authorizedRep). The
        // backend only does this when no existing authorizedRepId is sent.
        repName: data.rep.name || null,
        repPhone: data.rep.phone || null,
      })
      companyId = (company as any).id
    } else if (companyId != null && data.rep.userId != null) {
      // Existing customer: persist the picked client user as the company's
      // authorized rep. Phone numbers for an existing rep / owner are their own
      // data (set via the member's profile or Admin), so we don't round-trip
      // them here — a new rep added in this flow already got its phone via the
      // invite endpoint.
      await useUpdateCompany(companyId, {
        name: data.company.name,
        authorizedRepId: data.rep.userId,
      })
    }
    const p = data.project
    // The structured checklist rows are JSONB columns: serialize the arrays;
    // free-text fields (waste management practices, manufacturing procedure)
    // go straight through as plain text.
    const productionPayload = {
      companyId,
      serviceId: p.serviceId,
      name: p.name,
      notes: p.notes,
      // Project address (full PH address where the project operates) — null
      // when the project address equals the company address.
      address: p.address || null,
      totalCost: p.totalCost ?? null,
      rawMaterials: p.rawMaterials ? JSON.stringify(p.rawMaterials) : null,
      productionOutput: p.productionOutput ? JSON.stringify(p.productionOutput) : null,
      wasteManagement: p.wasteManagement || null,
      wasteMaterials: p.wasteMaterials ? JSON.stringify(p.wasteMaterials) : null,
      manufacturingProcedure: p.manufacturingProcedure || null,
    }
    const project = await useCreateProject(productionPayload)
    const projectId = (project as any).id as number
    // Optional flowchart: best-effort. Object storage may not be configured
    // yet — the project is already created, so a failed upload is reported
    // as a notice (attach later from the Documents tab) instead of an error.
    if (p.flowchart) {
      try {
        const doc = await useUploadDocument({
          projectId,
          title: 'Production flowchart',
          description: 'Submitted with the new project wizard',
          file: p.flowchart,
        })
        // PUT (not the create path): update() emits no notifications, so
        // recording the URL on the project is quiet. Re-send the full
        // payload — apply() overwrites every field.
        await useUpdateProject(projectId, {
          ...productionPayload,
          productionFlowchartUrl: (doc as any).fileUrl,
          progress: (project as any).progress ?? 0,
        })
        notice.value = `Project created. The production flowchart was saved in the project's Documents tab.`
      } catch (e: any) {
        notice.value = `Project created, but the flowchart could not be uploaded (${
          e?.response?.data?.message || 'object storage not configured yet'
        }). You can attach it later from the Documents tab.`
      }
    } else {
      notice.value = 'Project created.'
    }
    // NOTE: do NOT set `showWizard = false` here. This is an async continuation
    // (we awaited useCreateProject above) — by the time it runs, the user may
    // have already opened a FRESH wizard, and this late write would dismiss the
    // new dialog. The child wizard closes itself on submit (it sets its own
    // open=false right after emitting), which v-model:open propagates here
    // synchronously — so the parent never has to close it.
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

    <div v-if="notice" class="mb-4 bg-emerald-50 border border-emerald-200 rounded-lg p-3 text-sm text-emerald-700">
      <i class="fas fa-circle-check mr-1" />{{ notice }}
    </div>

    <!-- Filters -->
    <Card class="mb-6">
      <CardContent class="p-4">
        <div class="flex flex-col sm:flex-row gap-4">
        <div class="flex-1 relative">
          <i class="fas fa-magnifying-glass absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none" />
          <Input
            v-model="searchQuery"
            type="text"
            placeholder="Search all projects — name, client, service, status, cost, updates, dates"
            class="pl-9 pr-9"
          />
          <button
            v-if="searchQuery"
            @click="searchQuery = ''"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
            aria-label="Clear search"
          >
            <i class="fas fa-xmark text-sm" />
          </button>
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
        <!-- One project card per row. Desktop: icon tile | content | status
             pill + dates (right column). Mobile (<640px): icon beside the
             content, pill + dates drop to a second row. -->
        <div v-else class="p-4 sm:p-5 flex flex-col gap-3">
          <div
            v-for="project in filteredProjects"
            :key="project.id"
            @click="goToProject(project.id)"
            class="grid grid-cols-[auto_1fr] sm:grid-cols-[auto_1fr_auto] items-center gap-3 sm:gap-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5 transition-all hover:border-primary hover:shadow-[0_4px_14px_rgba(41,202,142,0.10)] cursor-pointer"
          >
            <div class="col-start-1 row-start-1 w-[52px] h-[52px] flex-none rounded-[11px] bg-accent text-accent-foreground flex items-center justify-center text-lg">
              <i class="fas fa-clipboard-list" />
            </div>
            <div class="col-start-2 row-start-1 min-w-0">
              <h3 class="text-base font-semibold text-gray-900 leading-snug">{{ project.name }}</h3>
              <div class="mt-1.5 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-[13px] text-gray-500">
                <span class="inline-flex items-center">
                  <i class="fas fa-building text-xs mr-1.5 text-gray-400" />{{ project.client }}
                </span>
                <span class="w-[3px] h-[3px] flex-none rounded-full bg-gray-300" />
                <span class="inline-flex items-center">
                  <i class="fas fa-tag text-xs mr-1.5 text-gray-400" />{{ project.serviceType }}
                </span>
                <span class="w-[3px] h-[3px] flex-none rounded-full bg-gray-300" />
                <span class="inline-flex items-center font-semibold text-gray-900">
                  <i class="fas fa-money-bill-wave text-xs mr-1.5 text-gray-400" />{{ formatPhp(project.totalCost) }}
                </span>
              </div>
              <div class="mt-2 truncate text-[13px]" :title="project.latestUpdate ?? ''">
                <template v-if="project.latestUpdate">
                  <span class="text-[#536976]">“{{ project.latestUpdate }}”</span>
                  <span class="text-gray-400"> · {{ timeAgo(project.latestUpdatedAt) || formatDateTime(project.latestUpdatedAt) }}</span>
                </template>
                <span v-else class="text-gray-400">No updates yet</span>
              </div>
            </div>
            <div class="col-start-2 row-start-2 sm:col-start-3 sm:row-start-1 flex flex-row flex-wrap items-center justify-between gap-2 sm:gap-2.5 sm:flex-col sm:items-end">
              <Badge :class="statusColors[project.status]">
                {{ project.status }}
              </Badge>
              <div class="flex flex-row sm:flex-col items-center gap-3 sm:gap-1 text-xs text-gray-400">
                <span>
                  <i class="fas fa-calendar-plus text-[11px] mr-1" />Created {{ formatDate(project.createdAt) }}
                </span>
                <span>
                  <i class="fas fa-flag-checkered text-[11px] mr-1" />Completed {{ project.completedAt ? formatDate(project.completedAt) : '—' }}
                </span>
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
