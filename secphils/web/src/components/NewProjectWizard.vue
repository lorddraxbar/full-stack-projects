<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Separator } from '@/components/ui/separator'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog'
import { useGetCompanies, useGetCompanyTeamFor, useGetServices, useInviteCustomerRep } from '@/services/api'

/**
 * Wizard output, shaped for backend orchestration in ProjectsView:
 * - new:      parent creates a Company (name/location/owner/description/email)
 *             then a Project against the returned companyId.
 * - existing: parent creates a Project against the selected companyId.
 * serviceId is a real Service id from /services (or null).
 */
export interface WizardData {
  scenario: 'new' | 'existing'
  companyId: number | null
  company: {
    name: string
    location: string
    owner: string
    description: string
  }
  rep: {
    name: string
    email: string
    /** Portal user id picked from the customer's client team (existing scenario); null otherwise. */
    userId: number | null
  }
  project: {
    name: string
    serviceId: number | null
    scope: string
    dueDate: string
  }
}

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  submit: [data: WizardData]
}>()

// Dialog open state controlled by prop
const dialogOpen = ref(props.open)

watch(() => props.open, (val) => {
  dialogOpen.value = val
  if (val) {
    resetForm()
    void loadLookups()
  }
})

watch(dialogOpen, (val) => {
  emit('update:open', val)
})

// Wizard state
const currentStep = ref(0)
const scenario = ref<'new' | 'existing'>('new')
const loadError = ref('')

// Form data
const companyForm = ref({
  name: '',
  location: '',
  owner: '',
  description: '',
})

const repForm = ref({
  name: '',
  email: '',
})

const projectForm = ref({
  name: '',
  serviceId: '' as string, // real Service id (string from Select)
  scope: '',
  dueDate: '',
})

// Real catalog lookups (backend /companies, /services)
const existingCompanies = ref<{ id: number; name: string; location: string | null; owner: string | null; authorizedRepId: number | null; authorizedRepName: string | null }[]>([])
const services = ref<{ id: number; name: string; category: string | null }[]>([])
const loadingLookups = ref(false)
const selectedCompanyId = ref<string | null>(null)
const selectedCompanyIdNum = computed<number | null>(() =>
  selectedCompanyId.value ? Number(selectedCompanyId.value) : null
)

// Declared before repResolved below: its getter reads selectedCompany, and a
// watch on repResolved evaluates it during setup — before later declarations
// run — so any forward reference here is a TDZ ReferenceError at first render.
const selectedCompany = computed(() =>
  existingCompanies.value.find(c => c.id === selectedCompanyIdNum.value)
)

// Client users of the selected customer company (existing scenario)
const clientTeam = ref<{ id: number; name: string; email: string; role: string; status: string }[]>([])
const teamLoading = ref(false)
const teamError = ref('')
const selectedRepId = ref<string | null>(null)

const selectedTeamMember = computed(() =>
  clientTeam.value.find(m => m.id === (selectedRepId.value ? Number(selectedRepId.value) : null))
)

// The representative this project will be submitted with (existing customer):
// the client the staff member picked, else — when no one is picked — the
// company's current rep (keeps it). The rep is REQUIRED, so with no team and no
// current rep the form must add a new one before the project can proceed.
const resolvedRep = computed(() => {
  const picked = selectedTeamMember.value
  if (picked) return picked
  const currentId = selectedCompany.value?.authorizedRepId
  return currentId ? clientTeam.value.find(m => m.id === currentId) ?? null : null
})

// The picker only earns its space when there is more than one client user to
// choose between. Exactly one -> auto-selected (rendered in the template); zero -> add-a-new only.
const showPicker = computed(() => clientTeam.value.length > 1)
const repResolved = computed(() => resolvedRep.value != null)
// A blocked Next stores the "rep required" copy in the red loadError banner;
// once the requirement is satisfied (pick or add a rep) that banner is stale — clear it.
watch(repResolved, (ok) => {
  if (ok) loadError.value = ''
})

const repRequiredMessage = computed(() => {
  if (clientTeam.value.length === 0)
    return 'This company has no client users yet and no representative on file. Add a new authorized representative to continue.'
  if (selectedCompany.value?.authorizedRepId == null)
    return 'An authorized representative is required — they review this customer\u2019s onboarding. Pick a client user or add a new one.'
  return ''
})

// Add-a-new-rep form (always available in the existing-customer flow).
const addingRep = ref(false)
const repError = ref('')
const addRepForm = ref({ name: '', email: '' })
const addRepBusy = ref(false)
const newRepFormValid = computed(() =>
  addRepForm.value.name.trim() !== '' && addRepForm.value.email.trim() !== ''
)
// The new-customer rep step binds repForm (the contact that becomes the company's
// rep on activation) — distinct from the add-rep form used in the existing flow.
const newCustomerRepValid = computed(() =>
  repForm.value.name.trim() !== '' && repForm.value.email.trim() !== ''
)

async function loadTeam() {
  teamError.value = ''
  if (selectedCompanyIdNum.value == null) {
    clientTeam.value = []
    selectedRepId.value = null
    return
  }
  teamLoading.value = true
  selectedRepId.value = null
  addingRep.value = false
  addRepForm.value = { name: '', email: '' }
  repError.value = ''
  try {
    const team = await useGetCompanyTeamFor(selectedCompanyIdNum.value)
    clientTeam.value = (team as any[]).map(m => ({
      id: m.id, name: m.name, email: m.email, role: m.role, status: m.status,
    }))
  } catch (e: any) {
    teamError.value = e?.response?.data?.message || 'Failed to load the client users for this company'
    clientTeam.value = []
  } finally {
    teamLoading.value = false
    // Pre-select the company's current representative so "keep current" is the
    // default; the picker can be overridden when there is more than one client.
    const currentRepId = selectedCompany.value?.authorizedRepId ?? null
    if (currentRepId != null && clientTeam.value.some(m => m.id === currentRepId)) {
      selectedRepId.value = String(currentRepId)
    } else if (clientTeam.value.length === 1) {
      // Exactly one client user: there is no choice to make — they are the rep.
      // (Overridable via "add a new one".)
      selectedRepId.value = String(clientTeam.value[0].id)
    }
  }
}

watch(selectedCompanyIdNum, (val, prev) => {
  if (val != null && val !== prev) void loadTeam()
})

async function loadLookups() {
  loadingLookups.value = true
  loadError.value = ''
  try {
    const [comps, svcs] = await Promise.all([useGetCompanies(), useGetServices()])
    existingCompanies.value = (comps as any[])
      .filter(c => c != null && c.id != null)
      .map(c => ({
        id: c.id,
        name: c.name,
        location: c.location ?? null,
        owner: c.owner ?? null,
        authorizedRepId: c.authorizedRepId ?? null,
        authorizedRepName: c.authorizedRepName ?? null,
      }))
    services.value = (svcs as any[])
      .filter(s => s != null && s.id != null && (s.isActive !== false))
      .map(s => ({ id: s.id, name: s.name, category: s.category ?? null }))
  } catch (e: any) {
    loadError.value = e?.response?.data?.message || 'Failed to load companies and services'
  } finally {
    loadingLookups.value = false
  }
}

// Steps:
//   new:      0 scenario -> 1 company details -> 2 representative -> 3 project
//   existing: 0 scenario -> 1 company + representative -> 2 project
const isScenarioNew = computed(() => scenario.value === 'new')
const totalSteps = computed(() => (isScenarioNew.value ? 4 : 3))

// Actions
const resetForm = () => {
  currentStep.value = 0
  scenario.value = 'new'
  companyForm.value = { name: '', location: '', owner: '', description: '' }
  repForm.value = { name: '', email: '' }
  projectForm.value = { name: '', serviceId: '', scope: '', dueDate: '' }
  selectedCompanyId.value = null
  selectedRepId.value = null
  clientTeam.value = []
  teamError.value = ''
  addingRep.value = false
  addRepForm.value = { name: '', email: '' }
  repError.value = ''
  loadError.value = ''
}

const nextStep = () => {
  loadError.value = ''
  if (isScenarioNew.value) {
    if (currentStep.value === 1 && !companyForm.value.name.trim()) {
      loadError.value = 'Company name is required.'
      return
    }
    if (currentStep.value === 2 && !newCustomerRepValid.value) {
      loadError.value = 'The representative name and email are required (the email becomes the company contact and invites them to the portal).'
      return
    }
  } else {
    if (currentStep.value === 1 && selectedCompanyId.value == null) {
      loadError.value = 'Select a customer company first.'
      return
    }
    if (currentStep.value === 1 && !repResolved.value) {
      loadError.value = repRequiredMessage.value
      return
    }
  }
  if (currentStep.value < totalSteps.value - 1) {
    currentStep.value++
  }
}

const prevStep = () => {
  loadError.value = ''
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

const selectScenario = (value: string) => {
  loadError.value = ''
  scenario.value = value === 'new' ? 'new' : 'existing'
  currentStep.value = 1
}

async function addRep() {
  repError.value = ''
  if (!newRepFormValid.value) {
    repError.value = 'Enter the new representative\u2019s full name and email.'
    return
  }
  const companyId = selectedCompanyIdNum.value
  if (companyId == null) return
  addRepBusy.value = true
  try {
    const created = (await useInviteCustomerRep(companyId, {
      name: addRepForm.value.name.trim(),
      email: addRepForm.value.email.trim(),
      setAsRep: true,
    })) as any
    addRepForm.value = { name: '', email: '' }
    addingRep.value = false
    // Refresh the team list, then pin the selection to the rep we just added:
    // loadTeam() re-preselects from the (stale) company cache, which still
    // points at the previous rep.
    await loadTeam()
    if (created?.id != null) selectedRepId.value = String(created.id)
    // Keep the cached company row in sync so the review step names the new rep.
    const comp = selectedCompany.value
    if (comp && created?.id != null) {
      comp.authorizedRepId = created.id
      comp.authorizedRepName = created.name ?? null
    }
  } catch (e: any) {
    repError.value = e?.response?.data?.message || 'Failed to add the new representative.'
  } finally {
    addRepBusy.value = false
  }
}

function validateProject(): string | null {
  if (!projectForm.value.name.trim()) return 'Project name is required.'
  if (!projectForm.value.scope.trim()) return 'Scope is required.'
  if (!projectForm.value.dueDate) return 'Due date is required.'
  return null
}

const handleSubmit = () => {
  const err = validateProject()
  if (err) {
    loadError.value = err
    return
  }
  const company =
    scenario.value === 'existing' && selectedCompany.value
      ? {
          name: selectedCompany.value.name,
          location: selectedCompany.value.location || '',
          owner: selectedCompany.value.owner || '',
          description: '',
        }
      : { ...companyForm.value }
  let rep: WizardData['rep']
  if (scenario.value === 'new') {
    rep = { name: repForm.value.name.trim(), email: repForm.value.email.trim(), userId: null }
  } else if (selectedTeamMember.value) {
    rep = { name: selectedTeamMember.value.name, email: selectedTeamMember.value.email, userId: selectedTeamMember.value.id }
  } else {
    // No one picked explicitly — keep the company's current rep. The step gate
    // guarantees repResolved here, so resolvedRep is non-null.
    const r = resolvedRep.value!
    rep = { name: r.name, email: r.email, userId: r.id }
  }
  const data: WizardData = {
    scenario: scenario.value,
    companyId: scenario.value === 'existing' ? selectedCompanyIdNum.value : null,
    company,
    rep,
    project: {
      name: projectForm.value.name.trim(),
      serviceId: projectForm.value.serviceId ? Number(projectForm.value.serviceId) : null,
      scope: projectForm.value.scope.trim(),
      dueDate: projectForm.value.dueDate,
    },
  }
  emit('submit', data)
  dialogOpen.value = false
}

const handleClose = () => {
  dialogOpen.value = false
  resetForm()
}
</script>

<template>
  <Dialog v-model:open="dialogOpen">
    <DialogContent class="max-w-3xl max-h-[90vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle class="text-2xl">New Project Creation Wizard</DialogTitle>
        <DialogDescription>
          Create a new project by following the steps below.
        </DialogDescription>
      </DialogHeader>

      <div v-if="loadError" class="mx-6 mt-4 bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
        {{ loadError }}
      </div>

      <div class="flex-1 overflow-y-auto">
        <!-- Scenario Selection (Step 0) -->
        <div v-if="currentStep === 0" class="space-y-6">
          <div class="text-center">
            <h3 class="text-lg font-semibold mb-2">What type of project is this?</h3>
            <p class="text-muted-foreground">Select whether this is for a new customer or an existing one.</p>
          </div>

          <div class="grid gap-4">
            <Card class="cursor-pointer hover:border-primary transition-colors" @click="selectScenario('new')">
              <CardHeader>
                <CardTitle>New Customer</CardTitle>
                <CardDescription>
                  Create a new customer company and project from scratch.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card class="cursor-pointer hover:border-primary transition-colors" @click="selectScenario('existing')">
              <CardHeader>
                <CardTitle>Existing Customer</CardTitle>
                <CardDescription>
                  Add a new project for an existing customer company.
                </CardDescription>
              </CardHeader>
            </Card>
          </div>
        </div>

        <!-- Existing: Company Selection + Representative (Step 1) -->
        <div v-if="currentStep === 1 && !isScenarioNew" class="space-y-4">
          <h3 class="text-lg font-semibold">Select Customer Company</h3>
          <div v-if="loadingLookups" class="text-sm text-muted-foreground">Loading companies…</div>
          <div v-else-if="existingCompanies.length === 0" class="text-sm text-muted-foreground">
            No customer companies on file yet — use the New Customer flow to create one.
          </div>
          <template v-else>
            <Select v-model="selectedCompanyId">
              <SelectTrigger>
                <SelectValue placeholder="Select a company..." />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem
                    v-for="company in existingCompanies"
                    :key="company.id"
                    :value="company.id.toString()"
                  >
                    {{ company.name }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>

            <div v-if="selectedCompany" class="mt-4 p-4 bg-muted rounded-lg">
              <h4 class="font-medium mb-2">{{ selectedCompany.name }}</h4>
              <div class="text-sm text-muted-foreground space-y-1">
                <p v-if="selectedCompany.location">Location: {{ selectedCompany.location }}</p>
                <p v-if="selectedCompany.owner">Owner: {{ selectedCompany.owner }}</p>
                <p v-if="selectedCompany.authorizedRepName">Current rep: {{ selectedCompany.authorizedRepName }}</p>
              </div>
            </div>

            <Separator class="my-4" />

            <div class="flex items-center justify-between">
              <h3 class="text-lg font-semibold">
                Authorized Representative <span class="text-sm font-normal text-red-500">*</span>
              </h3>
              <button
                type="button"
                class="text-sm font-medium text-emerald-700 hover:text-emerald-800 underline underline-offset-2"
                :class="{ 'pointer-events-none opacity-50': teamLoading || !!teamError }"
                @click="addingRep = !addingRep"
              >
                + Add a new representative
              </button>
            </div>
            <p class="text-sm text-muted-foreground">
              The authorized representative reviews this customer's onboarding, so one is required.
              Pick a client user from {{ selectedCompany?.name }}, or add a new one.
            </p>
            <div v-if="teamLoading" class="text-sm text-muted-foreground">Loading client users…</div>
            <div v-else-if="teamError" class="text-sm text-red-600">{{ teamError }}</div>
            <template v-else>
              <!-- 2+ client users: a picker to choose the reviewer -->
              <div v-if="showPicker" class="max-h-56 overflow-y-auto rounded-lg border divide-y">
                <label
                  v-for="member in clientTeam"
                  :key="member.id"
                  class="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-muted transition-colors"
                >
                  <input
                    type="radio"
                    name="authorizedRep"
                    class="h-4 w-4 accent-emerald-600"
                    :checked="selectedRepId === member.id.toString()"
                    @change="selectedRepId = member.id.toString()"
                  />
                  <span class="flex-1">
                    <span class="text-sm font-medium">{{ member.name }}</span>
                    <span class="text-xs text-muted-foreground"> · {{ member.email }}</span>
                  </span>
                  <span class="text-xs text-muted-foreground">{{ member.status }}</span>
                </label>
              </div>
              <!-- exactly one client user: auto-selected, no picker needed -->
              <div v-else-if="clientTeam.length === 1" class="rounded-lg border bg-muted/40 px-3 py-2 text-sm">
                <span class="font-medium">{{ clientTeam[0].name }}</span>
                <span class="text-xs text-muted-foreground"> · {{ clientTeam[0].email }}</span>
                <span class="text-xs text-emerald-700 ml-2">selected</span>
              </div>
              <!-- zero client users: must add one -->
              <div v-else class="text-sm text-muted-foreground">
                No client users on file for this company yet. Add a new representative to continue.
              </div>
            </template>
            <div v-if="!repResolved && !teamLoading && !teamError" class="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-3">
              {{ repRequiredMessage }}
            </div>
            <!-- Add-a-new-rep form (always available in the existing-customer flow) -->
            <div v-if="addingRep" class="rounded-lg border p-4 space-y-3 bg-muted/30">
              <div class="grid gap-3 sm:grid-cols-2">
                <div class="space-y-1">
                  <Label class="text-xs">Full Name *</Label>
                  <Input v-model="addRepForm.name" placeholder="Full name" />
                </div>
                <div class="space-y-1">
                  <Label class="text-xs">Email Address *</Label>
                  <Input v-model="addRepForm.email" type="email" placeholder="email@company.com" />
                </div>
              </div>
              <div v-if="repError" class="text-sm text-red-600">{{ repError }}</div>
              <div class="flex items-center gap-2">
                <Button size="sm" :disabled="addRepBusy" @click="addRep()">
                  {{ addRepBusy ? 'Adding…' : 'Add & set as representative' }}
                </Button>
                <Button size="sm" variant="ghost" @click="addingRep = false">Cancel</Button>
              </div>
              <p class="text-xs text-muted-foreground">
                This sends the representative an invite to the portal and makes them the company's
                authorized representative.
              </p>
            </div>
          </template>
        </div>

        <!-- New: Company Details (Step 1) -->
        <div v-if="currentStep === 1 && isScenarioNew" class="space-y-4">
          <h3 class="text-lg font-semibold">Customer Company Details</h3>

          <div class="space-y-2">
            <Label for="companyName">Company Name *</Label>
            <Input id="companyName" v-model="companyForm.name" placeholder="Enter company name" />
          </div>

          <div class="space-y-2">
            <Label for="location">Location</Label>
            <Input id="location" v-model="companyForm.location" placeholder="City, Province" />
          </div>

          <div class="space-y-2">
            <Label for="owner">Company Owner</Label>
            <Input id="owner" v-model="companyForm.owner" placeholder="Owner's full name" />
          </div>

          <div class="space-y-2">
            <Label for="companyDesc">Business Description</Label>
            <Textarea id="companyDesc" v-model="companyForm.description" placeholder="Describe the company's business" rows="3" />
          </div>
        </div>

        <!-- New: Representative (Step 2) -->
        <div v-if="currentStep === 2 && isScenarioNew" class="space-y-4">
          <h3 class="text-lg font-semibold">Authorized Representative</h3>
          <p class="text-sm text-muted-foreground">
            The primary contact for this customer. Their email becomes the company contact and is used to invite them to the portal.
          </p>

          <div class="space-y-2">
            <Label for="fullName">Full Name *</Label>
            <Input id="fullName" v-model="repForm.name" placeholder="Enter full name" />
          </div>

          <div class="space-y-2">
            <Label for="email">Email Address *</Label>
            <Input id="email" v-model="repForm.email" type="email" placeholder="email@company.com" />
          </div>
        </div>

        <!-- Project (last step for both) -->
        <div v-if="currentStep === (isScenarioNew ? 3 : 2)" class="space-y-4">
          <h3 class="text-lg font-semibold">Project Overview</h3>

          <div class="space-y-2">
            <Label for="projectName">Project Name *</Label>
            <Input id="projectName" v-model="projectForm.name" placeholder="Enter project name" />
          </div>

          <div class="space-y-2">
            <Label for="service">Service Type</Label>
            <Select v-model="projectForm.serviceId">
              <SelectTrigger>
                <SelectValue placeholder="Select a service (optional)..." />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <!-- sentinel value: shadcn SelectItem rejects value="" (used to clear the model) -->
                  <SelectItem value="none" disabled class="hidden" />
                  <SelectItem v-for="service in services" :key="service.id" :value="service.id.toString()">
                    {{ service.name }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>

          <div class="space-y-2">
            <Label for="scope">Scope *</Label>
            <Textarea v-model="projectForm.scope" placeholder="What will be done for this project?" rows="3" />
          </div>

          <div class="space-y-2">
            <Label for="dueDate">Due Date *</Label>
            <Input id="dueDate" v-model="projectForm.dueDate" type="date" />
          </div>

          <!-- Review summary -->
          <div class="space-y-3 pt-2">
            <h4 class="text-sm font-semibold text-muted-foreground uppercase">Review</h4>
            <Card>
              <CardHeader>
                <CardTitle class="text-base">Customer Company</CardTitle>
              </CardHeader>
              <CardContent class="space-y-1 text-sm">
                <p><strong>Name:</strong> {{ isScenarioNew ? companyForm.name : selectedCompany?.name }}</p>
                <p v-if="(isScenarioNew ? companyForm.location : selectedCompany?.location)">
                  <strong>Location:</strong> {{ isScenarioNew ? companyForm.location : selectedCompany?.location }}
                </p>
                <p v-if="(isScenarioNew ? companyForm.owner : selectedCompany?.owner)">
                  <strong>Owner:</strong> {{ isScenarioNew ? companyForm.owner : selectedCompany?.owner }}
                </p>
              </CardContent>
            </Card>
            <Card v-if="(isScenarioNew && (repForm.name || repForm.email)) || (!isScenarioNew && resolvedRep)">
              <CardHeader>
                <CardTitle class="text-base">Authorized Representative</CardTitle>
              </CardHeader>
              <CardContent class="space-y-1 text-sm">
                <p><strong>Name:</strong> {{ isScenarioNew ? (repForm.name || '—') : (resolvedRep?.name || '—') }}</p>
                <p><strong>Email:</strong> {{ isScenarioNew ? (repForm.email || '—') : (resolvedRep?.email || '—') }}</p>
              </CardContent>
            </Card>
            <div class="bg-emerald-50 border border-emerald-200 rounded-lg p-4">
              <p class="text-sm text-emerald-800" v-if="isScenarioNew">
                <strong>Next steps:</strong> The contact email is saved on the customer's company
                profile. Once the customer activates a portal account with that address, they become
                the company's authorized representative and can see this project in their workspace.
              </p>
              <p class="text-sm text-emerald-800" v-else-if="selectedTeamMember && selectedRepId">
                <strong>Next steps:</strong> {{ selectedTeamMember.name }} ({{ selectedTeamMember.email }})
                becomes the company's authorized representative and will see this project in their workspace.
              </p>
              <p class="text-sm text-emerald-800" v-else>
                <strong>Next steps:</strong> {{ (resolvedRep?.name || `The company's current representative`) }} will
                review this customer's onboarding and see this project in their workspace.
              </p>
            </div>
          </div>
        </div>
      </div>

      <Separator />

      <!-- Footer with navigation -->
      <DialogFooter class="flex flex-col sm:flex-row gap-2 sm:justify-between">
        <Button variant="outline" @click="handleClose">Cancel</Button>

        <div class="flex gap-2">
          <Button v-if="currentStep > 0" variant="outline" @click="prevStep">
            Previous
          </Button>

          <Button v-if="currentStep < totalSteps - 1" @click="nextStep">
            Next
          </Button>

          <Button v-if="currentStep === totalSteps - 1" @click="handleSubmit">
            Submit Wizard
          </Button>
        </div>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
