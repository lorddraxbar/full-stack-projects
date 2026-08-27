<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Trash2, Check } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
 * serviceId is a required real Service id from /services; notes is free text (optional).
 * The Production Details step captures the project & production checklist:
 * totalCost (required), raw materials / production output / waste materials
 * (structured lists — ProjectsView serializes them to JSONB strings),
 * waste-management and manufacturing-procedure free text, and an optional
 * flowchart file the parent attaches as a project document after creation.
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
    serviceId: number
    notes: string
    /** Where the business physically operates. When `addressDiffers` is
     *  false this is null and the company address (above) is the address. */
    address: string | null
    /** Checked when the project operates somewhere other than the company address. */
    addressDiffers: boolean
    /** Total project cost in PHP (estimated or actual) — required. */
    totalCost: number | null
    rawMaterials: { name: string; quantity: number | null; period: 'MONTHLY' | 'YEARLY' }[] | null
    productionOutput: { name: string; monthlyTons: number | null; annualTons: number | null }[] | null
    wasteManagement: string
    wasteMaterials: { type: string; recyclable: boolean; monthlyTons: number | null }[] | null
    manufacturingProcedure: string
    /** Flowchart file (PDF/PNG/JPG/SVG) to attach as a project document; optional. */
    flowchart: File | null
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

// Visible-viewport max-height. On phones, CSS `vh` is the FULL layout
// viewport including the region behind the browser address bar / home
// indicator, so a `90vh` dialog is taller than what's actually visible and
// its bottom edge gets clipped by the browser chrome (the "trimmed" look).
// dvh tracks the real visible height; 90vh stays as the fallback for
// browsers without dvh support. Set inline so the declaration wins the
// cascade over any utility classes (tailwind-merge would otherwise drop
// duplicate max-h utilities).
const dialogMaxH = computed(() =>
  typeof CSS !== 'undefined' && CSS.supports?.('max-height', '1dvh')
    ? { maxHeight: '90dvh' }
    : { maxHeight: '90vh' },
)

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
  notes: '',
  address: '',
  addressDiffers: false,
})

// Project & production details (production step). Total cost is the only
// required field — the rest of the checklist can be left blank and completed
// later from the project's detail page.
const productionForm = ref({
  totalCost: '',
  rawMaterials: [] as { name: string; quantity: string; period: 'MONTHLY' | 'YEARLY' }[],
  productionOutput: [] as { name: string; monthlyTons: string; annualTons: string }[],
  wasteManagement: '',
  wasteMaterials: [] as { type: string; recyclable: boolean; monthlyTons: string }[],
  manufacturingProcedure: '',
  flowchart: null as File | null,
})

function addRow(kind: 'rawMaterials' | 'productionOutput' | 'wasteMaterials') {
  if (kind === 'rawMaterials') {
    productionForm.value.rawMaterials.push({ name: '', quantity: '', period: 'MONTHLY' })
  } else if (kind === 'productionOutput') {
    productionForm.value.productionOutput.push({ name: '', monthlyTons: '', annualTons: '' })
  } else {
    productionForm.value.wasteMaterials.push({ type: '', recyclable: true, monthlyTons: '' })
  }
}

function removeRow(kind: 'rawMaterials' | 'productionOutput' | 'wasteMaterials', i: number) {
  productionForm.value[kind].splice(i, 1)
}

const onFlowchartFile = (e: Event) => {
  productionForm.value.flowchart = (e.target as HTMLInputElement).files?.[0] ?? null
}

const countRows = (kind: 'rawMaterials' | 'productionOutput' | 'wasteMaterials') =>
  productionForm.value[kind].filter(r => (kind === 'wasteMaterials' ? (r as any).type : (r as any).name).trim())

// The structured rows' numeric fields are type="number" inputs bound with
// v-model, so Vue coerces them to numbers (and '' when cleared). Map any
// number-or-string to a number or null — never call .trim() on them.
const numOrNull = (v: string | number | null | undefined): number | null => {
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

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
    } else if (clientTeam.value.length > 0) {
      // No rep on file yet: preselect the first client user as the default so
      // there's always a highlighted choice. Still overridable via the picker
      // (or "add a new one") — the rep is required, and a default satisfies it.
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
//   new:      0 scenario -> 1 company details -> 2 representative -> 3 project -> 4 production details
//   existing: 0 scenario -> 1 company + representative -> 2 project -> 3 production details
const isScenarioNew = computed(() => scenario.value === 'new')
const totalSteps = computed(() => (isScenarioNew.value ? 5 : 4))
const isProductionStep = computed(() => currentStep.value === (isScenarioNew.value ? 4 : 3))

// Human step names for the stepper. The scenario step (0) is shared; after
// that the label depends on which scenario was chosen.
const stepLabels = computed<string[]>(() =>
  isScenarioNew.value
    ? ['Type', 'Company', 'Representative', 'Project', 'Details']
    : ['Type', 'Company & Rep', 'Project', 'Details'],
)
const stepIndex = computed(() => currentStep.value)

// Actions
const resetForm = () => {
  currentStep.value = 0
  scenario.value = 'new'
  companyForm.value = { name: '', location: '', owner: '', description: '' }
  repForm.value = { name: '', email: '' }
  projectForm.value = { name: '', serviceId: '', notes: '', address: '', addressDiffers: false }
  productionForm.value = {
    totalCost: '',
    rawMaterials: [],
    productionOutput: [],
    wasteManagement: '',
    wasteMaterials: [],
    manufacturingProcedure: '',
    flowchart: null,
  }
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
  // Leaving the project step requires the service (and project name). The
  // project step is not the final step anymore (production is), so handleSubmit
  // only re-checks production — this gate is what enforces 'Service required'.
  const projectStep = isScenarioNew.value ? 3 : 2
  if (currentStep.value === projectStep) {
    const err = validateProject()
    if (err) {
      loadError.value = err
      return
    }
  }
  // The production-details step always gates on total cost (both scenarios —
  // it is the only required field on that step; the rest can be completed
  // later from the project's detail page).
  if (isProductionStep.value) {
    const err = validateProduction()
    if (err) {
      loadError.value = err
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
  // Selection only — the user stays on the Scenario step until they press
  // Next (the active ring shows which flow was picked).
  scenario.value = value === 'new' ? 'new' : 'existing'
}

async function addRep() {
  repError.value = ''
  if (!newRepFormValid.value) {
    repError.value = 'Enter the new representative\u2019s full name and email.'
    return
  }
  const companyId = selectedCompanyIdNum.value
  if (companyId == null) {
    repError.value = 'Select a customer company first.'
    return
  }
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
  if (!projectForm.value.serviceId) return 'Service type is required.'
  if (projectForm.value.addressDiffers && !projectForm.value.address.trim()) {
    return 'Enter the project address, or uncheck "Project address is different from company address".'
  }
  return null
}

// The production step is the wizard's last one — re-check it in handleSubmit
// too, so a submit from any state can't bypass the total-cost gate.
function validateProduction(): string | null {
  // totalCost is bound with v-model on a type="number" input, so Vue coerces
  // it to a number (and leaves '' when cleared) — never assume a string here.
  const raw = productionForm.value.totalCost
  const empty = raw == null || raw === ''
  const cost = empty ? NaN : Number(raw)
  if (empty || !isFinite(cost) || cost < 0) {
    return 'Total project cost is required (PHP, estimated or actual).'
  }
  return null
}

const handleSubmit = () => {
  const err = isProductionStep.value ? validateProduction() : validateProject()
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
      // validateProject() guarantees a service is selected before this runs.
      serviceId: Number(projectForm.value.serviceId),
      notes: projectForm.value.notes.trim(),
      // When the project address differs from the company's, the checkbox is
      // checked and this holds the full project address; otherwise null and
      // the company address is used.
      address: projectForm.value.addressDiffers ? projectForm.value.address.trim() : null,
      addressDiffers: projectForm.value.addressDiffers,
      totalCost: Number(productionForm.value.totalCost),
      rawMaterials: countRows('rawMaterials').length
        ? productionForm.value.rawMaterials
            .filter(r => r.name.trim())
            .map(r => ({
              name: r.name.trim(),
              quantity: numOrNull(r.quantity),
              period: r.period,
            }))
            : null,
      productionOutput: countRows('productionOutput').length
        ? productionForm.value.productionOutput
            .filter(r => r.name.trim())
            .map(r => ({
              name: r.name.trim(),
              monthlyTons: numOrNull(r.monthlyTons),
              annualTons: numOrNull(r.annualTons),
            }))
        : null,
      wasteManagement: productionForm.value.wasteManagement.trim(),
      wasteMaterials: countRows('wasteMaterials').length
        ? productionForm.value.wasteMaterials
            .filter(r => r.type.trim())
            .map(r => ({
              type: r.type.trim(),
              recyclable: r.recyclable,
              monthlyTons: numOrNull(r.monthlyTons),
            }))
        : null,
      manufacturingProcedure: productionForm.value.manufacturingProcedure.trim(),
      flowchart: productionForm.value.flowchart,
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
    <!-- max-height is set inline (dialogMaxH): on phones the CSS `vh` unit is
         the FULL layout viewport including the area behind the browser address
         bar, so a 90vh dialog is taller than the visible area and its bottom
         edge gets clipped by the browser chrome (the "trimmed" look). dvh
         tracks the visible height; 90vh remains the fallback for browsers
         without dvh support. -->
    <DialogContent :style="dialogMaxH" class="overflow-hidden flex flex-col max-w-[min(896px,calc(100vw-2rem))] sm:max-w-[min(896px,calc(100vw-2rem))] p-4 sm:p-6 gap-3 sm:gap-6">
      <DialogHeader class="gap-1.5 sm:gap-2">
        <DialogTitle class="text-lg sm:text-2xl">New Project Creation Wizard</DialogTitle>
        <DialogDescription class="text-xs sm:text-sm">
          Create a new project by following the steps below.
        </DialogDescription>
      </DialogHeader>

      <!-- Stepper: compact "Step X of Y" + dots on mobile, full numbered
           circles with labels on desktop. Shows how far along the user is. -->
      <div class="px-6 pt-1" aria-label="Wizard progress">
        <!-- Mobile: text + dot trail -->
        <div class="flex items-center justify-between gap-3 sm:hidden">
          <span class="text-sm font-medium">
            Step {{ stepIndex + 1 }} of {{ totalSteps }}
            <span class="text-muted-foreground">· {{ stepLabels[stepIndex] }}</span>
          </span>
          <div class="flex items-center gap-1.5">
            <span
              v-for="s in totalSteps"
              :key="'m' + s"
              class="h-1.5 rounded-full transition-all"
              :class="s - 1 === stepIndex ? 'w-5 bg-primary' : s - 1 < stepIndex ? 'w-1.5 bg-primary/60' : 'w-1.5 bg-border'"
            />
          </div>
        </div>
        <!-- Desktop: numbered circles with labels beneath and connector
             lines behind the circles. Labels wrap under each circle so long
             names (e.g. "Company & Rep") never overflow the dialog width. -->
        <ol class="hidden sm:flex sm:items-start">
          <li
            v-for="(label, i) in stepLabels"
            :key="'d' + i"
            class="relative flex flex-1 flex-col items-center"
          >
            <!-- connector from the previous circle to this one: spans the full
                 gap between the two circle edges (1px overlap at each end,
                 hidden under the z-10 circles) so the line is continuous -->
            <span
              v-if="i > 0"
              class="absolute top-3.5 h-px left-[calc(-50%_+13px)] right-[calc(50%_+13px)]"
              :class="i <= stepIndex ? 'bg-primary/50' : 'bg-border'"
            />
            <span
              class="relative z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs font-semibold transition-colors"
              :class="i === stepIndex
                ? 'border-primary bg-primary text-primary-foreground'
                : i < stepIndex
                  ? 'border-primary/50 bg-primary/10 text-primary'
                  : 'border-border bg-background text-muted-foreground'"
            >
              <Check v-if="i < stepIndex" class="h-4 w-4" />
              <template v-else>{{ i + 1 }}</template>
            </span>
            <span
              class="mt-1.5 px-1 text-center text-xs leading-tight"
              :class="i === stepIndex ? 'font-semibold text-foreground' : 'text-muted-foreground'"
            >{{ label }}</span>
          </li>
        </ol>
      </div>

      <div v-if="loadError" class="mx-6 mt-4 bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
        {{ loadError }}
      </div>

      <!-- p-1.5 clearance for Card rings: the active card's ring is a 2px
           box-shadow offset 2px OUTSIDE the border (4px total beyond the
           card edge), and this container's `overflow-y-auto` clips
           box-shadows at its padding-box edge. With no padding, the ring
           was drawn only inside the card — the bottom edge of the lower
           card (and the top of the upper one) lost its whole green
           outline ("trimming"). 6px > 4px on every side, so the full
           ring renders. -->
      <div class="flex-1 overflow-y-auto p-1.5">
        <!-- Scenario Selection (Step 0) -->
        <div v-if="currentStep === 0" class="space-y-2">
          <div class="text-center">
            <h3 class="text-base font-semibold mb-1">What type of customer is this?</h3>
            <p class="text-sm text-muted-foreground">Select whether this is for a new customer or an existing one.</p>
          </div>

          <!-- size="sm" + tight spacing keep both cards fully inside the
               scroll area even on short phone viewports (no bottom clipping). -->
          <div class="grid gap-2">
            <Card size="sm" class="cursor-pointer transition hover:ring-primary/40" :active="scenario === 'new'" @click="selectScenario('new')">
              <CardHeader>
                <CardTitle class="text-sm">New Customer</CardTitle>
                <CardDescription>
                  Create a new customer company and project from scratch.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card size="sm" class="cursor-pointer transition hover:ring-primary/40" :active="scenario === 'existing'" @click="selectScenario('existing')">
              <CardHeader>
                <CardTitle class="text-sm">Existing Customer</CardTitle>
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
                <p v-if="selectedCompany.location">Company Address: {{ selectedCompany.location }}</p>
                <p v-if="selectedCompany.owner">Owner: {{ selectedCompany.owner }}</p>
                <p v-if="selectedCompany.authorizedRepName">Current rep: {{ selectedCompany.authorizedRepName }}</p>
              </div>
            </div>

            <!-- Rep selection only makes sense for a chosen company. Until one is
                 picked, the add-rep link/picker would target nothing, so they stay
                 hidden and a placeholder nudges toward selecting a company first. -->
            <Separator class="my-4" />
            <div v-if="!selectedCompany" class="text-sm text-muted-foreground">
              Pick a customer company above to choose its authorized representative.
            </div>
            <template v-else>
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
              Pick a client user from {{ selectedCompany.name }}, or add a new one.
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
            <Label for="location">Company Address</Label>
            <Input id="location" v-model="companyForm.location" placeholder="Full address (barangay, city/municipality, province, ZIP)" />
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
            <Label for="service">Service Type *</Label>
            <Select v-model="projectForm.serviceId">
              <SelectTrigger>
                <SelectValue placeholder="Select a service..." />
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
            <Label for="notes">Notes</Label>
            <Textarea v-model="projectForm.notes" placeholder="Anything else the team should know (optional)..." rows="3" />
          </div>

          <div class="space-y-2">
            <div class="flex items-center gap-2">
              <Checkbox id="addressDiffers" :model-value="projectForm.addressDiffers" @update:model-value="(v: unknown) => projectForm.addressDiffers = v === true" />
              <Label for="addressDiffers" class="cursor-pointer">Project address is different from company address</Label>
            </div>
            <p class="text-sm text-muted-foreground">
              Leave unchecked if the project operates at the company address
              {{ isScenarioNew ? '(entered above' : '(on the company profile' }}).
            </p>
            <div v-if="projectForm.addressDiffers" class="space-y-2">
              <Label for="projectAddress">Project Address *</Label>
              <Textarea id="projectAddress" v-model="projectForm.address"
                placeholder="Full address where this project operates (barangay, city/municipality, province, ZIP)" rows="2" />
            </div>
          </div>
        </div>

        <!-- Production Details (wizard's last step for both scenarios) -->
        <div v-if="isProductionStep" class="space-y-4">
          <h3 class="text-lg font-semibold">Production Details</h3>
          <p class="text-sm text-muted-foreground -mt-2">
            Total project cost is required; the rest of the checklist is optional and can be
            completed later from the project's detail page.
          </p>

          <!-- Project & production -->
          <Card>
            <CardHeader>
              <CardTitle class="text-base">Project &amp; Production</CardTitle>
              <CardDescription>Total project cost (estimated or actual investment)</CardDescription>
            </CardHeader>
            <CardContent>
              <div class="space-y-2">
                <Label for="totalCost">Total Project Cost (₱) *</Label>
                <Input id="totalCost" v-model="productionForm.totalCost" type="number" min="0" step="any" placeholder="e.g. 2500000" />
              </div>
            </CardContent>
          </Card>

          <!-- Raw materials -->
          <Card>
            <CardHeader class="flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle class="text-base">Raw Materials</CardTitle>
                <CardDescription>Each material with quantity used or purchased, in tons</CardDescription>
              </div>
              <Button type="button" variant="outline" size="sm" @click="addRow('rawMaterials')">+ Add material</Button>
            </CardHeader>
            <CardContent class="space-y-2">
              <p v-if="productionForm.rawMaterials.length === 0" class="text-sm text-muted-foreground">No raw materials added yet.</p>
              <div v-for="(row, i) in productionForm.rawMaterials" :key="'raw-' + i" class="flex flex-wrap items-end gap-2">
                <div class="flex-1 min-w-40 space-y-1">
                  <Label>Material name</Label>
                  <Input v-model="row.name" placeholder="e.g. Virgin LDPE resin" />
                </div>
                <div class="w-32 space-y-1">
                  <Label>Quantity (tons)</Label>
                  <Input v-model="row.quantity" type="number" min="0" step="any" placeholder="0" />
                </div>
                <div class="w-28 space-y-1">
                  <Label>Period</Label>
                  <Select :model-value="row.period" @update:model-value="(v) => (row.period = v as 'MONTHLY' | 'YEARLY')">
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="MONTHLY">Per month</SelectItem>
                      <SelectItem value="YEARLY">Per year</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <Button type="button" variant="ghost" size="icon" class="text-muted-foreground hover:text-red-600" @click="removeRow('rawMaterials', i)">
                  <Trash2 class="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>

          <!-- Production output -->
          <Card>
            <CardHeader class="flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle class="text-base">Production Output</CardTitle>
                <CardDescription>Finished products with annual volume in tons</CardDescription>
              </div>
              <Button type="button" variant="outline" size="sm" @click="addRow('productionOutput')">+ Add product</Button>
            </CardHeader>
            <CardContent class="space-y-2">
              <p v-if="productionForm.productionOutput.length === 0" class="text-sm text-muted-foreground">No products added yet.</p>
              <div v-for="(row, i) in productionForm.productionOutput" :key="'out-' + i" class="flex flex-wrap items-end gap-2">
                <div class="flex-1 min-w-40 space-y-1">
                  <Label>Product name</Label>
                  <Input v-model="row.name" placeholder="e.g. HDPE bags, 300mm" />
                </div>
                <div class="w-32 space-y-1">
                  <Label>Monthly (tons)</Label>
                  <Input v-model="row.monthlyTons" type="number" min="0" step="any" placeholder="0" />
                </div>
                <div class="w-32 space-y-1">
                  <Label>Annual (tons)</Label>
                  <Input v-model="row.annualTons" type="number" min="0" step="any" placeholder="0" />
                </div>
                <Button type="button" variant="ghost" size="icon" class="text-muted-foreground hover:text-red-600" @click="removeRow('productionOutput', i)">
                  <Trash2 class="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>

          <!-- Waste management -->
          <Card>
            <CardHeader class="flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle class="text-base">Waste Management</CardTitle>
                <CardDescription>How wastes are managed and how much is generated per month</CardDescription>
              </div>
              <Button type="button" variant="outline" size="sm" @click="addRow('wasteMaterials')">+ Add waste type</Button>
            </CardHeader>
            <CardContent class="space-y-3">
              <div class="space-y-2">
                <Label for="wasteManagement">Waste management practices</Label>
                <Textarea id="wasteManagement" v-model="productionForm.wasteManagement" rows="3" placeholder="Recyclable materials: describe processes and quantities. Non-recyclable: describe disposal methods and quantities..." />
              </div>
              <div class="space-y-2">
                <Label>Waste materials per month (tons)</Label>
                <p v-if="productionForm.wasteMaterials.length === 0" class="text-sm text-muted-foreground">No waste types added yet.</p>
                <div v-for="(row, i) in productionForm.wasteMaterials" :key="'waste-' + i" class="flex flex-wrap items-end gap-2">
                  <div class="flex-1 min-w-40 space-y-1">
                    <Label>Type</Label>
                    <Input v-model="row.type" placeholder="e.g. Non-recyclable film scraps" />
                  </div>
                  <div class="w-36 space-y-1">
                    <Label>Monthly (tons)</Label>
                    <Input v-model="row.monthlyTons" type="number" min="0" step="any" placeholder="0" />
                  </div>
                  <div class="flex items-center gap-2 pb-1.5">
                    <Checkbox id="recyclable" :model-value="row.recyclable" @update:model-value="(v: unknown) => (row.recyclable = v === true)" />
                    <Label for="recyclable" class="text-sm font-normal">Recyclable</Label>
                  </div>
                  <Button type="button" variant="ghost" size="icon" class="text-muted-foreground hover:text-red-600" @click="removeRow('wasteMaterials', i)">
                    <Trash2 class="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>

          <!-- Manufacturing process -->
          <Card>
            <CardHeader>
              <CardTitle class="text-base">Manufacturing Process</CardTitle>
              <CardDescription>Step-by-step procedure and a visual flowchart of the production process</CardDescription>
            </CardHeader>
            <CardContent class="space-y-3">
              <div class="space-y-2">
                <Label for="manufacturingProcedure">Manufacturing procedure</Label>
                <Textarea id="manufacturingProcedure" v-model="productionForm.manufacturingProcedure" rows="4" placeholder="Describe each production stage — processing methods, equipment used, quality control measures..." />
              </div>
              <div class="space-y-2">
                <Label>Production flowchart (PDF, PNG, JPG, SVG)</Label>
                <input type="file" accept=".pdf,.png,.jpg,.jpeg,.svg,image/png,image/jpeg,image/svg+xml,application/pdf" @change="onFlowchartFile" class="block w-full text-sm file:mr-3 file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-2 file:text-sm file:font-medium" />
                <p v-if="productionForm.flowchart" class="text-sm text-muted-foreground">
                  Selected: <strong>{{ productionForm.flowchart.name }}</strong> — it will be attached as a project document after creation.
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <Separator />

      <!-- Footer with navigation. At ALL sizes the buttons share a single
           horizontal row; on mobile each is flex-1 so Cancel / Previous /
           Next split the width evenly (1/3 each), and on desktop the group is
           right-aligned with Cancel on the left. The nav group is
           `display:contents` on mobile so its buttons join the footer's flex
           row directly, while staying a flex group on desktop. -->
      <DialogFooter class="flex flex-row gap-2 sm:justify-between">
        <Button
          variant="outline"
          class="flex-1 sm:flex-none whitespace-normal"
          @click="handleClose"
        >
          Cancel
        </Button>

        <div class="contents sm:flex sm:gap-2">
          <Button
            v-if="currentStep > 0"
            variant="outline"
            class="flex-1 sm:flex-none whitespace-normal"
            @click="prevStep"
          >
            Previous
          </Button>

          <Button
            v-if="currentStep < totalSteps - 1"
            class="flex-1 sm:flex-none whitespace-normal"
            @click="nextStep"
          >
            Next
          </Button>

          <Button
            v-if="currentStep === totalSteps - 1"
            class="flex-1 sm:flex-none whitespace-normal"
            @click="handleSubmit"
          >
            Submit Wizard
          </Button>
        </div>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
