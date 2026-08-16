<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRole } from '@/composables/useRole'

const { isClient, isProvider } = useRole()

const activeTab = ref('profile')

const profile = ref({
  fullName: 'John Doe',
  email: 'john.doe@example.com',
  phone: '+63 917 123 4567',
  twoFactor: false,
})

// ---------- Client: Company Profile ----------
const company = ref({
  name: 'ABC Manufacturing',
  address: '123 Industrial Ave, Cebu City, Philippines',
  contactDetails: 'ops@abcmfg.com &middot; +63 32 234 5678',
  businessType: 'Manufacturing',
})

const clientTeam = ref([
  { id: 1, name: 'Maria Santos', email: 'maria.santos@abcmfg.com', role: 'Authorized Representative', status: 'Active' },
  { id: 2, name: 'Pedro Cruz', email: 'pedro.cruz@abcmfg.com', role: 'Operations Manager', status: 'Active' },
  { id: 3, name: 'Liza Reyes', email: 'liza.reyes@abcmfg.com', role: 'Finance', status: 'Invited' },
])

const inviteForm = ref({ name: '', email: '', role: '' })
const inviteMember = () => {
  if (!inviteForm.value.email.trim()) return
  clientTeam.value.push({
    id: Date.now(),
    name: inviteForm.value.name || inviteForm.value.email,
    email: inviteForm.value.email,
    role: inviteForm.value.role || 'Team Member',
    status: 'Invited',
  })
  inviteForm.value = { name: '', email: '', role: '' }
  alert('Invitation sent. The team member will receive an email with an account setup link.')
}

// ---------- Provider: Communication Settings ----------
const communication = ref({
  emailSignature: true,
  autoReply: true,
  autoReplyText: 'Thank you for your message. Our team will respond within one business day.',
  callNotifications: true,
  messageNotifications: true,
  quietHours: false,
})

// ---------- Notifications (all roles) ----------
const notificationPrefs = ref({
  email: {
    projectCreated: true,
    newMessage: true,
    projectUpdate: true,
    documentUploaded: true,
    documentRequested: true,
    taskAssigned: true,
    taskStatusChanged: true,
    projectStatusChanged: true,
    announcement: true,
    teamInvitation: true,
  },
  inApp: {
    newMessage: true,
    documentUploaded: true,
    taskAssigned: true,
    projectStatusChanged: true,
    announcement: true,
  },
})

const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

// ---------- Role-based tabs ----------
const tabs = computed(() => {
  if (isClient.value) {
    return [
      { id: 'profile', label: 'Profile' },
      { id: 'company', label: 'Company Profile' },
      { id: 'team', label: 'Team & Invitations' },
      { id: 'notifications', label: 'Notifications' },
      { id: 'security', label: 'Security' },
    ]
  }
  if (isProvider.value) {
    return [
      { id: 'profile', label: 'Profile' },
      { id: 'communication', label: 'Communication' },
      { id: 'notifications', label: 'Notifications' },
      { id: 'security', label: 'Security' },
    ]
  }
  // Admin: personal settings only (system settings live in the Admin view)
  return [
    { id: 'profile', label: 'Profile' },
    { id: 'notifications', label: 'Notifications' },
    { id: 'security', label: 'Security' },
  ]
})

const heading = computed(() =>
  isClient.value ? 'Client Settings' : isProvider.value ? 'Provider Settings' : 'Admin Settings'
)
const subheading = computed(() =>
  isClient.value
    ? 'Manage your profile, company information, and team.'
    : isProvider.value
      ? 'Manage your profile and communication preferences.'
      : 'Manage your personal account settings.'
)

const saveProfile = () => alert('Profile updated successfully!')
const saveCompany = () => alert('Company profile saved!')
const saveCommunication = () => alert('Communication settings saved!')
const saveNotifications = () => alert('Notification preferences saved!')

const changePassword = () => {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    alert('New passwords do not match')
    return
  }
  if (passwordForm.value.newPassword.length < 8) {
    alert('Password must be at least 8 characters')
    return
  }
  alert('Password changed successfully!')
  passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
}

const formatKey = (key: string) =>
  key.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">{{ heading }}</h1>
      <p class="text-gray-600 mt-1">{{ subheading }}</p>
    </div>

    <!-- Tabs -->
    <div class="border-b border-gray-200 mb-6">
      <nav class="-mb-px flex gap-8 overflow-x-auto">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          @click="activeTab = tab.id"
          :class="[
            'py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors',
            activeTab === tab.id
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300',
          ]"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Profile Tab (all roles) -->
    <div v-if="activeTab === 'profile'" class="bg-white rounded-lg shadow p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-6">Profile Information</h2>

      <div class="flex items-center gap-6 mb-6">
        <div class="w-20 h-20 rounded-full bg-blue-600 flex items-center justify-center text-white text-2xl font-medium">
          {{ profile.fullName.charAt(0) }}
        </div>
        <div>
          <button class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors text-sm">
            Change Avatar
          </button>
          <p class="text-sm text-gray-500 mt-1">JPG, PNG or GIF. Max 2MB.</p>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
          <input
            v-model="profile.fullName"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Email</label>
          <input
            v-model="profile.email"
            type="email"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Phone</label>
          <input
            v-model="profile.phone"
            type="tel"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      <div class="mt-6 flex justify-end">
        <button
          @click="saveProfile"
          class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
        >
          Save Changes
        </button>
      </div>
    </div>

    <!-- Company Profile Tab (client only) -->
    <div v-if="activeTab === 'company'" class="bg-white rounded-lg shadow p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-2">Company Profile</h2>
      <p class="text-sm text-gray-600 mb-6">Your company information as seen by your consultants.</p>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Company Name</label>
          <input
            v-model="company.name"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Business Type</label>
          <input
            v-model="company.businessType"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Address</label>
          <input
            v-model="company.address"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Contact Details</label>
          <input
            v-model="company.contactDetails"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      <div class="mt-6 flex justify-end">
        <button
          @click="saveCompany"
          class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
        >
          Save Company Profile
        </button>
      </div>
    </div>

    <!-- Team & Invitations Tab (client only) -->
    <div v-if="activeTab === 'team'" class="space-y-6">
      <!-- Client Team Members -->
      <div class="bg-white rounded-lg shadow">
        <div class="p-6 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Client Team Members</h2>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Role</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="member in clientTeam" :key="member.id" class="hover:bg-gray-50">
                <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ member.name }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ member.email }}</td>
                <td class="px-6 py-4 text-sm text-gray-600">{{ member.role }}</td>
                <td class="px-6 py-4">
                  <span
                    :class="[
                      'px-2 py-1 text-xs font-medium rounded-full',
                      member.status === 'Active'
                        ? 'bg-green-100 text-green-800'
                        : 'bg-yellow-100 text-yellow-800',
                    ]"
                  >
                    {{ member.status }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Invite Team Member -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Invite a Team Member</h2>
        <p class="text-sm text-gray-600 mb-4">
          Invited members receive an email with an account setup link and are added to your company.
        </p>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
            <input
              v-model="inviteForm.name"
              type="text"
              placeholder="Juan Dela Cruz"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email Address</label>
            <input
              v-model="inviteForm.email"
              type="email"
              placeholder="juan@company.com"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <input
              v-model="inviteForm.role"
              type="text"
              placeholder="Team Member"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
        <div class="mt-4 flex justify-end">
          <button
            @click="inviteMember"
            class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            <i class="fas fa-paper-plane mr-1" /> Send Invitation
          </button>
        </div>
      </div>
    </div>

    <!-- Communication Tab (provider only) -->
    <div v-if="activeTab === 'communication'" class="bg-white rounded-lg shadow p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">Communication Settings</h2>
      <div class="space-y-4 max-w-xl">
        <div class="flex items-center justify-between py-2">
          <div>
            <p class="text-gray-700">Email Signature</p>
            <p class="text-sm text-gray-500">Append your signature to outgoing messages</p>
          </div>
          <label class="relative inline-flex items-center cursor-pointer">
            <input v-model="communication.emailSignature" type="checkbox" class="sr-only peer" />
            <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          </label>
        </div>

        <div class="flex items-center justify-between py-2">
          <div>
            <p class="text-gray-700">Auto-Reply</p>
            <p class="text-sm text-gray-500">Automatically acknowledge new client messages</p>
          </div>
          <label class="relative inline-flex items-center cursor-pointer">
            <input v-model="communication.autoReply" type="checkbox" class="sr-only peer" />
            <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          </label>
        </div>

        <div v-if="communication.autoReply" class="pl-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Auto-Reply Message</label>
          <textarea
            v-model="communication.autoReplyText"
            rows="2"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
          />
        </div>

        <div class="flex items-center justify-between py-2">
          <div>
            <p class="text-gray-700">Message Notifications</p>
            <p class="text-sm text-gray-500">Notify me about new project messages</p>
          </div>
          <label class="relative inline-flex items-center cursor-pointer">
            <input v-model="communication.messageNotifications" type="checkbox" class="sr-only peer" />
            <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          </label>
        </div>

        <div class="flex items-center justify-between py-2">
          <div>
            <p class="text-gray-700">Call Notifications</p>
            <p class="text-sm text-gray-500">Notify me about scheduled call reminders</p>
          </div>
          <label class="relative inline-flex items-center cursor-pointer">
            <input v-model="communication.callNotifications" type="checkbox" class="sr-only peer" />
            <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          </label>
        </div>

        <div class="flex items-center justify-between py-2">
          <div>
            <p class="text-gray-700">Quiet Hours</p>
            <p class="text-sm text-gray-500">Mute notifications outside working hours</p>
          </div>
          <label class="relative inline-flex items-center cursor-pointer">
            <input v-model="communication.quietHours" type="checkbox" class="sr-only peer" />
            <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          </label>
        </div>
      </div>

      <div class="mt-6 flex justify-end">
        <button
          @click="saveCommunication"
          class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
        >
          Save Communication Settings
        </button>
      </div>
    </div>

    <!-- Notifications Tab (all roles) -->
    <div v-if="activeTab === 'notifications'" class="space-y-6">
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Email Notifications</h2>
        <div class="space-y-3">
          <div
            v-for="(_value, key) in notificationPrefs.email"
            :key="'email-' + key"
            class="flex items-center justify-between py-2"
          >
            <span class="text-gray-700">{{ formatKey(key as string) }}</span>
            <label class="relative inline-flex items-center cursor-pointer">
              <input
                v-model="notificationPrefs.email[key as keyof typeof notificationPrefs.email]"
                type="checkbox"
                class="sr-only peer"
              />
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">In-App Notifications</h2>
        <div class="space-y-3">
          <div
            v-for="(_value, key) in notificationPrefs.inApp"
            :key="'inApp-' + key"
            class="flex items-center justify-between py-2"
          >
            <span class="text-gray-700">{{ formatKey(key as string) }}</span>
            <label class="relative inline-flex items-center cursor-pointer">
              <input
                v-model="notificationPrefs.inApp[key as keyof typeof notificationPrefs.inApp]"
                type="checkbox"
                class="sr-only peer"
              />
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>
        </div>
      </div>

      <div class="flex justify-end">
        <button
          @click="saveNotifications"
          class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
        >
          Save Preferences
        </button>
      </div>
    </div>

    <!-- Security Tab (all roles) -->
    <div v-if="activeTab === 'security'" class="space-y-6">
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Two-Factor Authentication</h2>
        <div class="flex items-center justify-between py-2 max-w-xl">
          <div>
            <p class="text-gray-700">2FA</p>
            <p class="text-sm text-gray-500">Require a verification code when signing in</p>
          </div>
          <label class="relative inline-flex items-center cursor-pointer">
            <input v-model="profile.twoFactor" type="checkbox" class="sr-only peer" />
            <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          </label>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-6">Change Password</h2>

        <div class="space-y-4 max-w-md">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Current Password</label>
            <input
              v-model="passwordForm.currentPassword"
              type="password"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">New Password</label>
            <input
              v-model="passwordForm.newPassword"
              type="password"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
            <input
              v-model="passwordForm.confirmPassword"
              type="password"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <button
            @click="changePassword"
            class="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            Change Password
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
