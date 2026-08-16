<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useRole } from '@/composables/useRole'

const { isClient, isProvider, isAdmin } = useRole()
const route = useRoute()
const projectId = computed(() => route.params.id)

const project = ref({
  id: projectId.value,
  name: 'Manufacturing Process Optimization',
  client: 'ABC Manufacturing',
  serviceType: 'Process Consulting',
  status: 'In Progress',
  progress: 65,
  scope: 'Comprehensive optimization of manufacturing processes across Lines 1–4, including bottleneck analysis, energy consumption reduction, and waste minimization.',
  objectives: 'Reduce cycle time by 15%, cut energy costs by 10%, and improve first-pass yield to 98% within the project timeline.',
  startDate: '2026-06-01',
  dueDate: '2026-09-15',
  budget: 250000,
  team: [
    { id: 1, name: 'John Doe', role: 'Lead Engineer', avatar: 'JD' },
    { id: 2, name: 'Jane Smith', role: 'Process Analyst', avatar: 'JS' },
    { id: 3, name: 'Bob Wilson', role: 'Quality Assurance', avatar: 'BW' },
    { id: 4, name: 'Alice Brown', role: 'Project Manager', avatar: 'AB' },
  ],
  company: {
    name: 'ABC Manufacturing',
    address: '123 Industrial Ave, Cebu City, Philippines',
    businessType: 'Manufacturing',
    representative: 'Maria Santos',
  },
  documents: [
    { id: 1, name: 'Bottleneck Analysis — Line 3', category: 'Deliverable', version: 'v1.2', uploadedBy: 'John Doe', date: '2026-08-15' },
    { id: 2, name: 'Production Records Q2', category: 'Client-Submitted', version: 'v1.0', uploadedBy: 'Maria Santos', date: '2026-07-20' },
    { id: 3, name: 'Energy Consumption Data Request', category: 'Requested', version: 'v1.0', uploadedBy: 'Jane Smith', date: '2026-07-18' },
  ],
  messages: [
    { id: 1, sender: 'Maria Santos', isClient: true, time: '2026-08-15 09:14', text: 'Can we schedule a walkthrough of Line 3 this week?', read: false },
    { id: 2, sender: 'John Doe', isClient: false, time: '2026-08-15 10:02', text: 'Sure — Thursday 10 AM works for us. I will bring the draft findings.', read: true },
    { id: 3, sender: 'Maria Santos', isClient: true, time: '2026-08-15 10:15', text: 'Thursday 10 AM confirmed. Thanks!', read: true },
  ],
})

const statusColors: Record<string, string> = {
  'In Progress': 'bg-blue-100 text-blue-800',
  'Not Started': 'bg-gray-100 text-gray-800',
  'Planning': 'bg-yellow-100 text-yellow-800',
  'Completed': 'bg-green-100 text-green-800',
  'On Hold': 'bg-red-100 text-red-800',
}

const docCategoryColors: Record<string, string> = {
  'Deliverable': 'bg-blue-100 text-blue-800',
  'Client-Submitted': 'bg-green-100 text-green-800',
  'Requested': 'bg-yellow-100 text-yellow-800',
}

const projectStatuses = ['Not Started', 'In Progress', 'On Hold', 'Completed']

// ---------- Role-based tabs ----------
const tabs = computed(() => {
  const base = ['Overview', 'Documents', 'Messages']
  if (isClient.value) return [...base, 'Team']
  if (isProvider.value) return ['Overview', 'Client Company & Team', 'Documents', 'Messages']
  if (isAdmin.value) return ['Overview', 'Client Company & Team', 'Documents', 'Messages', 'Admin Controls']
  return base
})
const activeTab = ref('Overview')

// ---------- Updates (shared by provider/admin overview) ----------
const updates = ref([
  { id: 1, date: '2026-08-15', author: 'John Doe', text: 'Completed the bottleneck analysis for Line 3. Draft findings shared in the project documents.' },
  { id: 2, date: '2026-08-12', author: 'Alice Brown', text: 'Kicked off the energy audit scope with the client team.' },
  { id: 3, date: '2026-08-05', author: 'Jane Smith', text: 'Baseline data collection started on Lines 1 and 2.' },
])
const newUpdate = ref('')
const addUpdate = () => {
  if (!newUpdate.value.trim()) return
  updates.value.unshift({
    id: Date.now(),
    date: new Date().toISOString().slice(0, 10),
    author: 'You',
    text: newUpdate.value.trim(),
  })
  newUpdate.value = ''
}

// ---------- Messages ----------
const messageDraft = ref('')
const sendMessage = () => {
  if (!messageDraft.value.trim()) return
  project.value.messages.push({
    id: Date.now(),
    sender: 'You',
    isClient: false,
    time: new Date().toLocaleString(),
    text: messageDraft.value.trim(),
    read: true,
  })
  messageDraft.value = ''
}

// ---------- Admin controls ----------
const adminForm = ref({
  status: project.value.status,
  scope: project.value.scope,
  dueDate: project.value.dueDate,
  teamAssignment: project.value.team.map(m => m.name),
})
const availableTeam = ['John Doe', 'Jane Smith', 'Bob Wilson', 'Alice Brown', 'Carol White', 'David Green']
const toggleTeamMember = (name: string) => {
  const idx = adminForm.value.teamAssignment.indexOf(name)
  if (idx >= 0) adminForm.value.teamAssignment.splice(idx, 1)
  else adminForm.value.teamAssignment.push(name)
}
const saveAdminChanges = () => {
  project.value.status = adminForm.value.status
  project.value.scope = adminForm.value.scope
  project.value.dueDate = adminForm.value.dueDate
  alert('Project configuration saved.')
}
</script>

<template>
  <div>
    <!-- Header -->
    <div class="mb-6">
      <div class="flex items-center justify-between mb-4">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">{{ project.name }}</h1>
          <p class="text-gray-600 mt-1">{{ project.client }} &middot; {{ project.serviceType }}</p>
        </div>
        <span :class="['px-3 py-1 text-sm font-medium rounded-full', statusColors[project.status]]">
          {{ project.status }}
        </span>
      </div>

      <div class="flex items-center gap-6 text-sm text-gray-600">
        <span><i class="fas fa-calendar-days mr-1"></i>Start: {{ project.startDate }}</span>
        <span><i class="fas fa-bullseye mr-1"></i>Due: {{ project.dueDate }}</span>
        <span v-if="!isClient"><i class="fas fa-coins mr-1"></i>Budget: ${{ project.budget.toLocaleString() }}</span>
      </div>

      <div class="mt-4 w-full bg-gray-200 rounded-full h-2">
        <div
          class="bg-blue-600 h-2 rounded-full transition-all"
          :style="{ width: project.progress + '%' }"
        />
      </div>
      <p class="text-sm text-gray-600 mt-1">{{ project.progress }}% complete</p>
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
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300',
          ]"
        >
          {{ tab }}
        </button>
      </nav>
    </div>

    <!-- ================= OVERVIEW ================= -->
    <div v-if="activeTab === 'Overview'">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Scope</h2>
          <p class="text-gray-700">{{ project.scope }}</p>
        </div>
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Objectives</h2>
          <p class="text-gray-700">{{ project.objectives }}</p>
        </div>
      </div>

      <!-- Add Update (provider/admin only) -->
      <div v-if="isProvider || isAdmin" class="bg-white rounded-lg shadow p-6 mb-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Add Update</h2>
        <textarea
          v-model="newUpdate"
          rows="3"
          placeholder="Post a dated progress comment..."
          class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
        />
        <div class="mt-3 flex justify-end">
          <button
            @click="addUpdate"
            class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
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
        <div class="relative">
          <div class="absolute left-3 top-1 bottom-1 w-px bg-gray-200" />
          <div
            v-for="update in (isClient ? updates.slice(0, 3) : updates)"
            :key="update.id"
            class="relative pl-10 pb-6 last:pb-0"
          >
            <span class="absolute left-1.5 top-1 w-3 h-3 rounded-full bg-blue-500 ring-4 ring-blue-100" />
            <p class="text-xs text-gray-500">{{ update.date }} &middot; {{ update.author }}</p>
            <p class="text-sm text-gray-700 mt-1">{{ update.text }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= CLIENT COMPANY & TEAM ================= -->
    <div v-if="activeTab === 'Client Company & Team'" class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Client Company</h2>
        <div class="space-y-4">
          <div>
            <p class="text-sm text-gray-500">Company Name</p>
            <p class="text-gray-900 font-medium">{{ project.company.name }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Address</p>
            <p class="text-gray-900">{{ project.company.address }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Business Type</p>
            <p class="text-gray-900">{{ project.company.businessType }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500">Authorized Representative</p>
            <p class="text-gray-900">{{ project.company.representative }}</p>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">
          {{ isAdmin ? 'Assigned Consultant Team' : 'Assigned Team' }}
        </h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="member in project.team"
            :key="member.id"
            class="flex items-center gap-4 p-4 border border-gray-200 rounded-lg"
          >
            <div class="w-12 h-12 rounded-full bg-blue-600 flex items-center justify-center text-white font-medium">
              {{ member.avatar }}
            </div>
            <div>
              <h3 class="font-medium text-gray-900">{{ member.name }}</h3>
              <p class="text-sm text-gray-600">{{ member.role }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= TEAM (client) ================= -->
    <div v-if="activeTab === 'Team'" class="bg-white rounded-lg shadow p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">Your Project Team</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div
          v-for="member in project.team"
          :key="member.id"
          class="flex items-center gap-4 p-4 border border-gray-200 rounded-lg"
        >
          <div class="w-12 h-12 rounded-full bg-blue-600 flex items-center justify-center text-white font-medium">
            {{ member.avatar }}
          </div>
          <div>
            <h3 class="font-medium text-gray-900">{{ member.name }}</h3>
            <p class="text-sm text-gray-600">{{ member.role }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= DOCUMENTS ================= -->
    <div v-if="activeTab === 'Documents'" class="bg-white rounded-lg shadow">
      <div class="p-6 border-b border-gray-200 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-gray-900">Project Documents</h2>
        <button class="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
          <i class="fas fa-upload mr-1" /> Upload Document
        </button>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Document Name</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Version</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Uploaded By</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr
              v-for="doc in project.documents"
              :key="doc.id"
              class="hover:bg-gray-50"
            >
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <i class="fas fa-file-lines text-blue-500 text-lg" />
                  <span class="font-medium text-gray-900 text-sm">{{ doc.name }}</span>
                </div>
              </td>
              <td class="px-6 py-4">
                <span :class="['px-2 py-1 text-xs font-medium rounded-full', docCategoryColors[doc.category]]">
                  {{ doc.category }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-600">{{ doc.version }}</td>
              <td class="px-6 py-4 text-sm text-gray-600">{{ doc.uploadedBy }}</td>
              <td class="px-6 py-4 text-sm text-gray-600">{{ doc.date }}</td>
              <td class="px-6 py-4">
                <button class="text-blue-600 hover:text-blue-700 text-sm font-medium">Download</button>
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
        <div
          v-for="msg in project.messages"
          :key="msg.id"
          :class="['flex', msg.isClient ? 'justify-start' : 'justify-end']"
        >
          <div
            :class="[
              'max-w-md rounded-lg p-4',
              msg.isClient ? 'bg-gray-100' : 'bg-blue-600 text-white',
            ]"
          >
            <div class="flex items-center justify-between gap-4 mb-1">
              <p :class="['text-xs font-medium', msg.isClient ? 'text-gray-600' : 'text-blue-100']">
                {{ msg.sender }}
              </p>
              <p :class="['text-xs', msg.isClient ? 'text-gray-400' : 'text-blue-200']">
                {{ msg.time }}
                <i :class="msg.read ? 'fas fa-check-double' : 'far fa-circle'" class="ml-1" />
              </p>
            </div>
            <p class="text-sm">{{ msg.text }}</p>
          </div>
        </div>
      </div>
      <div class="p-6 border-t border-gray-200">
        <div class="flex gap-3">
          <textarea
            v-model="messageDraft"
            rows="2"
            placeholder="Type a message..."
            class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
          />
          <button
            @click="sendMessage"
            class="self-end bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
          >
            Send
          </button>
        </div>
      </div>
    </div>

    <!-- ================= ADMIN CONTROLS ================= -->
    <div v-if="activeTab === 'Admin Controls'" class="bg-white rounded-lg shadow p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-6">Project Configuration</h2>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select
            v-model="adminForm.status"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option v-for="s in projectStatuses" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Timeline (Due Date)</label>
          <input
            v-model="adminForm.dueDate"
            type="date"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div class="lg:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Scope</label>
          <textarea
            v-model="adminForm.scope"
            rows="4"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
          />
        </div>

        <div class="lg:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-2">Assign Team</label>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="member in availableTeam"
              :key="member"
              class="flex items-center gap-2 px-3 py-2 border border-gray-200 rounded-lg cursor-pointer hover:bg-gray-50"
            >
              <input
                type="checkbox"
                :checked="adminForm.teamAssignment.includes(member)"
                @change="toggleTeamMember(member)"
                class="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              />
              <span class="text-sm text-gray-700">{{ member }}</span>
            </label>
          </div>
        </div>
      </div>

      <div class="mt-6 flex justify-end">
        <button
          @click="saveAdminChanges"
          class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
        >
          Save Changes
        </button>
      </div>
    </div>
  </div>
</template>
