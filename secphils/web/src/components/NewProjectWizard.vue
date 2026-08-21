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

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  submit: [data: WizardData]
}>()

export interface WizardData {
  scenario: 'new' | 'existing'
  company: {
    name: string
    location: string
    owner: string
    businessType: string
  }
  representative: {
    fullName: string
    jobTitle: string
    email: string
    phone: string
  }
  project: {
    name: string
    serviceType: string
    description: string
    startDate: string
    endDate: string
  }
}

// Dialog open state controlled by prop
const dialogOpen = ref(props.open)

watch(() => props.open, (val) => {
  dialogOpen.value = val
  if (val) {
    resetForm()
  }
})

watch(dialogOpen, (val) => {
  emit('update:open', val)
})

// Wizard state
const currentStep = ref(0)
const scenario = ref<'new' | 'existing'>('new')

// Form data
const companyForm = ref({
  name: '',
  location: '',
  owner: '',
  businessType: '',
})

const repForm = ref({
  fullName: '',
  jobTitle: '',
  email: '',
  phone: '',
})

const projectForm = ref({
  name: '',
  serviceType: '',
  description: '',
  startDate: '',
  endDate: '',
})

// Mock company data for existing customers
const existingCompanies = ref([
  { id: 1, name: 'ABC Manufacturing', location: 'Detroit, MI', owner: 'John Smith', businessType: 'Manufacturing' },
  { id: 2, name: 'XYZ Energy Corp', location: 'Houston, TX', owner: 'Sarah Johnson', businessType: 'Energy' },
  { id: 3, name: 'Global Logistics Inc', location: 'Memphis, TN', owner: 'Mike Chen', businessType: 'Logistics' },
  { id: 4, name: 'Municipal Water Authority', location: 'Sacramento, CA', owner: 'Lisa Park', businessType: 'Public Utilities' },
  { id: 5, name: 'Green Power Solutions', location: 'Austin, TX', owner: 'David Brown', businessType: 'Renewable Energy' },
])

const selectedCompanyId = ref<number | null>(null)

// Service types
const serviceTypes = [
  'Feasibility Study',
  'Process Optimization',
  'Engineering Design',
  'Compliance Audit',
  'Energy Assessment',
  'Supply Chain Analysis',
  'Water Treatment Design',
  'Other',
]

// Computed
const isScenarioNew = computed(() => scenario.value === 'new')
const selectedCompany = computed(() =>
  existingCompanies.value.find(c => c.id === selectedCompanyId.value)
)

const totalSteps = computed(() => isScenarioNew.value ? 4 : 3)

// Actions
const resetForm = () => {
  currentStep.value = 0
  scenario.value = 'new'
  companyForm.value = { name: '', location: '', owner: '', businessType: '' }
  repForm.value = { fullName: '', jobTitle: '', email: '', phone: '' }
  projectForm.value = { name: '', serviceType: '', description: '', startDate: '', endDate: '' }
  selectedCompanyId.value = null
}

const nextStep = () => {
  if (currentStep.value < totalSteps.value - 1) {
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

const selectScenario = (value: string) => {
  scenario.value = value === 'new' ? 'new' : 'existing'
  if (scenario.value === 'existing') {
    currentStep.value = 0 // Reset to company selection
  }
}

const handleSubmit = () => {
  const data: WizardData = {
    scenario: scenario.value,
    company: { ...companyForm.value },
    representative: { ...repForm.value },
    project: { ...projectForm.value },
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

      <div class="flex-1 overflow-y-auto">
        <!-- Scenario Selection (Screen 1) -->
        <div v-if="currentStep === 0 && isScenarioNew" class="space-y-6">
          <div class="text-center">
            <h3 class="text-lg font-semibold mb-2">What type of project is this?</h3>
            <p class="text-muted-foreground">Select whether this is for a new customer or an existing one.</p>
          </div>

          <div class="grid gap-4">
            <Card
              class="cursor-pointer hover:border-primary transition-colors"
              @click="selectScenario('new')"
            >
              <CardHeader>
                <CardTitle>New Customer</CardTitle>
                <CardDescription>
                  Create a new customer company and project from scratch.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card
              class="cursor-pointer hover:border-primary transition-colors"
              @click="selectScenario('existing')"
            >
              <CardHeader>
                <CardTitle>Existing Customer</CardTitle>
                <CardDescription>
                  Add a new project for an existing customer company.
                </CardDescription>
              </CardHeader>
            </Card>
          </div>
        </div>

        <!-- Scenario B: Company Selection (Screen 1 for existing) -->
        <div v-if="currentStep === 0 && !isScenarioNew" class="space-y-4">
          <h3 class="text-lg font-semibold">Select Customer Company</h3>
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
              <p>Location: {{ selectedCompany.location }}</p>
              <p>Owner: {{ selectedCompany.owner }}</p>
              <p>Business: {{ selectedCompany.businessType }}</p>
            </div>
          </div>
        </div>

        <!-- Scenario A: Company Details (Screen 1 for new) -->
        <div v-if="currentStep === 1 && isScenarioNew" class="space-y-4">
          <h3 class="text-lg font-semibold">Customer Company Details</h3>

          <div class="space-y-2">
            <Label for="companyName">Company Name *</Label>
            <Input
              id="companyName"
              v-model="companyForm.name"
              placeholder="Enter company name"
            />
          </div>

          <div class="space-y-2">
            <Label for="location">Location *</Label>
            <Input
              id="location"
              v-model="companyForm.location"
              placeholder="City, State"
            />
          </div>

          <div class="space-y-2">
            <Label for="owner">Company Owner *</Label>
            <Input
              id="owner"
              v-model="companyForm.owner"
              placeholder="Owner's full name"
            />
          </div>

          <div class="space-y-2">
            <Label for="businessType">Business Type / Description *</Label>
            <Textarea
              id="businessType"
              v-model="companyForm.businessType"
              placeholder="Describe the company's business"
              rows="3"
            />
          </div>
        </div>

        <!-- Authorized Representative (Screen 2 for new, Screen 2 for existing) -->
        <div v-if="currentStep === (isScenarioNew ? 2 : 1)" class="space-y-4">
          <h3 class="text-lg font-semibold">Authorized Representative</h3>
          <p class="text-sm text-muted-foreground">
            Who will be the primary contact for this project?
          </p>

          <div class="space-y-2">
            <Label for="fullName">Full Name *</Label>
            <Input
              id="fullName"
              v-model="repForm.fullName"
              placeholder="Enter full name"
            />
          </div>

          <div class="space-y-2">
            <Label for="jobTitle">Job Title *</Label>
            <Input
              id="jobTitle"
              v-model="repForm.jobTitle"
              placeholder="e.g., Project Manager"
            />
          </div>

          <div class="space-y-2">
            <Label for="email">Email Address *</Label>
            <Input
              id="email"
              v-model="repForm.email"
              type="email"
              placeholder="email@company.com"
            />
          </div>

          <div class="space-y-2">
            <Label for="phone">Phone Number</Label>
            <Input
              id="phone"
              v-model="repForm.phone"
              placeholder="(555) 123-4567"
            />
          </div>
        </div>

        <!-- Project Overview (Screen 3 for new, Screen 3 for existing) -->
        <div v-if="currentStep === (isScenarioNew ? 3 : 2)" class="space-y-4">
          <h3 class="text-lg font-semibold">Project Overview</h3>

          <div class="space-y-2">
            <Label for="projectName">Project Name *</Label>
            <Input
              id="projectName"
              v-model="projectForm.name"
              placeholder="Enter project name"
            />
          </div>

          <div class="space-y-2">
            <Label for="serviceType">Service Type *</Label>
            <Select v-model="projectForm.serviceType">
              <SelectTrigger>
                <SelectValue placeholder="Select a service type..." />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem
                    v-for="service in serviceTypes"
                    :key="service"
                    :value="service"
                  >
                    {{ service }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>

          <div class="space-y-2">
            <Label for="description">Description</Label>
            <Textarea
              id="description"
              v-model="projectForm.description"
              placeholder="Brief project description"
              rows="3"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <Label for="startDate">Estimated Start Date *</Label>
              <Input
                id="startDate"
                v-model="projectForm.startDate"
                type="date"
              />
            </div>

            <div class="space-y-2">
              <Label for="endDate">Estimated Completion Date *</Label>
              <Input
                id="endDate"
                v-model="projectForm.endDate"
                type="date"
              />
            </div>
          </div>
        </div>

        <!-- Finish (Screen 4 for new) -->
        <div v-if="currentStep === 3 && isScenarioNew" class="space-y-6">
          <h3 class="text-lg font-semibold">Review & Submit</h3>

          <Card>
            <CardHeader>
              <CardTitle>Customer Company</CardTitle>
            </CardHeader>
            <CardContent class="space-y-2">
              <p><strong>Name:</strong> {{ companyForm.name }}</p>
              <p><strong>Location:</strong> {{ companyForm.location }}</p>
              <p><strong>Owner:</strong> {{ companyForm.owner }}</p>
              <p><strong>Business:</strong> {{ companyForm.businessType }}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Authorized Representative</CardTitle>
            </CardHeader>
            <CardContent class="space-y-2">
              <p><strong>Name:</strong> {{ repForm.fullName }}</p>
              <p><strong>Title:</strong> {{ repForm.jobTitle }}</p>
              <p><strong>Email:</strong> {{ repForm.email }}</p>
              <p><strong>Phone:</strong> {{ repForm.phone || 'Not provided' }}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Project Overview</CardTitle>
            </CardHeader>
            <CardContent class="space-y-2">
              <p><strong>Project:</strong> {{ projectForm.name }}</p>
              <p><strong>Service:</strong> {{ projectForm.serviceType }}</p>
              <p><strong>Description:</strong> {{ projectForm.description || 'Not provided' }}</p>
              <p><strong>Start:</strong> {{ projectForm.startDate }}</p>
              <p><strong>End:</strong> {{ projectForm.endDate }}</p>
            </CardContent>
          </Card>

          <div class="bg-emerald-50 border border-emerald-200 rounded-lg p-4">
            <p class="text-sm text-emerald-800">
              <strong>Note:</strong> An email will be sent to the authorized representative with a link to review the provided information and complete any additional details as needed. Login credentials will be created for this new customer.
            </p>
          </div>
        </div>

        <!-- Finish (Screen 3 for existing) -->
        <div v-if="currentStep === 2 && !isScenarioNew" class="space-y-6">
          <h3 class="text-lg font-semibold">Review & Submit</h3>

          <Card>
            <CardHeader>
              <CardTitle>Customer Company</CardTitle>
            </CardHeader>
            <CardContent class="space-y-2">
              <p><strong>Name:</strong> {{ selectedCompany?.name }}</p>
              <p><strong>Location:</strong> {{ selectedCompany?.location }}</p>
              <p><strong>Owner:</strong> {{ selectedCompany?.owner }}</p>
              <p><strong>Business:</strong> {{ selectedCompany?.businessType }}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Authorized Representative</CardTitle>
            </CardHeader>
            <CardContent class="space-y-2">
              <p><strong>Name:</strong> {{ repForm.fullName }}</p>
              <p><strong>Title:</strong> {{ repForm.jobTitle }}</p>
              <p><strong>Email:</strong> {{ repForm.email }}</p>
              <p><strong>Phone:</strong> {{ repForm.phone || 'Not provided' }}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Project Overview</CardTitle>
            </CardHeader>
            <CardContent class="space-y-2">
              <p><strong>Project:</strong> {{ projectForm.name }}</p>
              <p><strong>Service:</strong> {{ projectForm.serviceType }}</p>
              <p><strong>Description:</strong> {{ projectForm.description || 'Not provided' }}</p>
              <p><strong>Start:</strong> {{ projectForm.startDate }}</p>
              <p><strong>End:</strong> {{ projectForm.endDate }}</p>
            </CardContent>
          </Card>

          <div class="bg-emerald-50 border border-emerald-200 rounded-lg p-4">
            <p class="text-sm text-emerald-800">
              <strong>Note:</strong> An email will be sent to the authorized representative with a link to review the provided information and complete any additional details as needed.
            </p>
          </div>
        </div>
      </div>

      <Separator />

      <!-- Footer with navigation -->
      <DialogFooter class="flex flex-col sm:flex-row gap-2 sm:justify-between">
        <Button variant="outline" @click="handleClose">Cancel</Button>

        <div class="flex gap-2">
          <Button
            v-if="currentStep > 0"
            variant="outline"
            @click="prevStep"
          >
            Previous
          </Button>

          <Button
            v-if="currentStep < totalSteps - 1"
            @click="nextStep"
          >
            Next
          </Button>

          <Button
            v-if="currentStep === totalSteps - 1"
            @click="handleSubmit"
          >
            Submit Wizard
          </Button>
        </div>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
