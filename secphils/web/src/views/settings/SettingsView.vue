<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRole } from '@/composables/useRole'
import {
  useGetMe,
  useUpdateMe,
  useGetMyCompany,
  useUpdateMyCompany,
  useGetCompanyTeam,
  useInviteTeamMember,
  useGetNotificationPreferences,
  useUpdateNotificationPreferences,
  useGetCommunicationSettings,
  useUpdateCommunicationSettings,
  useChangePassword,
  useEnable2fa,
  useVerify2faEnable,
  useDisable2fa,
  type CompanyTeamMember,
  type NotificationPreferences,
  type CommunicationSettings,
} from '@/services/api'

const { isClient, isUser } = useRole()

const activeTab = ref('profile')
const loading = ref(true)

// ---------- Feedback ----------
const notice = ref<{ type: 'success' | 'error'; text: string } | null>(null)
let noticeTimer: ReturnType<typeof setTimeout> | null = null
const flash = (type: 'success' | 'error', text: string) => {
  notice.value = { type, text }
  if (noticeTimer) clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => (notice.value = null), 4000)
}

// ---------- Profile (all roles) ----------
const profile = ref({
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  avatar: '',
  twoFactorEnabled: false,
})
const profileFullName = computed(() => (profile.value.firstName + ' ' + profile.value.lastName).trim() || 'User')
const profileInitial = computed(() => profileFullName.value.charAt(0).toUpperCase() || 'U')
const avatarInput = ref<HTMLInputElement | null>(null)
const savingProfile = ref(false)

async function loadProfile() {
  const u = await useGetMe()
  profile.value = {
    firstName: u?.firstName ?? '',
    lastName: u?.lastName ?? '',
    email: u?.email ?? '',
    phone: u?.phone ?? '',
    avatar: u?.avatar ?? '',
    twoFactorEnabled: !!u?.twoFactorEnabled,
  }
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await useUpdateMe({
      firstName: profile.value.firstName,
      lastName: profile.value.lastName,
      email: profile.value.email,
      phone: profile.value.phone,
    })
    localStorage.setItem('userName', profileFullName.value)
    flash('success', 'Profile updated successfully')
  } catch (e: any) {
    flash('error', e?.response?.data?.message ?? 'Failed to save profile')
  } finally {
    savingProfile.value = false
  }
}

function onAvatarSelected(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    flash('error', 'Image must be 2MB or smaller')
    return
  }
  const reader = new FileReader()
  reader.onload = async () => {
    profile.value.avatar = String(reader.result)
    try {
      await useUpdateMe({ avatar: profile.value.avatar })
      flash('success', 'Avatar updated')
    } catch (err: any) {
      flash('error', err?.response?.data?.message ?? 'Failed to update avatar')
    }
  }
  reader.readAsDataURL(file)
}

// ---------- Client: Company Profile ----------
const company = ref({ name: '', businessType: '', address: '', contactDetails: '' })

async function loadCompany() {
  try {
    const c = await useGetMyCompany()
    company.value = {
      name: c?.name ?? '',
      businessType: c?.industrySectors ?? '',
      address: c?.location ?? '',
      contactDetails: c?.contactDetails ?? '',
    }
  } catch {
    // Client not linked to a company yet — leave the form empty.
  }
}

async function saveCompany() {
  try {
    await useUpdateMyCompany({
      name: company.value.name,
      industrySectors: company.value.businessType,
      location: company.value.address,
      contactDetails: company.value.contactDetails,
    })
    flash('success', 'Company profile saved')
  } catch (e: any) {
    flash('error', e?.response?.data?.message ?? 'Failed to save company profile')
  }
}

// ---------- Client: Team & Invitations ----------
const clientTeam = ref<CompanyTeamMember[]>([])
const inviteForm = ref({ name: '', email: '', role: '' })
const inviting = ref(false)

async function loadTeam() {
  try {
    clientTeam.value = await useGetCompanyTeam()
  } catch {
    clientTeam.value = []
  }
}

async function inviteMember() {
  if (!inviteForm.value.email.trim()) {
    flash('error', 'Please enter an email address.')
    return
  }
  inviting.value = true
  try {
    await useInviteTeamMember({
      name: inviteForm.value.name.trim() || inviteForm.value.email.trim(),
      email: inviteForm.value.email.trim(),
      role: inviteForm.value.role.trim() || 'Team Member',
    })
    inviteForm.value = { name: '', email: '', role: '' }
    await loadTeam()
    flash('success', 'Invitation sent. The team member will receive an email with an account setup link.')
  } catch (e: any) {
    flash('error', e?.response?.data?.message ?? 'Failed to send invitation')
  } finally {
    inviting.value = false
  }
}

// ---------- Communication (USER only) ----------
const communication = ref<CommunicationSettings>({
  emailSignature: true,
  autoReply: true,
  autoReplyText: '',
  callNotifications: true,
  messageNotifications: true,
  quietHours: false,
})

async function loadCommunication() {
  try {
    communication.value = await useGetCommunicationSettings()
  } catch {
    // keep defaults
  }
}

async function saveCommunication() {
  try {
    await useUpdateCommunicationSettings({ ...communication.value })
    flash('success', 'Communication settings saved')
  } catch (e: any) {
    flash('error', e?.response?.data?.message ?? 'Failed to save communication settings')
  }
}

// ---------- Notifications (all roles) ----------
const notificationPrefs = ref<NotificationPreferences>({ email: {}, inApp: {} })

async function loadNotificationPrefs() {
  try {
    notificationPrefs.value = await useGetNotificationPreferences()
  } catch {
    // keep defaults (empty until loaded)
  }
}

async function saveNotifications() {
  try {
    await useUpdateNotificationPreferences({
      email: notificationPrefs.value.email,
      inApp: notificationPrefs.value.inApp,
    })
    flash('success', 'Notification preferences saved')
  } catch (e: any) {
    flash('error', e?.response?.data?.message ?? 'Failed to save preferences')
  }
}

// ---------- Security: password ----------
const passwordForm = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })
const changingPassword = ref(false)

async function changePassword() {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    flash('error', 'New passwords do not match')
    return
  }
  if (passwordForm.value.newPassword.length < 8) {
    flash('error', 'Password must be at least 8 characters')
    return
  }
  changingPassword.value = true
  try {
    await useChangePassword(passwordForm.value.currentPassword, passwordForm.value.newPassword)
    passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    flash('success', 'Password changed successfully')
  } catch (e: any) {
    flash('error', e?.response?.data?.message ?? 'Failed to change password')
  } finally {
    changingPassword.value = false
  }
}

// ---------- Security: two-factor authentication ----------
const twoFactorEnabled = computed(() => profile.value.twoFactorEnabled)
const show2faSetup = ref(false)
const show2faDisable = ref(false)
const twoFactorSecret = ref('')
const twoFactorOtpauthUri = ref('')
const twoFactorCode = ref('')
const twoFactorBusy = ref(false)
const twoFactorError = ref('')

async function start2faSetup() {
  twoFactorError.value = ''
  twoFactorCode.value = ''
  twoFactorBusy.value = true
  try {
    const res = await useEnable2fa()
    twoFactorSecret.value = res.secret
    twoFactorOtpauthUri.value = res.otpauthUri
    show2faSetup.value = true
  } catch (e: any) {
    twoFactorError.value = e?.response?.data?.message ?? 'Failed to start 2FA setup'
  } finally {
    twoFactorBusy.value = false
  }
}

async function confirm2faEnable() {
  if (twoFactorCode.value.length !== 6) {
    twoFactorError.value = 'Enter the 6-digit code from your authenticator app'
    return
  }
  twoFactorBusy.value = true
  twoFactorError.value = ''
  try {
    await useVerify2faEnable(twoFactorSecret.value, twoFactorCode.value)
    profile.value.twoFactorEnabled = true
    show2faSetup.value = false
    twoFactorCode.value = ''
    flash('success', 'Two-factor authentication enabled')
  } catch (e: any) {
    twoFactorError.value = e?.response?.data?.message ?? 'Invalid verification code'
  } finally {
    twoFactorBusy.value = false
  }
}

async function confirm2faDisable() {
  if (twoFactorCode.value.length !== 6) {
    twoFactorError.value = 'Enter the 6-digit code from your authenticator app'
    return
  }
  twoFactorBusy.value = true
  twoFactorError.value = ''
  try {
    await useDisable2fa(twoFactorCode.value)
    profile.value.twoFactorEnabled = false
    show2faDisable.value = false
    twoFactorCode.value = ''
    flash('success', 'Two-factor authentication disabled')
  } catch (e: any) {
    twoFactorError.value = e?.response?.data?.message ?? 'Invalid verification code'
  } finally {
    twoFactorBusy.value = false
  }
}

function cancel2fa() {
  show2faSetup.value = false
  show2faDisable.value = false
  twoFactorCode.value = ''
  twoFactorError.value = ''
}

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
  if (isUser.value) {
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
  isClient.value ? 'Client Settings' : isUser.value ? 'User Settings' : 'Admin Settings'
)
const subheading = computed(() =>
  isClient.value
    ? 'Manage your profile, company information, and team.'
    : isUser.value
      ? 'Manage your profile and communication preferences.'
      : 'Manage your personal account settings.'
)

const formatKey = (key: string) =>
  key.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())

onMounted(async () => {
  loading.value = true
  try {
    await loadProfile()
  } catch {
    /* auth guard will redirect on failure */
  }
  try {
    await loadNotificationPrefs()
  } catch {
    /* keep defaults */
  }
  if (isClient.value) {
    try {
      await loadCompany()
    } catch {
      /* ignored */
    }
    try {
      await loadTeam()
    } catch {
      /* ignored */
    }
  }
  if (isUser.value) {
    try {
      await loadCommunication()
    } catch {
      /* keep defaults */
    }
  }
  loading.value = false
})
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">{{ heading }}</h1>
      <p class="text-gray-600 mt-1">{{ subheading }}</p>
    </div>

    <div
      v-if="notice"
      :class="[
        'mb-6 p-3 rounded-lg text-sm',
        notice.type === 'success' ? 'bg-green-50 border border-green-200 text-green-800' : 'bg-red-50 border border-red-200 text-red-700',
      ]"
    >
      {{ notice.text }}
    </div>

    <div v-if="loading" class="py-16 text-center text-gray-500">Loading settings…</div>

    <template v-else>
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
                ? 'border-emerald-600 text-emerald-600'
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
          <div
            v-if="profile.avatar"
            class="w-20 h-20 rounded-full bg-cover bg-center border border-gray-200"
            :style="{ backgroundImage: `url(${profile.avatar})` }"
          />
          <div
            v-else
            class="w-20 h-20 rounded-full bg-emerald-600 flex items-center justify-center text-white text-2xl font-medium"
          >
            {{ profileInitial }}
          </div>
          <div>
            <button
              type="button"
              @click="avatarInput?.click()"
              class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors text-sm"
            >
              Change Avatar
            </button>
            <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="onAvatarSelected" />
            <p class="text-sm text-gray-500 mt-1">JPG, PNG or GIF. Max 2MB.</p>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <input
                v-model="profile.firstName"
                type="text"
                placeholder="First name"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
              <input
                v-model="profile.lastName"
                type="text"
                placeholder="Last name"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input
              v-model="profile.email"
              type="email"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Phone</label>
            <input
              v-model="profile.phone"
              type="tel"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
        </div>

        <div class="mt-6 flex justify-end">
          <button
            @click="saveProfile"
            :disabled="savingProfile"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50"
          >
            <span v-if="savingProfile">Saving…</span>
            <span v-else>Save Changes</span>
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
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Business Type</label>
            <input
              v-model="company.businessType"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Company Address</label>
            <input
              v-model="company.address"
              type="text"
              placeholder="Full address (barangay, city/municipality, province, ZIP)"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Contact Details</label>
            <input
              v-model="company.contactDetails"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
        </div>

        <div class="mt-6 flex justify-end">
          <button
            @click="saveCompany"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium"
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
                          : member.status === 'Invited'
                            ? 'bg-yellow-100 text-yellow-800'
                            : 'bg-gray-100 text-gray-700',
                      ]"
                    >
                      {{ member.status }}
                    </span>
                  </td>
                </tr>
                <tr v-if="clientTeam.length === 0">
                  <td colspan="4" class="px-6 py-8 text-center text-sm text-gray-500">
                    No team members yet.
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
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Email Address</label>
              <input
                v-model="inviteForm.email"
                type="email"
                placeholder="juan@company.com"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
              <input
                v-model="inviteForm.role"
                type="text"
                placeholder="Team Member"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          </div>
          <div class="mt-4 flex justify-end">
            <button
              @click="inviteMember"
              :disabled="inviting"
              class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50"
            >
              <i class="fas fa-paper-plane mr-1" />
              {{ inviting ? 'Sending…' : 'Send Invitation' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Communication Tab (user only) -->
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
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-emerald-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
            </label>
          </div>

          <div class="flex items-center justify-between py-2">
            <div>
              <p class="text-gray-700">Auto-Reply</p>
              <p class="text-sm text-gray-500">Automatically acknowledge new client messages</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input v-model="communication.autoReply" type="checkbox" class="sr-only peer" />
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-emerald-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
            </label>
          </div>

          <div v-if="communication.autoReply" class="pl-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Auto-Reply Message</label>
            <textarea
              v-model="communication.autoReplyText"
              rows="2"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
            />
          </div>

          <div class="flex items-center justify-between py-2">
            <div>
              <p class="text-gray-700">Message Notifications</p>
              <p class="text-sm text-gray-500">Notify me about new project messages</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input v-model="communication.messageNotifications" type="checkbox" class="sr-only peer" />
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-emerald-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
            </label>
          </div>

          <div class="flex items-center justify-between py-2">
            <div>
              <p class="text-gray-700">Call Notifications</p>
              <p class="text-sm text-gray-500">Notify me about scheduled call reminders</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input v-model="communication.callNotifications" type="checkbox" class="sr-only peer" />
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-emerald-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
            </label>
          </div>

          <div class="flex items-center justify-between py-2">
            <div>
              <p class="text-gray-700">Quiet Hours</p>
              <p class="text-sm text-gray-500">Mute notifications outside working hours</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input v-model="communication.quietHours" type="checkbox" class="sr-only peer" />
              <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-emerald-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
            </label>
          </div>
        </div>

        <div class="mt-6 flex justify-end">
          <button
            @click="saveCommunication"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium"
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
                <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-emerald-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
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
                <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-emerald-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
              </label>
            </div>
          </div>
        </div>

        <div class="flex justify-end">
          <button
            @click="saveNotifications"
            class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium"
          >
            Save Preferences
          </button>
        </div>
      </div>

      <!-- Security Tab (all roles) -->
      <div v-if="activeTab === 'security'" class="space-y-6">
        <div class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Two-Factor Authentication</h2>
          <p class="text-sm text-gray-600 mb-4">Require a verification code when signing in.</p>

          <!-- Not enabled: setup -->
          <div v-if="!twoFactorEnabled && !show2faSetup">
            <button
              @click="start2faSetup"
              :disabled="twoFactorBusy"
              class="bg-emerald-600 text-white px-5 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50"
            >
              {{ twoFactorBusy ? 'Generating…' : 'Enable Two-Factor Authentication' }}
            </button>
          </div>

          <!-- Enabling: show secret + code -->
          <div v-else-if="!twoFactorEnabled && show2faSetup" class="space-y-3">
            <p class="text-sm text-gray-600">
              Add this account to your authenticator app using the key below (or scan the URI):
            </p>
            <div class="bg-gray-50 rounded p-3 font-mono text-xs break-all select-all border border-gray-200">
              {{ twoFactorOtpauthUri }}
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 mb-1">Manual key</p>
              <div class="font-mono text-sm select-all bg-gray-50 rounded p-2 border border-gray-200 break-all">
                {{ twoFactorSecret }}
              </div>
            </div>
            <div class="flex flex-wrap items-center gap-2 pt-2">
              <input
                v-model="twoFactorCode"
                inputmode="numeric"
                maxlength="6"
                placeholder="6-digit code"
                class="w-40 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
              <button
                @click="confirm2faEnable"
                :disabled="twoFactorBusy"
                class="bg-emerald-600 text-white px-5 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50"
              >
                {{ twoFactorBusy ? 'Verifying…' : 'Verify & Enable' }}
              </button>
              <button
                @click="cancel2fa"
                class="text-sm text-gray-500 hover:text-gray-700 px-2"
              >
                Cancel
              </button>
            </div>
            <div v-if="twoFactorError" class="text-sm text-red-600">{{ twoFactorError }}</div>
          </div>

          <!-- Enabled: disable flow -->
          <div v-else class="flex flex-wrap items-center justify-between gap-3">
            <span class="inline-flex items-center gap-2 text-sm font-medium text-green-700">
              <i class="fas fa-shield-halved" /> Enabled
            </span>
            <button
              v-if="!show2faDisable"
              @click="show2faDisable = true; twoFactorError = ''"
              class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors text-sm"
            >
              Disable Two-Factor Authentication
            </button>
            <div v-else class="flex flex-wrap items-center gap-2">
              <input
                v-model="twoFactorCode"
                inputmode="numeric"
                maxlength="6"
                placeholder="6-digit code"
                class="w-40 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
              <button
                @click="confirm2faDisable"
                :disabled="twoFactorBusy"
                class="bg-red-600 text-white px-5 py-2 rounded-lg hover:bg-red-700 transition-colors font-medium disabled:opacity-50"
              >
                {{ twoFactorBusy ? 'Verifying…' : 'Disable' }}
              </button>
              <button
                @click="cancel2fa"
                class="text-sm text-gray-500 hover:text-gray-700 px-2"
              >
                Cancel
              </button>
              <div v-if="twoFactorError" class="text-sm text-red-600 w-full">{{ twoFactorError }}</div>
            </div>
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
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">New Password</label>
              <input
                v-model="passwordForm.newPassword"
                type="password"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
              <input
                v-model="passwordForm.confirmPassword"
                type="password"
                class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <button
              @click="changePassword"
              :disabled="changingPassword"
              class="bg-emerald-600 text-white px-6 py-2 rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50"
            >
              {{ changingPassword ? 'Changing…' : 'Change Password' }}
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>