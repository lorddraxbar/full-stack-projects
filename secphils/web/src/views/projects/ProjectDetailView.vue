<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRole } from '@/composables/useRole'
import RowActionsMenu from '@/components/RowActionsMenu.vue'
import BackToListButton from '@/components/BackToListButton.vue'
import {
  useGetMe, useGetProject, useGetCompany, useGetProjectTeam,
  useGetDocuments, useCreateDocument, useDeleteDocument,
  useGetMessages, useSendMessage, useUpdateProject,
  useArchiveProject, useRestoreProject, useHardDeleteProject,
  useUpdateCompany, useUpdateUser,
} from '@/services/api'
import {
  projectStatusLabel, fileTypeLabel,
  PROJECT_STATUS_COLORS, FILE_TYPE_COLORS,
  formatDate, formatDateTime, formatPhp,
} from '@/lib/labels'

const { isClient, isAdmin } = useRole()
const isUser = computed(() => !isClient.value && !isAdmin.value)
const route = useRoute()
const projectId = computed(() => Number(route.params.id))

const me = ref<{ id: number; fullName: string; role: string } | null>(null)
const project = ref<any>(null)
const company = ref<any>(null)
const team = ref<{ userId: number; fullName: string; role: string }[]>([])
const documents = ref<any[]>([])
const messages = ref<any[]>([])
const loading = ref(true)
const loadError = ref('')
const saveError = ref('')

// ---------- Role-based tabs ----------
const tabs = computed(() => {
  const base = ['Overview', 'Production', 'Documents', 'Messages']
  if (isClient.value) return [...base, 'Team']
  // Provider side (admin or staff user) both get the Administration tab.
  if (isUser.value || isAdmin.value) {
    return ['Overview', 'Production', 'Documents', 'Messages', 'Company', 'Administration']
  }
  return base
})
const activeTab = ref('Overview')

// ---------- Production checklist (wizard step, JSONB columns) ----------
function tryParseJson(raw: any): any[] | null {
  if (!raw) return null
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : null
  } catch {
    return null
  }
}
const rawMaterials = computed(() => tryParseJson(project.value?.rawMaterials))
const productionOutput = computed(() => tryParseJson(project.value?.productionOutput))
const wasteMaterials = computed(() => tryParseJson(project.value?.wasteMaterials))
const hasProductionData = computed(() => {
  const p = project.value
  return !!p && (
    p.totalCost != null ||
    p.wasteManagement ||
    p.manufacturingProcedure ||
    p.productionFlowchartUrl ||
    (rawMaterials.value && rawMaterials.value.length > 0) ||
    (productionOutput.value && productionOutput.value.length > 0) ||
    (wasteMaterials.value && wasteMaterials.value.length > 0)
  )
})

// The authorized rep can only mark a project complete once the production
// checklist is satisfied: total cost (never skippable) plus each editable
// section either filled OR explicitly flagged "Not applicable" (a client
// with no waste stream shouldn't be blocked). The flowchart is NOT part of
// the gate — it's an optional attached document, not a checklist field.
// Flags live server-side in projects.checklist_na (jsonb, V32).
const CHECKLIST_SECTIONS = [
  { key: 'rawMaterials', label: 'raw materials' },
  { key: 'productionOutput', label: 'production output' },
  { key: 'wasteManagement', label: 'waste management practices' },
  { key: 'wasteMaterials', label: 'waste types' },
  { key: 'manufacturingProcedure', label: 'the manufacturing procedure' },
]
const checklistNa = computed(() => {
  try {
    const obj = JSON.parse((project.value?.checklistNa as string) || '{}')
    return obj && typeof obj === 'object' && !Array.isArray(obj) ? obj as Record<string, boolean> : {}
  } catch {
    return {}
  }
})
function sectionFilled(key: string): boolean {
  switch (key) {
    case 'rawMaterials': return !!(rawMaterials.value && rawMaterials.value.length > 0)
    case 'productionOutput': return !!(productionOutput.value && productionOutput.value.length > 0)
    case 'wasteMaterials': return !!(wasteMaterials.value && wasteMaterials.value.length > 0)
    case 'wasteManagement': return !!((project.value?.wasteManagement || '').trim())
    case 'manufacturingProcedure': return !!((project.value?.manufacturingProcedure || '').trim())
    default: return false
  }
}
const productionChecklistComplete = computed(() => {
  const p = project.value
  if (!p || p.totalCost == null) return false
  return CHECKLIST_SECTIONS.every(s => sectionFilled(s.key) || !!checklistNa.value[s.key])
})
const nasWithoutContent = computed(() =>
  CHECKLIST_SECTIONS.filter(s => !sectionFilled(s.key) && !!checklistNa.value[s.key])
)
// Actionable nudge for the review card: tells the rep exactly what's still
// missing (and that a missing section can be marked not applicable).
const checklistNudge = computed(() => {
  const p = project.value
  const missing = CHECKLIST_SECTIONS.filter(s => !sectionFilled(s.key) && !checklistNa.value[s.key]).map(s => s.label)
  if (missing.length > 0) return `Missing: ${missing.join(', ')}. Fill them in or mark them not applicable.`
  if (!p || p.totalCost == null) return 'The total project cost is required before this project can be completed.'
  return ''
})

function initials(name: string): string {
  return name.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase()
}

function isMine(msg: any): boolean {
  return me.value != null && msg.senderId === me.value.id
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [meRes, projRes] = await Promise.all([useGetMe(), useGetProject(projectId.value)])
    // GET /users/me returns the UserResponse body directly (no envelope)
    me.value = meRes || null
    project.value = projRes
    // Initialize the admin form only once the project has loaded
    initAdminForm()

    const [teamRes, docsRes, msgsRes] = await Promise.all([
      useGetProjectTeam(projectId.value).catch(() => []),
      useGetDocuments({ projectId: projectId.value }).catch(() => []),
      useGetMessages(projectId.value).catch(() => []),
    ])
    team.value = (Array.isArray(teamRes) ? teamRes : []).map((m: any) => ({
      userId: m.userId, fullName: m.fullName, role: m.role,
    }))
    documents.value = Array.isArray(docsRes) ? docsRes : []
    messages.value = Array.isArray(msgsRes) ? msgsRes : []

    if (project.value?.companyId) {
      company.value = await useGetCompany(project.value.companyId).catch(() => null)
    }

    // Deep-link: /projects/{id}?edit=1 opens the Production tab in edit mode
    // straight away (used by the "edit these details" affordances).
    if (route.query.edit) {
      activeTab.value = 'Production'
      startProductionEdit()
    }
  } catch (err: any) {
    loadError.value = err?.response?.data?.message || err?.message || 'Failed to load project'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------- Archive lifecycle (soft delete / restore / hard delete) ----------
const router = useRouter()
const lifecycleBusy = ref(false)
const archived = computed(() => project.value?.status === 'ARCHIVED')

async function archiveProject() {
  if (!confirm('Archive this project? It will be hidden and its files moved into the archive. You can restore it within the grace period.')) return
  lifecycleBusy.value = true
  saveError.value = ''
  try {
    await useArchiveProject(projectId.value)
    await load()
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to archive project'
  } finally {
    lifecycleBusy.value = false
  }
}

async function restoreProject() {
  if (!confirm('Restore this archived project? It will return to its previous status.')) return
  lifecycleBusy.value = true
  saveError.value = ''
  try {
    await useRestoreProject(projectId.value)
    await load()
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to restore project'
  } finally {
    lifecycleBusy.value = false
  }
}

async function hardDeleteProject() {
  if (!confirm('Permanently delete this project? All data and files will be permanently removed. This cannot be undone.')) return
  // Admins must supply their password when the retention window hasn't elapsed.
  const password = window.prompt('Enter your password to permanently delete this project:')
  if (password === null) return
  lifecycleBusy.value = true
  saveError.value = ''
  try {
    await useHardDeleteProject(projectId.value, password)
    router.push('/projects')
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to delete project'
    lifecycleBusy.value = false
  }
}

// ---------- Messages ----------
const messageDraft = ref('')
const sending = ref(false)
// Safe by default: staff start staff-only; flip to share with the client.
const sendInternal = ref(true)
const visibleToClient = computed({
  get: () => !isClient.value && !sendInternal.value,
  set: (v: boolean) => { sendInternal.value = !v },
})
// What actually gets sent — a client can never be internal (the backend 403s it).
const effectiveInternal = computed(() => !isClient.value && sendInternal.value)
async function sendMessage() {
  if (!messageDraft.value.trim() || sending.value) return
  sending.value = true
  try {
    await useSendMessage(projectId.value, messageDraft.value.trim(), effectiveInternal.value)
    messageDraft.value = ''
    messages.value = await useGetMessages(projectId.value)
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to send message'
  } finally {
    sending.value = false
  }
}

function isInternal(msg: any): boolean {
  return msg?.visibility === 'INTERNAL'
}

// ---------- Documents (add / delete) ----------
const docDialogOpen = ref(false)
const docSaving = ref(false)
const docForm = ref({ title: '', description: '', fileUrl: '' })

function openDocDialog() {
  docForm.value = { title: '', description: '', fileUrl: '' }
  docDialogOpen.value = true
}

async function submitDocument() {
  if (!docForm.value.title.trim() || docSaving.value) return
  docSaving.value = true
  try {
    await useCreateDocument({
      projectId: projectId.value,
      title: docForm.value.title.trim(),
      description: docForm.value.description.trim() || null,
      fileUrl: docForm.value.fileUrl.trim() || null,
    })
    docDialogOpen.value = false
    documents.value = await useGetDocuments({ projectId: projectId.value })
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to add document'
  } finally {
    docSaving.value = false
  }
}

async function deleteDocument(id: number) {
  if (!confirm('Delete this document?')) return
  try {
    await useDeleteDocument(id)
    documents.value = await useGetDocuments({ projectId: projectId.value })
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to delete document'
  }
}

// ---------- Administration tab ----------
// Available to both provider admins and non-admin staff (USER); clients
// never see it. Holds the project notes (internal), the status
// configuration, and the project lifecycle (archive/restore for staff;
// hard delete stays admin-only).
const adminForm = ref({
  status: '',
  notes: '',
})
const adminReady = ref(false)
const projectStatusCodes = ['NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'ARCHIVED']

function initAdminForm() {
  if (!project.value) return
  adminForm.value = {
    status: project.value.status || 'NOT_STARTED',
    notes: project.value.notes || '',
  }
  adminReady.value = true
}
// (adminForm is initialized inside load() once the project is available)

// Notes card: read-only by default; staff flips it into edit mode
// (same pattern as the Project & Representative card on the Overview).
const editingNotes = ref(false)
const notesSaving = ref(false)
const notesError = ref('')

function startNotesEdit() {
  adminForm.value.notes = project.value?.notes || ''
  notesError.value = ''
  editingNotes.value = true
}
function cancelNotesEdit() {
  // Revert the draft to the stored value.
  adminForm.value.notes = project.value?.notes || ''
  editingNotes.value = false
}

async function saveAdminChanges() {
  if (!project.value) return
  saveError.value = ''
  notesError.value = ''
  notesSaving.value = true
  try {
    const updated = await useUpdateProject(projectId.value, {
      companyId: project.value.companyId,
      serviceId: project.value.serviceId ?? null,
      name: project.value.name,
      notes: adminForm.value.notes,
      // Project address + authorized-rep contact are edited from the
      // Overview tab now — pass through whatever is stored so this
      // status/notes save doesn't clear them (apply() overwrites every
      // field).
      address: project.value.address ?? null,
      deliverables: project.value.deliverables ?? null,
      status: adminForm.value.status,
      totalCost: project.value.totalCost ?? null,
      rawMaterials: project.value.rawMaterials ?? null,
      productionOutput: project.value.productionOutput ?? null,
      wasteManagement: project.value.wasteManagement ?? null,
      wasteMaterials: project.value.wasteMaterials ?? null,
      manufacturingProcedure: project.value.manufacturingProcedure ?? null,
      checklistNa: project.value.checklistNa ?? '{}',
      productionFlowchartUrl: project.value.productionFlowchartUrl ?? null,
      // No longer editable from the UI (the progress slider was removed),
      // but apply() overwrites every field — echo the stored value back so a
      // status/notes save doesn't reset progress to 0.
      progress: project.value.progress ?? 0,
    })
    project.value = updated
    adminForm.value.notes = project.value.notes || ''
    editingNotes.value = false
  } catch (err: any) {
    // Surface the failure in both cards that share this save.
    const msg = err?.response?.data?.message || 'Failed to save project'
    saveError.value = msg
    notesError.value = msg
  } finally {
    notesSaving.value = false
  }
}

// ---------- Authorized-rep review & completion ----------
// The customer's authorized representative is the one who reviews a
// submitted project and marks it complete. Identified by matching their
// own user id against the company's authorizedRepId.
const isAuthorizedRep = computed(() => {
  return !!me.value?.id && !!company.value?.authorizedRepId &&
    Number(company.value.authorizedRepId) === Number(me.value.id)
})
const completing = ref(false)
async function markCompleted() {
  if (!project.value) return
  if (!confirm('Mark this project as completed? The SECPhils team will be notified and it will appear in the reviews list.')) return
  completing.value = true
  saveError.value = ''
  try {
    const updated = await useUpdateProject(projectId.value, {
      companyId: project.value.companyId,
      serviceId: project.value.serviceId ?? null,
      name: project.value.name,
      notes: project.value.notes ?? null,
      objectives: project.value.objectives ?? null,
      deliverables: project.value.deliverables ?? null,
      address: project.value.address ?? null,
      status: 'COMPLETED',
      totalCost: project.value.totalCost ?? null,
      rawMaterials: project.value.rawMaterials ?? null,
      productionOutput: project.value.productionOutput ?? null,
      wasteManagement: project.value.wasteManagement ?? null,
      wasteMaterials: project.value.wasteMaterials ?? null,
      manufacturingProcedure: project.value.manufacturingProcedure ?? null,
      checklistNa: project.value.checklistNa ?? '{}',
      productionFlowchartUrl: project.value.productionFlowchartUrl ?? null,
      progress: project.value.progress ?? 0,
    })
    project.value = updated
  } catch (err: any) {
    saveError.value = err?.response?.data?.message || 'Failed to mark the project complete'
  } finally {
    completing.value = false
  }
}

// ---------- Production details editor ----------
// The Production tab is read-only by default. Provider staff (admin/user) can
// flip into edit mode to update the checklist the wizard captured: total cost,
// waste management + waste types, raw materials, production output, and the
// manufacturing procedure. One useUpdateProject PUT saves it all — the backend
// ProjectController.apply() applies these fields unconditionally (null clears a
// field), so clearing works too. The flowchart is a project Document, not part
// of this payload, so it is left untouched.
const editingProduction = ref(false)
const producingEdit = ref(false)
const productionError = ref('')
const productionEdit = ref({
  totalCost: '' as string,
  wasteManagement: '',
  rawMaterials: [] as { name: string; quantity: string; unit: string; period: 'MONTHLY' | 'YEARLY' }[],
  productionOutput: [] as { name: string; quantity: string; unit: string; period: 'MONTHLY' | 'YEARLY'; pricePerUnit: string }[],
  wasteMaterials: [] as { type: string; quantity: string; unit: string; period: 'MONTHLY' | 'YEARLY'; recyclable: boolean }[],
  manufacturingProcedure: '',
})
// Working copy of the per-section "Not applicable" flags while editing.
// Saved only on Save (Cancel leaves the stored flags untouched).
const productionNa = ref<Record<string, boolean>>({})

// '' for null/0-free numbers; never .trim() a number input (Vue stores a
// number with .number, but the editor inputs are plain v-model strings).
function toStr(n: number | null | undefined): string {
  return n == null ? '' : String(n)
}
function numOrNull(s: string): number | null {
  if (s == null || s === '') return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}

function initProductionEdit() {
  const p = project.value
  if (!p) return
  productionEdit.value = {
    totalCost: toStr(p.totalCost),
    wasteManagement: p.wasteManagement || '',
    rawMaterials: (rawMaterials.value || []).map(m => ({
      name: m.name ?? '', quantity: toStr(m.quantity), unit: m.unit ?? '', period: (m.period as 'MONTHLY' | 'YEARLY') || 'MONTHLY',
    })),
    productionOutput: (productionOutput.value || []).map(o => ({
      name: o.name ?? '', quantity: toStr(o.quantity), unit: o.unit ?? '', period: (o.period as 'MONTHLY' | 'YEARLY') || 'MONTHLY', pricePerUnit: toStr(o.pricePerUnit),
    })),
    wasteMaterials: (wasteMaterials.value || []).map(w => ({
      type: w.type ?? '', quantity: toStr(w.quantity), unit: w.unit ?? '', period: (w.period as 'MONTHLY' | 'YEARLY') || 'MONTHLY', recyclable: !!w.recyclable,
    })),
    manufacturingProcedure: p.manufacturingProcedure || '',
  }
  productionNa.value = { ...CHECKLIST_SECTIONS.reduce((acc, s) => { acc[s.key] = !!checklistNa.value[s.key]; return acc }, {} as Record<string, boolean>) }
}

function startProductionEdit() {
  initProductionEdit()
  productionError.value = ''
  editingProduction.value = true
}

// Review-card affordance: jump straight into the Production editor (same
// path the /projects/{id}?edit=1 deep-link takes). This is what lets the
// authorized rep actually complete the required project details instead of
// being stuck with a read-only checklist next to the complete button.
function openProductionEditFromReview() {
  activeTab.value = 'Production'
  startProductionEdit()
}
function cancelProductionEdit() {
  editingProduction.value = false
}

// ---------- Company details editor ----------
// The Company tab is read-only by default. Staff/admin (isUser || isAdmin)
// can edit the client company's core details. Company fields go through
// the null-safe company PUT (edits values; the null-safe apply can't clear
// a field, which is fine — we only set). The authorized-rep contact now
// lives in the Overview tab (see the overview editor below).
const companyForm = ref<{ name: string; location: string; phone: string; owner: string; description: string }>({
  name: '', location: '', phone: '', owner: '', description: '',
})
const editingCompany = ref(false)
const companySaving = ref(false)
const companySaveError = ref('')

function startCompanyEdit() {
  const c = company.value as any
  companyForm.value = {
    name: c?.name || '',
    location: c?.location || '',
    phone: c?.phone || '',
    owner: c?.owner || '',
    description: c?.description || '',
  }
  companySaveError.value = ''
  editingCompany.value = true
}

function cancelCompanyEdit() {
  editingCompany.value = false
  companyForm.value = { name: '', location: '', phone: '', owner: '', description: '' }
}

async function saveCompanyEdit() {
  if (!company.value) return
  companySaving.value = true
  companySaveError.value = ''
  try {
    const name = companyForm.value.name.trim()
    if (!name) throw new Error('Company name is required.')
    const updatedCompany = await useUpdateCompany(company.value.id, {
      name,
      location: companyForm.value.location.trim() || null,
      phone: companyForm.value.phone.trim() || null,
      owner: companyForm.value.owner.trim() || null,
      description: companyForm.value.description.trim() || null,
    })
    company.value = updatedCompany
    editingCompany.value = false
  } catch (err: any) {
    companySaveError.value = err?.response?.data?.message || err?.message || 'Failed to save company details.'
  } finally {
    companySaving.value = false
  }
}

// ---------- Overview editor: project address + authorized rep ----------
// These two were split across the Company tab / Admin Controls. They now live
// together in the Overview tab. The project address is a project field (PUT
// project); the rep's name/email/phone live on the rep's User row (PUT user).
// After the rep update we re-fetch the company so its derived rep fields
// (authorizedRepName/Email/Phone) re-render from the fresh user row.
const overviewForm = ref<{
  address: string; addressDiffers: boolean;
  repName: string; repEmail: string; repPhone: string;
}>({ address: '', addressDiffers: false, repName: '', repEmail: '', repPhone: '' })
const editingOverview = ref(false)
const overviewSaving = ref(false)
const overviewError = ref('')

// The company's canonical address. New customer companies store it in
// `location` (the wizard's "Company Address" field); the provider's own
// profile stores it in `headquarters` (Admin → Company settings). Check
// both so a project without its own address still shows the address.
const companyAddress = computed(() => {
  const c = company.value as any
  return c?.location || c?.headquarters || ''
})

function startOverviewEdit() {
  const p = project.value
  const c = company.value as any
  overviewForm.value = {
    address: p?.address || '',
    addressDiffers: !!p?.address,
    repName: c?.authorizedRepName || '',
    repEmail: c?.authorizedRepEmail || '',
    repPhone: c?.authorizedRepPhone || '',
  }
  overviewError.value = ''
  editingOverview.value = true
}

function cancelOverviewEdit() {
  editingOverview.value = false
  overviewForm.value = { address: '', addressDiffers: false, repName: '', repEmail: '', repPhone: '' }
}

async function saveOverviewEdit() {
  const p = project.value
  if (!p) return
  overviewSaving.value = true
  overviewError.value = ''
  const f = overviewForm.value
  try {
    // 1. Project address. A plain address override, or null to fall back to
    //    the company address. Everything else passes through unchanged
    //    (apply() overwrites every field).
    const address = f.addressDiffers ? (f.address.trim() || null) : null
    const updated = await useUpdateProject(projectId.value, {
      companyId: p.companyId,
      serviceId: p.serviceId ?? null,
      name: p.name,
      notes: p.notes ?? null,
      objectives: p.objectives ?? null,
      deliverables: p.deliverables ?? null,
      address,
      status: p.status,
      totalCost: p.totalCost ?? null,
      rawMaterials: p.rawMaterials ?? null,
      productionOutput: p.productionOutput ?? null,
      wasteManagement: p.wasteManagement ?? null,
      wasteMaterials: p.wasteMaterials ?? null,
      manufacturingProcedure: p.manufacturingProcedure ?? null,
      checklistNa: p.checklistNa ?? '{}',
      productionFlowchartUrl: p.productionFlowchartUrl ?? null,
      progress: p.progress ?? 0,
    })
    project.value = updated

    // 2. The authorized rep's name/email/phone live on the rep's User row.
    //    Email can't be cleared (unique, non-blank only); a blank name just
    //    skips the first/last-name split. Re-fetch the company afterwards so
    //    the derived rep fields reflect the new user row.
    const repId = (company.value as any)?.authorizedRepId
    if (repId) {
      const email = f.repEmail.trim()
      const repName = f.repName.trim()
      const nameParts = repName ? repName.split(/\s+/) : []
      await useUpdateUser(repId, {
        email: email || undefined,
        firstName: nameParts[0] || undefined,
        lastName: nameParts.slice(1).join(' ') || undefined,
        phone: f.repPhone.trim() || undefined,
      })
      company.value = await useGetCompany(p.companyId)
    }
    editingOverview.value = false
  } catch (err: any) {
    overviewError.value = err?.response?.data?.message || err?.message || 'Failed to save.'
  } finally {
    overviewSaving.value = false
  }
}

function addEditRow(kind: 'rawMaterials' | 'productionOutput' | 'wasteMaterials') {
  if (kind === 'rawMaterials') productionEdit.value.rawMaterials.push({ name: '', quantity: '', unit: '', period: 'MONTHLY' })
  else if (kind === 'productionOutput') productionEdit.value.productionOutput.push({ name: '', quantity: '', unit: '', period: 'MONTHLY', pricePerUnit: '' })
  else productionEdit.value.wasteMaterials.push({ type: '', quantity: '', unit: '', period: 'MONTHLY', recyclable: true })
}
function removeEditRow(kind: 'rawMaterials' | 'productionOutput' | 'wasteMaterials', i: number) {
  productionEdit.value[kind].splice(i, 1)
}

async function saveProductionEdit() {
  if (!project.value) return
  producingEdit.value = true
  productionError.value = ''
  const e = productionEdit.value
  const json = (a: any[]) => (a.length ? JSON.stringify(a) : null)
  try {
    const updated = await useUpdateProject(projectId.value, {
      // Non-production fields pass through unchanged.
      companyId: project.value.companyId,
      serviceId: project.value.serviceId ?? null,
      name: project.value.name,
      address: project.value.address ?? null,
      // Objectives were removed from the UI — pass through whatever is
      // stored so an edit elsewhere can't clear it.
      objectives: project.value.objectives ?? null,
      deliverables: project.value.deliverables ?? null,
      status: project.value.status,
      // Production checklist fields (edit mode) — blank clears the field.
      totalCost: e.totalCost === '' ? null : Number(e.totalCost),
      rawMaterials: json(e.rawMaterials.filter(r => r.name.trim()).map(r => ({ name: r.name.trim(), quantity: numOrNull(r.quantity), unit: r.unit.trim() || null, period: r.period }))),
      productionOutput: json(e.productionOutput.filter(r => r.name.trim()).map(r => ({ name: r.name.trim(), quantity: numOrNull(r.quantity), unit: r.unit.trim() || null, period: r.period, pricePerUnit: numOrNull(r.pricePerUnit) }))),
      wasteManagement: e.wasteManagement.trim() || null,
      wasteMaterials: json(e.wasteMaterials.filter(r => r.type.trim()).map(r => ({ type: r.type.trim(), quantity: numOrNull(r.quantity), unit: r.unit.trim() || null, period: r.period, recyclable: r.recyclable }))),
      manufacturingProcedure: e.manufacturingProcedure.trim() || null,
      // Per-section "Not applicable" flags — always send the full map
      // (unchecked sections are false; the DB stores the whole object).
      checklistNa: JSON.stringify(productionNa.value),
      productionFlowchartUrl: project.value.productionFlowchartUrl ?? null,
      progress: project.value.progress ?? 0,
    })
    project.value = updated
    editingProduction.value = false
  } catch (err: any) {
    productionError.value = err?.response?.data?.message || 'Failed to save production details'
  } finally {
    producingEdit.value = false
  }
}
</script>

<template>
  <div>
    <div v-if="loading" class="flex items-center justify-center py-20">
      <svg class="animate-spin h-8 w-8 text-emerald-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
    </div>

    <div v-else-if="loadError" class="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
      {{ loadError }}
    </div>

    <div v-else-if="project">
      <!-- Header -->
      <div class="mb-6">
        <div class="mb-3">
          <BackToListButton to="Projects" />
        </div>
        <div class="flex items-center justify-between mb-4">
          <div>
            <h1 class="text-2xl font-bold text-gray-900">{{ project.name }}</h1>
            <p class="text-gray-600 mt-1">
              {{ project.companyName || company?.name || '—' }} &middot; {{ project.serviceName || '—' }}
            </p>
          </div>
          <span :class="['px-3 py-1 text-sm font-medium rounded-full', PROJECT_STATUS_COLORS[projectStatusLabel(project.status)]]">
            {{ projectStatusLabel(project.status) }}
          </span>
        </div>

      </div>

      <!-- Tabs -->
      <div class="border-b border-gray-200 mb-6">
        <nav class="-mb-px flex gap-8 overflow-x-auto">
          <button
            v-for="tab in tabs"
            :key="tab"
            @click="activeTab = tab"
            :class="[
              'py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors',
              activeTab === tab
                ? 'border-emerald-600 text-emerald-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300',
            ]"
          >
            {{ tab }}
          </button>
        </nav>
      </div>

      <!-- ================= OVERVIEW ================= -->
      <div v-if="activeTab === 'Overview'">
        <!-- Project address + authorized-rep contact (staff/admin editable).
             Leads the Overview. Notes are provider-internal (Administration
             tab) and are not shown to clients here. -->
        <div class="bg-white rounded-lg shadow p-6 mb-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900">Project &amp; Representative</h2>
            <button
              v-if="!isClient && !editingOverview"
              @click="startOverviewEdit"
              class="inline-flex items-center gap-2 text-sm font-medium text-emerald-600 hover:text-emerald-700 border border-emerald-200 bg-emerald-50 hover:bg-emerald-100 px-3 py-1.5 rounded-lg transition-colors"
            >
              <i class="fas fa-pencil" /> Edit
            </button>
          </div>
          <p v-if="overviewError" class="mb-4 text-sm text-red-600">{{ overviewError }}</p>

          <div class="mb-4">
            <p class="text-sm text-gray-500">Project Address</p>
            <label v-if="editingOverview" class="flex items-center gap-2 mt-1 mb-2">
              <input type="checkbox" v-model="overviewForm.addressDiffers" class="rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />
              <span class="text-sm text-gray-700">Different from company address</span>
            </label>
            <input
              v-if="editingOverview && overviewForm.addressDiffers"
              v-model="overviewForm.address"
              placeholder="Full address where the project operates (barangay, city, province, ZIP)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
            <p v-else class="text-gray-900 mt-1">
              {{ project.address || companyAddress || '—' }}
              <span v-if="!project.address && companyAddress" class="text-gray-400 text-xs"> (company address)</span>
              <span v-if="editingOverview && !overviewForm.addressDiffers" class="text-gray-400 text-xs"> — uses the company address; check the box to override</span>
            </p>
          </div>

          <h3 class="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">Authorized Representative</h3>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <p class="text-sm text-gray-500">Name</p>
              <input
                v-if="editingOverview"
                v-model="overviewForm.repName"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                placeholder="Full name"
              />
              <p v-else class="text-gray-900 mt-1">{{ company?.authorizedRepName || '—' }}</p>
            </div>
            <div>
              <p class="text-sm text-gray-500">Email Address</p>
              <input
                v-if="editingOverview"
                v-model="overviewForm.repEmail"
                type="email"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                placeholder="name@example.com"
              />
              <p v-else class="text-gray-900 mt-1">{{ company?.authorizedRepEmail || '—' }}</p>
            </div>
            <div>
              <p class="text-sm text-gray-500">Phone</p>
              <input
                v-if="editingOverview"
                v-model="overviewForm.repPhone"
                class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                placeholder="+63 000 000 0000"
              />
              <p v-else class="text-gray-900 mt-1">{{ company?.authorizedRepPhone || '—' }}</p>
            </div>
          </div>

          <div v-if="editingOverview" class="mt-6 pt-4 border-t border-gray-200 flex justify-end gap-3">
            <button @click="cancelOverviewEdit" class="border border-gray-300 text-gray-700 px-5 py-2 rounded-lg hover:bg-gray-50 transition-colors font-medium">Cancel</button>
            <button @click="saveOverviewEdit" :disabled="overviewSaving" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50">
              {{ overviewSaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </div>

        <!-- Notes are provider-internal: hidden from the client Overview entirely.
             The card + editing both live on the Administration tab. -->

        <!-- Authorized-rep review card: the customer's rep reviews a
             submitted project and marks it complete (notifies the team). The
             secondary action jumps into the Production editor so the rep can
             actually complete the required project details first. -->
        <div v-if="isAuthorizedRep && project.status !== 'COMPLETED'" class="bg-emerald-50 border border-emerald-200 rounded-lg shadow p-6 mb-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-1">Review &amp; complete this project</h2>
          <p class="text-sm text-gray-600 mb-1">
            You're the authorized representative for {{ company?.name || 'this customer' }}. Check the project
            details and production checklist, then mark it complete when everything looks right.
          </p>
          <p v-if="checklistNudge" class="text-sm text-amber-700">
            <i class="fas fa-circle-exclamation mr-1" />{{ checklistNudge }}
          </p>
          <div class="flex flex-wrap items-center gap-3 mt-4">
            <button
              @click="openProductionEditFromReview"
              class="inline-flex items-center gap-2 border border-emerald-200 text-emerald-700 bg-emerald-50 hover:bg-emerald-100 px-4 py-2 rounded-lg transition-colors text-sm font-medium"
            >
              <i class="fas fa-pencil" />
              {{ productionChecklistComplete ? 'Edit production details' : 'Complete the production details' }}
            </button>
            <button
              @click="markCompleted"
              :disabled="completing || !productionChecklistComplete"
              :title="!productionChecklistComplete ? 'Complete all the production details first' : ''"
              class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ completing ? 'Marking…' : 'Mark as completed' }}
            </button>
            <span v-if="saveError" class="text-sm text-red-600">{{ saveError }}</span>
          </div>
        </div>

        <!-- Add Update (user/admin only) -->
        <div v-if="isUser || isAdmin" class="bg-white rounded-lg shadow p-6 mb-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Add Update</h2>
          <textarea
            v-model="messageDraft"
            rows="3"
            placeholder="Post a progress update to the project conversation..."
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
          />
          <div class="mt-3 flex justify-end">
            <button
              @click="sendMessage"
              :disabled="sending"
              class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
            >
              Post Update
            </button>
          </div>
        </div>

        <!-- Update History -->
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">
            {{ isClient ? 'Recent Updates' : 'Update History' }}
          </h2>
          <div v-if="messages.length === 0" class="text-sm text-gray-500">No updates posted yet.</div>
          <div v-else class="relative">
            <div class="absolute left-3 top-1 bottom-1 w-px bg-gray-200" />
            <div
              v-for="msg in (isClient ? [...messages].reverse().slice(0, 3) : [...messages].reverse())"
              :key="msg.id"
              class="relative pl-10 pb-6 last:pb-0"
            >
              <span class="absolute left-1.5 top-1 w-3 h-3 rounded-full bg-emerald-500 ring-4 ring-emerald-100" />
              <p class="text-xs text-gray-500">{{ formatDateTime(msg.createdAt) }} &middot; {{ msg.senderName || '—' }}</p>
              <p class="text-sm text-gray-700 mt-1">{{ msg.body }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= PRODUCTION (wizard checklist) ================= -->
      <div v-if="activeTab === 'Production'" class="space-y-6">
        <!-- Header + Edit toggle (staff, or the authorized rep completing the
          project's required details; other clients stay read-only) -->
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900">Production Details</h2>
          <button
            v-if="(!isClient || isAuthorizedRep) && !editingProduction"
            @click="startProductionEdit"
            class="inline-flex items-center gap-2 text-sm font-medium text-emerald-600 hover:text-emerald-700 border border-emerald-200 bg-emerald-50 hover:bg-emerald-100 px-3 py-1.5 rounded-lg transition-colors"
          >
            <i class="fas fa-pencil" /> Edit
          </button>
        </div>

        <!-- READ-ONLY mode -->
        <div v-if="!editingProduction">
          <div v-if="!hasProductionData" class="bg-white rounded-lg shadow p-6">
            <p class="text-sm text-gray-500">
              No production details captured for this project yet — they can be completed later from this page.
            </p>
            <p v-if="!isClient || isAuthorizedRep" class="text-sm text-gray-500 mt-2">
              <i class="fas fa-lightbulb mr-1" />Use the Edit button to fill them in.
            </p>
          </div>

          <div v-else class="space-y-6">
            <!-- Sections the rep explicitly marked "Not applicable" (no content) -->
            <div v-if="nasWithoutContent.length" class="bg-gray-50 border border-gray-200 rounded-lg p-3 text-sm text-gray-600">
              <i class="fas fa-circle-slash mr-1 text-gray-400" />Marked not applicable: {{ nasWithoutContent.map(s => s.label).join(', ') }}.
            </div>
            <!-- Total project cost — shown to staff and to the authorized rep (they
              complete/verify these details; the read-only view for other
              clients stays staff-hidden) -->
            <div v-if="(!isClient || isAuthorizedRep) && project.totalCost != null" class="bg-white rounded-lg shadow p-6">
              <h2 class="text-sm font-medium text-gray-500 uppercase mb-1">Total Project Cost</h2>
              <p class="text-2xl font-bold text-gray-900">{{ formatPhp(project.totalCost) }}</p>
            </div>

            <!-- Raw materials -->
            <div v-if="rawMaterials && rawMaterials.length" class="bg-white rounded-lg shadow p-6">
              <h2 class="text-lg font-semibold text-gray-900 mb-4">Raw Materials</h2>
              <div class="overflow-x-auto">
                <table class="w-full">
                  <thead class="bg-gray-50">
                    <tr>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Material</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Quantity</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Unit</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Period</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-200">
                    <tr v-for="(m, i) in rawMaterials" :key="'raw-' + i">
                      <td class="px-4 py-3 text-sm font-medium text-gray-900">{{ m.name }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ m.quantity ?? '—' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ m.unit || 'tons' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ m.period === 'YEARLY' ? 'Per year' : 'Per month' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Production output -->
            <div v-if="productionOutput && productionOutput.length" class="bg-white rounded-lg shadow p-6">
              <h2 class="text-lg font-semibold text-gray-900 mb-4">Production Output</h2>
              <div class="overflow-x-auto">
                <table class="w-full">
                  <thead class="bg-gray-50">
                    <tr>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Product</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Quantity</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Unit</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Period</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Price / Unit</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-200">
                    <tr v-for="(o, i) in productionOutput" :key="'out-' + i">
                      <td class="px-4 py-3 text-sm font-medium text-gray-900">{{ o.name }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ o.quantity ?? '—' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ o.unit || '—' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ o.period === 'YEARLY' ? 'Per year' : 'Per month' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ formatPhp(o.pricePerUnit) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Waste management -->
            <div v-if="project.wasteManagement || (wasteMaterials && wasteMaterials.length)" class="bg-white rounded-lg shadow p-6">
              <h2 class="text-lg font-semibold text-gray-900 mb-4">Waste Management</h2>
              <p v-if="project.wasteManagement" class="text-gray-700 whitespace-pre-line">{{ project.wasteManagement }}</p>
              <div v-if="wasteMaterials && wasteMaterials.length" class="overflow-x-auto mt-4">
                <table class="w-full">
                  <thead class="bg-gray-50">
                    <tr>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Waste Type</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Quantity</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Unit</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Period</th>
                      <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Recyclable</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-200">
                    <tr v-for="(w, i) in wasteMaterials" :key="'waste-' + i">
                      <td class="px-4 py-3 text-sm font-medium text-gray-900">{{ w.type }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ w.quantity ?? '—' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ w.unit || '—' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">{{ w.period === 'YEARLY' ? 'Per year' : 'Per month' }}</td>
                      <td class="px-4 py-3 text-sm text-gray-700">
                        <span v-if="w.recyclable" class="text-emerald-600"><i class="fas fa-check mr-1" />Recyclable</span>
                        <span v-else class="text-gray-500">Non-recyclable</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Manufacturing process -->
            <div v-if="project.manufacturingProcedure || project.productionFlowchartUrl" class="bg-white rounded-lg shadow p-6">
              <h2 class="text-lg font-semibold text-gray-900 mb-4">Manufacturing Process</h2>
              <div v-if="project.manufacturingProcedure" class="space-y-4">
                <h3 class="text-sm font-medium text-gray-500 uppercase mb-1">Procedure</h3>
                <p class="text-gray-700 whitespace-pre-line">{{ project.manufacturingProcedure }}</p>
              </div>
              <div v-if="project.productionFlowchartUrl" class="mt-4">
                <h3 class="text-sm font-medium text-gray-500 uppercase mb-1">Production Flowchart</h3>
                <a
                  :href="project.productionFlowchartUrl"
                  target="_blank"
                  rel="noopener"
                  class="inline-flex items-center gap-2 text-emerald-600 hover:underline"
                >
                  <i class="fas fa-diagram-project" /> View production flowchart
                </a>
              </div>
            </div>
          </div>
        </div>

        <!-- EDIT mode -->
        <div v-else class="space-y-6">
          <p v-if="productionError" class="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">{{ productionError }}</p>

          <!-- Total project cost -->
          <div class="bg-white rounded-lg shadow p-6">
            <label class="block text-sm font-medium text-gray-700 mb-1">Total Project Cost (₱)</label>
            <input
              v-model="productionEdit.totalCost"
              type="number" min="0" step="any"
              placeholder="Optional — leave blank if not known yet"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>

          <!-- Raw materials -->
          <div class="bg-white rounded-lg shadow p-6">
            <div class="flex items-center justify-between mb-3">
              <h2 class="text-lg font-semibold text-gray-900">Raw Materials</h2>
              <div class="flex items-center gap-2">
                <label class="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer select-none" title="Mark this section as not applicable to your company">
                  <input type="checkbox" v-model="productionNa.rawMaterials" class="rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />Not applicable
                </label>
                <button v-if="!productionNa.rawMaterials" @click="addEditRow('rawMaterials')" class="text-sm font-medium text-emerald-600 hover:text-emerald-700"><i class="fas fa-plus mr-1" />Add material</button>
              </div>
            </div>
            <p v-if="productionNa.rawMaterials" class="text-sm text-gray-500 italic">Marked as not applicable — your company has no raw material inputs.</p>
            <template v-else>
              <p v-if="!productionEdit.rawMaterials.length" class="text-sm text-gray-500">No raw materials — add one above.</p>
              <div v-for="(m, i) in productionEdit.rawMaterials" :key="'eraw-' + i" class="grid grid-cols-12 gap-2 mb-2">
                <input v-model="m.name" placeholder="Material" class="col-span-5 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                <input v-model="m.quantity" type="number" min="0" step="any" placeholder="Qty" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                <input v-model="m.unit" placeholder="Unit" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                <select v-model="m.period" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm">
                  <option value="MONTHLY">Per month</option><option value="YEARLY">Per year</option>
                </select>
                <button @click="removeEditRow('rawMaterials', i)" class="col-span-1 justify-self-end text-red-500 hover:text-red-700" title="Remove"><i class="fas fa-trash" /></button>
              </div>
            </template>
          </div>

          <!-- Production output -->
          <div class="bg-white rounded-lg shadow p-6">
            <div class="flex items-center justify-between mb-3">
              <h2 class="text-lg font-semibold text-gray-900">Production Output</h2>
              <div class="flex items-center gap-2">
                <label class="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer select-none" title="Mark this section as not applicable to your company">
                  <input type="checkbox" v-model="productionNa.productionOutput" class="rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />Not applicable
                </label>
                <button v-if="!productionNa.productionOutput" @click="addEditRow('productionOutput')" class="text-sm font-medium text-emerald-600 hover:text-emerald-700"><i class="fas fa-plus mr-1" />Add product</button>
              </div>
            </div>
            <p v-if="productionNa.productionOutput" class="text-sm text-gray-500 italic">Marked as not applicable — your company produces no saleable output.</p>
            <template v-else>
              <p v-if="!productionEdit.productionOutput.length" class="text-sm text-gray-500">No products — add one above.</p>
              <div v-for="(o, i) in productionEdit.productionOutput" :key="'eout-' + i" class="grid grid-cols-12 gap-2 mb-2">
                <input v-model="o.name" placeholder="Product" class="col-span-3 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                <input v-model="o.quantity" type="number" min="0" step="any" placeholder="Qty" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                <input v-model="o.unit" placeholder="Unit" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                <select v-model="o.period" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm">
                  <option value="MONTHLY">Per month</option><option value="YEARLY">Per year</option>
                </select>
                <input v-model="o.pricePerUnit" type="number" min="0" step="any" placeholder="Price/Unit (₱)" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                <button @click="removeEditRow('productionOutput', i)" class="col-span-1 justify-self-end text-red-500 hover:text-red-700" title="Remove"><i class="fas fa-trash" /></button>
              </div>
            </template>
          </div>

          <!-- Waste management -->
          <div class="bg-white rounded-lg shadow p-6 space-y-5">
            <div class="flex items-center justify-between">
              <h3 class="text-sm font-medium text-gray-700">Waste Management Practices</h3>
              <label class="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer select-none" title="Mark this section as not applicable to your company">
                <input type="checkbox" v-model="productionNa.wasteManagement" class="rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />Not applicable
              </label>
            </div>
            <p v-if="productionNa.wasteManagement" class="text-sm text-gray-500 italic">Marked as not applicable — waste management does not apply to your operations.</p>
            <textarea
              v-else
              v-model="productionEdit.wasteManagement"
              rows="3"
              placeholder="How do you manage your wastes (recyclable and non-recyclable)?"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            ></textarea>
            <div>
              <div class="flex items-center justify-between mb-3">
                <h3 class="text-sm font-medium text-gray-700">Waste Types</h3>
                <div class="flex items-center gap-2">
                  <label class="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer select-none" title="Mark this section as not applicable to your company">
                    <input type="checkbox" v-model="productionNa.wasteMaterials" class="rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />Not applicable
                  </label>
                  <button v-if="!productionNa.wasteMaterials" @click="addEditRow('wasteMaterials')" class="text-sm font-medium text-emerald-600 hover:text-emerald-700"><i class="fas fa-plus mr-1" />Add waste type</button>
                </div>
              </div>
              <p v-if="productionNa.wasteMaterials" class="text-sm text-gray-500 italic">Marked as not applicable — your company generates no waste types to list.</p>
              <template v-else>
                <p v-if="!productionEdit.wasteMaterials.length" class="text-sm text-gray-500">No waste types — add one above.</p>
                <div v-for="(w, i) in productionEdit.wasteMaterials" :key="'ewaste-' + i" class="grid grid-cols-12 gap-2 mb-2 items-center">
                  <input v-model="w.type" placeholder="Waste type" class="col-span-4 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                  <input v-model="w.quantity" type="number" min="0" step="any" placeholder="Qty" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                  <input v-model="w.unit" placeholder="Unit" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm" />
                  <select v-model="w.period" class="col-span-2 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm">
                    <option value="MONTHLY">Per month</option><option value="YEARLY">Per year</option>
                  </select>
                  <div class="col-span-2 flex items-center justify-between">
                    <label class="flex items-center gap-1 text-sm text-gray-700"><input type="checkbox" v-model="w.recyclable" class="rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />Recyclable</label>
                    <button @click="removeEditRow('wasteMaterials', i)" class="text-red-500 hover:text-red-700" title="Remove"><i class="fas fa-trash" /></button>
                  </div>
                </div>
              </template>
            </div>
          </div>

          <!-- Manufacturing process -->
          <div class="bg-white rounded-lg shadow p-6">
            <div class="flex items-center justify-between mb-1">
              <label class="block text-sm font-medium text-gray-700">Manufacturing Procedure</label>
              <label class="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer select-none" title="Mark this section as not applicable to your company">
                <input type="checkbox" v-model="productionNa.manufacturingProcedure" class="rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" />Not applicable
              </label>
            </div>
            <p v-if="productionNa.manufacturingProcedure" class="text-sm text-gray-500 italic">Marked as not applicable — your company does not manufacture products.</p>
            <textarea
              v-else
              v-model="productionEdit.manufacturingProcedure"
              rows="4"
              placeholder="How do you manufacture your products/output?"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            ></textarea>
            <p class="text-xs text-gray-500 mt-2">The production flowchart is a project document — add or replace it from the Documents tab.</p>
          </div>

          <!-- Actions -->
          <div class="flex justify-end gap-3">
            <button @click="cancelProductionEdit" class="border border-gray-300 text-gray-700 px-5 py-2 rounded-lg hover:bg-gray-50 transition-colors font-medium">Cancel</button>
            <button @click="saveProductionEdit" :disabled="producingEdit" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50">
              {{ producingEdit ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </div>
      </div>

      <!-- ================= COMPANY ================= -->
      <div v-if="activeTab === 'Company'">
        <div class="bg-white rounded-lg shadow p-6">
          <!-- Header + Edit toggle (staff only; clients are read-only) -->
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900">Client Company</h2>
            <button
              v-if="!isClient && !editingCompany"
              @click="startCompanyEdit"
              class="inline-flex items-center gap-2 text-sm font-medium text-emerald-600 hover:text-emerald-700 border border-emerald-200 bg-emerald-50 hover:bg-emerald-100 px-3 py-1.5 rounded-lg transition-colors"
            >
              <i class="fas fa-pencil" /> Edit
            </button>
          </div>
          <p v-if="companySaveError" class="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-4">{{ companySaveError }}</p>

          <!-- COMPANY -->
          <div>
            <h3 class="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">Company</h3>
            <div class="space-y-4">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <p class="text-sm text-gray-500">Company Name</p>
                  <input
                    v-if="editingCompany"
                    v-model="companyForm.name"
                    class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                    placeholder="Company name"
                  />
                  <p v-else class="text-gray-900 font-medium">{{ company?.name || project.companyName || '—' }}</p>
                </div>
                <div>
                  <p class="text-sm text-gray-500">Company Address</p>
                  <input
                    v-if="editingCompany"
                    v-model="companyForm.location"
                    class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                    placeholder="Street, city, region"
                  />
                  <p v-else class="text-gray-900">{{ companyAddress || '—' }}</p>
                </div>
                <div>
                  <p class="text-sm text-gray-500">Company Phone</p>
                  <input
                    v-if="editingCompany"
                    v-model="companyForm.phone"
                    class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                    placeholder="+63 000 000 0000"
                  />
                  <p v-else class="text-gray-900">{{ company?.phone || '—' }}</p>
                </div>
                <div>
                  <p class="text-sm text-gray-500">Company Owner</p>
                  <input
                    v-if="editingCompany"
                    v-model="companyForm.owner"
                    class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                    placeholder="Owner name"
                  />
                  <p v-else class="text-gray-900">{{ company?.owner || '—' }}</p>
                </div>
              </div>
              <div>
                <p class="text-sm text-gray-500">Business Description</p>
                <textarea
                  v-if="editingCompany"
                  v-model="companyForm.description"
                  rows="3"
                  class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                  placeholder="What the company does"
                ></textarea>
                <p v-else class="text-gray-900">{{ company?.description || '—' }}</p>
              </div>
            </div>
          </div>

          <div v-if="!isClient && editingCompany" class="mt-6 pt-4 border-t border-gray-200 flex justify-end gap-3">
            <button @click="cancelCompanyEdit" class="border border-gray-300 text-gray-700 px-5 py-2 rounded-lg hover:bg-gray-50 transition-colors font-medium">Cancel</button>
            <button @click="saveCompanyEdit" :disabled="companySaving" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50">
              {{ companySaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </div>
      </div>

      <!-- ================= TEAM (client) ================= -->
      <div v-if="activeTab === 'Team'" class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Your Project Team</h2>
        <div v-if="team.length === 0" class="text-sm text-gray-500">No team members assigned yet.</div>
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="member in team"
            :key="member.userId"
            class="flex items-center gap-4 p-4 border border-gray-200 rounded-lg"
          >
            <div class="w-12 h-12 rounded-full bg-emerald-600 flex items-center justify-center text-white font-medium">
              {{ initials(member.fullName) }}
            </div>
            <div>
              <h3 class="font-medium text-gray-900">{{ member.fullName }}</h3>
              <p class="text-sm text-gray-600">{{ member.role }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= DOCUMENTS ================= -->
      <div v-if="activeTab === 'Documents'" class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900">Project Documents</h2>
          <button
            v-if="!isClient"
            @click="openDocDialog"
            class="bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium"
          >
            <i class="fas fa-upload mr-1" /> Add Document
          </button>
        </div>
        <div v-if="documents.length === 0" class="p-6 text-sm text-gray-500">
          No documents for this project yet.
        </div>
        <div v-else class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Document Name</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Version</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Uploaded By</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th class="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase whitespace-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="doc in documents" :key="doc.id" class="hover:bg-gray-50">
                <td class="px-6 py-4">
                  <div class="flex items-center gap-3">
                    <i class="fas fa-file-lines text-emerald-500 text-lg" />
                    <div>
                      <span class="font-medium text-gray-900 text-sm">{{ doc.title }}</span>
                      <p v-if="doc.description" class="text-xs text-gray-500">{{ doc.description }}</p>
                    </div>
                  </div>
                </td>
                <td class="px-6 py-4">
                  <span :class="['px-2 py-1 text-xs font-medium rounded-full', FILE_TYPE_COLORS[fileTypeLabel(doc.fileType)] || 'bg-gray-100 text-gray-700']">
                    {{ fileTypeLabel(doc.fileType) }}
                  </span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-600">v{{ doc.version ?? 1 }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ doc.uploaderName || '—' }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ formatDate(doc.uploadedAt) }}</td>
                <td class="px-3 py-4 text-right whitespace-nowrap">
                  <RowActionsMenu v-if="!isClient" :actions="[
                    { label: 'Delete', color: 'text-red-600 hover:text-red-700 hover:bg-red-50', onClick: () => deleteDocument(doc.id) }
                  ]" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ================= MESSAGES ================= -->
      <div v-if="activeTab === 'Messages'" class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Project Conversation</h2>
          <p class="text-sm text-gray-600 mt-1">Shared thread for all project participants</p>
        </div>
        <div class="p-6 space-y-4">
          <div v-if="messages.length === 0" class="text-sm text-gray-500">No messages yet. Start the conversation below.</div>
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['flex', isMine(msg) ? 'justify-end' : 'justify-start']"
          >
            <div
              :class="[
                'max-w-md rounded-lg p-4',
                isInternal(msg)
                  ? (isMine(msg) ? 'bg-slate-700 text-white ring-1 ring-dashed ring-slate-400' : 'bg-slate-100 ring-1 ring-dashed ring-slate-400')
                  : (isMine(msg) ? 'bg-emerald-600 text-white' : 'bg-gray-100'),
              ]"
            >
              <div class="flex items-center justify-between gap-4 mb-1">
                <p :class="['text-xs font-medium flex items-center gap-1.5', isMine(msg) ? 'text-emerald-100' : 'text-gray-600']">
                  {{ msg.senderName || '—' }}
                  <span
                    v-if="isInternal(msg)"
                    :class="[
                      'inline-flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide px-1.5 py-0.5 rounded border',
                      isMine(msg) ? 'bg-white/20 text-white border-white/40' : 'bg-slate-200 text-slate-700 border-slate-400/60',
                    ]"
                    title="Internal — only visible to provider staff, not the client"
                  >
                    <i class="fas fa-lock"></i> Internal
                  </span>
                </p>
                <p :class="['text-xs', isMine(msg) ? 'text-emerald-200' : 'text-gray-400']">
                  {{ formatDateTime(msg.createdAt) }}
                </p>
              </div>
              <p class="text-sm">{{ msg.body }}</p>
              <div
                v-if="msg.attachmentFileName"
                class="mt-2 pt-2 border-t text-xs"
                :class="isMine(msg) ? 'border-white/20 text-emerald-100' : 'border-gray-300 text-gray-600'"
              >
                <i class="fas fa-paperclip mr-1"></i>{{ msg.attachmentFileName }}
              </div>
            </div>
          </div>
        </div>
        <div class="p-6 border-t border-gray-200">
          <div
            class="flex flex-col gap-3 rounded-lg border p-3"
            :class="effectiveInternal ? 'border-slate-300 bg-slate-50' : 'border-emerald-300 bg-emerald-50/40'"
          >
            <!-- Audience banner: where you see who will see this message. -->
            <div
              class="flex items-center gap-2 text-sm font-medium"
              :class="effectiveInternal ? 'text-slate-600' : 'text-emerald-800'"
            >
              <template v-if="effectiveInternal">
                <i class="fas fa-lock text-xs"></i>
                <span>Staff only &mdash; your client won&rsquo;t see this message</span>
              </template>
              <template v-else>
                <i class="fas fa-bullhorn text-xs"></i>
                <span>Visible to the client</span>
              </template>
              <!-- Staff get the switch; clients can only post client-visible. -->
              <label v-if="!isClient" class="ml-auto flex items-center gap-2 cursor-pointer select-none text-xs" :class="visibleToClient ? 'text-emerald-800' : 'text-slate-600'">
                <span class="font-semibold">Visible to client</span>
                <span class="relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors" :class="visibleToClient ? 'bg-emerald-600' : 'bg-slate-300'">
                  <span class="inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform" :class="visibleToClient ? 'translate-x-4' : 'translate-x-0.5'"></span>
                  <input v-model="visibleToClient" type="checkbox" class="sr-only" />
                </span>
              </label>
            </div>

            <textarea
              v-model="messageDraft"
              rows="2"
              :placeholder="effectiveInternal ? 'Type an internal staff note…' : 'Type a message…'"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm bg-white"
            ></textarea>

            <div class="flex items-center justify-end">
              <button
                @click="sendMessage"
                :disabled="sending || !messageDraft.trim()"
                class="inline-flex items-center gap-2 px-5 py-2 rounded-lg text-white font-medium transition-colors disabled:opacity-50"
                :class="effectiveInternal ? 'bg-slate-700 hover:bg-slate-800' : 'bg-emerald-600 hover:bg-emerald-700'"
              >
                <i :class="effectiveInternal ? 'fas fa-lock' : 'fas fa-paper-plane'"></i>
                {{ sending ? 'Sending…' : (effectiveInternal ? 'Send to staff' : 'Send to client') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= ADMINISTRATION (admin + staff user) ================= -->
      <div v-if="activeTab === 'Administration'">
        <!-- Notes: internal to the project — hidden from the client view. -->
        <div class="bg-white rounded-lg shadow p-6 mb-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900">Notes</h2>
            <button
              v-if="!editingNotes"
              @click="startNotesEdit"
              class="inline-flex items-center gap-2 text-sm font-medium text-emerald-600 hover:text-emerald-700 border border-emerald-200 bg-emerald-50 hover:bg-emerald-100 px-3 py-1.5 rounded-lg transition-colors"
            >
              <i class="fas fa-pencil" /> Edit
            </button>
          </div>
          <p v-if="notesError" class="mb-4 text-sm text-red-600">{{ notesError }}</p>
          <textarea
            v-if="editingNotes"
            v-model="adminForm.notes"
            rows="4"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
          ></textarea>
          <p v-else class="text-gray-700">{{ adminForm.notes || '—' }}</p>
          <div v-if="editingNotes" class="mt-6 pt-4 border-t border-gray-200 flex justify-end gap-3">
            <button @click="cancelNotesEdit" class="border border-gray-300 text-gray-700 px-5 py-2 rounded-lg hover:bg-gray-50 transition-colors font-medium">Cancel</button>
            <button @click="saveAdminChanges" :disabled="notesSaving" class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50">
              {{ notesSaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </div>

        <!-- Project Configuration (notes moved to their own card above). -->
        <div class="bg-white rounded-lg shadow p-6 mb-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Project Configuration</h2>
          <p v-if="saveError" class="mb-4 text-sm text-red-600">{{ saveError }}</p>
          <div class="max-w-md">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Status</label>
            <select
              v-model="adminForm.status"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option v-for="s in projectStatusCodes" :key="s" :value="s">{{ projectStatusLabel(s) }}</option>
            </select>
          </div>
          </div>

          <div class="mt-6 flex justify-end">
            <button
              @click="saveAdminChanges"
              class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium"
            >
              Save Changes
            </button>
          </div>
        </div>

        <!-- Lifecycle: archive / restore / hard delete -->
        <div class="bg-white rounded-lg shadow p-6">
          <h3 class="text-base font-semibold text-gray-900">Lifecycle</h3>
          <p class="mt-1 text-sm text-gray-600">
            Archiving hides the project from the list and moves its files into the archive.
            While archived it can be restored within the retention window; after that it can only
            be permanently deleted.
          </p>

          <div v-if="archived" class="mt-4 rounded-lg bg-amber-50 border border-amber-200 p-4 text-sm">
            <p class="font-medium text-amber-800">This project is archived.</p>
            <p class="mt-1 text-amber-700">
              Archived {{ project.archivedAt ? formatDate(project.archivedAt) : '—' }}
              <span v-if="project.deleteAt">
                · retention ends {{ formatDate(project.deleteAt) }}
              </span>
            </p>
          </div>

          <div class="mt-4 flex flex-wrap items-center gap-3">
            <button
              v-if="!archived"
              @click="archiveProject"
              :disabled="lifecycleBusy"
              class="px-4 py-2 border border-amber-300 text-amber-800 bg-amber-50 rounded-lg hover:bg-amber-100 transition-colors text-sm font-medium disabled:opacity-50"
            >
              {{ lifecycleBusy ? 'Working…' : 'Archive Project' }}
            </button>
            <button
              v-else
              @click="restoreProject"
              :disabled="lifecycleBusy"
              class="px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors text-sm font-medium disabled:opacity-50"
            >
              {{ lifecycleBusy ? 'Working…' : 'Restore Project' }}
            </button>
            <button
              v-if="isAdmin"
              @click="hardDeleteProject"
              :disabled="lifecycleBusy"
              class="px-4 py-2 border border-red-300 text-red-700 bg-red-50 rounded-lg hover:bg-red-100 transition-colors text-sm font-medium disabled:opacity-50"
            >
              {{ lifecycleBusy ? 'Working…' : 'Permanently Delete' }}
            </button>
            <p class="text-xs text-gray-500">
              Permanent deletion requires your password inside the retention window.
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= DOCUMENT DIALOG ================= -->
    <div
      v-if="docDialogOpen"
      class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      @click.self="docDialogOpen = false"
    >
      <div class="bg-white rounded-lg shadow-xl w-full max-w-md">
        <div class="p-6 border-b border-gray-200">
          <h3 class="text-lg font-semibold text-gray-900">Add Document</h3>
        </div>
        <div class="p-6 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Document Title</label>
            <input
              v-model="docForm.title"
              type="text"
              placeholder="e.g. Bottleneck Analysis — Line 3"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">File URL (optional)</label>
            <input
              v-model="docForm.fileUrl"
              type="url"
              placeholder="https://… (link to the hosted file)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              v-model="docForm.description"
              rows="3"
              placeholder="What does this document contain?"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>
        </div>
        <div class="p-6 border-t border-gray-200 flex justify-end gap-3">
          <button
            @click="docDialogOpen = false"
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            @click="submitDocument"
            :disabled="docSaving"
            class="bg-emerald-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-emerald-700 disabled:opacity-50"
          >
            {{ docSaving ? 'Saving…' : 'Add Document' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
