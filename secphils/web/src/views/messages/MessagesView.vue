<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import {
  useGetMe, useGetProjects, useGetMessages, useSendMessage,
  useUploadMessage, useDownloadMessage,
  useGetMessageTrash, useRestoreMessage, useDeleteMessagePermanently, useEmptyMessageTrash,
  type MessageTrashRow,
} from '@/services/api'
import { useRole } from '@/composables/useRole'
import DocumentPreviewModal from '@/components/DocumentPreviewModal.vue'
import TrashMessageModal from '@/components/TrashMessageModal.vue'
import Pagination from '@/components/Pagination.vue'
import { formatDateTime, timeAgo, formatFileSize, formatDate } from '@/lib/labels'
import { useRetention } from '@/composables/useRetention'

interface Conversation {
  id: number
  project: string
  lastMessage: string
  lastMessageBy: string
  lastMessageTime: string
  messageCount: number
  lastHasFile: boolean
  /** True when the latest message is an internal (staff-only) one. */
  lastMessageInternal: boolean
}

const me = ref<{ id: number; fullName: string; companyId: number | null } | null>(null)
const { isClient } = useRole()
const { retentionDays } = useRetention()
const isStaff = computed(() => !isClient.value)

// Provider-only message removal (V33): the supported erasure path. Server
// enforces the same gate (staff + own-company-or-admin); the button is UI only.
const trashTarget = ref<{ id: number; preview: string } | null>(null)
function openTrash(msg: any) {
  const preview = (msg.body || '').length > 90 ? msg.body.slice(0, 90) + '…' : (msg.body || '')
  trashTarget.value = { id: msg.id, preview }
}
async function onMessageRemoved() {
  if (selectedId.value != null) await selectConversation(selectedId.value)
  await loadConversations()
  await loadMessageTrash()
}

// ---------- Staff-only message trash pane (V33) ----------
// 'conversations' (default) or 'msgtrash' — the trash tab is staff-only, the
// same shape as the Documents view's trash.
const view = ref<'conversations' | 'msgtrash'>('conversations')
const trashRows = ref<MessageTrashRow[]>([])
const trashLoading = ref(false)
const trashError = ref('')
const passwordModal = ref<{ open: boolean; purpose: 'one' | 'all'; msgId: number | null }>(
  { open: false, purpose: 'all', msgId: null })
const passwordInput = ref('')
const passwordBusy = ref(false)
const passwordError = ref('')

async function loadMessageTrash() {
  trashLoading.value = true
  trashError.value = ''
  try {
    trashRows.value = await useGetMessageTrash()
  } catch (e: any) {
    trashError.value = e?.response?.data?.message || 'Failed to load the message trash'
  } finally {
    trashLoading.value = false
  }
}

async function restoreMsg(id: number) {
  try {
    await useRestoreMessage(id)
    await loadMessageTrash()
    if (selectedId.value != null) await selectConversation(selectedId.value)
    await loadConversations()
  } catch (e: any) {
    trashError.value = e?.response?.data?.message || 'Failed to restore the message'
  }
}

function openPasswordModal(purpose: 'one' | 'all', msgId: number | null) {
  passwordInput.value = ''
  passwordError.value = ''
  passwordModal.value = { open: true, purpose, msgId }
}

async function confirmPassword() {
  if (passwordBusy.value) return
  if (!passwordInput.value) { passwordError.value = 'Password is required.'; return }
  passwordBusy.value = true
  passwordError.value = ''
  try {
    if (passwordModal.value.purpose === 'one' && passwordModal.value.msgId != null) {
      await useDeleteMessagePermanently(passwordModal.value.msgId, passwordInput.value)
    } else {
      await useEmptyMessageTrash(passwordInput.value)
    }
    passwordModal.value = { open: false, purpose: 'all', msgId: null }
    await loadMessageTrash()
  } catch (e: any) {
    passwordError.value = e?.response?.data?.message || 'Failed — password confirmation rejected'
  } finally {
    passwordBusy.value = false
  }
}
const conversations = ref<Conversation[]>([])
const selectedId = ref<number | null>(null)
const messages = ref<any[]>([])
const loading = ref(true)
const loadError = ref('')
const searchQuery = ref('')
const newMessage = ref('')
const page = ref(1)
const pageSize = 20
watch(searchQuery, () => { page.value = 1 })
const sending = ref(false)
const sendError = ref('')
// Safe by default: staff start staff-only. Flip `visibleToClient` to share
// with the client. The client's composer is always client-visible (effectiveInternal).
const sendInternal = ref(true)
const visibleToClient = computed({
  get: () => !isClient.value && !sendInternal.value,
  set: (v: boolean) => { sendInternal.value = !v },
})
// What actually gets sent — a client can never be internal (the backend 403s it).
const effectiveInternal = computed(() => !isClient.value && sendInternal.value)

function isInternal(msg: any): boolean {
  return msg?.visibility === 'INTERNAL'
}

const pendingFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const MAX_UPLOAD_BYTES = 25 * 1024 * 1024 // matches backend maxUploadMb / nginx cap

const selectedConversation = computed(() =>
  conversations.value.find(c => c.id === selectedId.value) || null
)

// Standard search: every space-separated term must appear somewhere in the
// conversation's displayed fields (project, last message, sender, count).
const filteredConversations = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return conversations.value
  const terms = q.split(/\s+/)
  return conversations.value.filter(c => {
    const haystack = [
      c.project,
      c.lastMessage,
      c.lastMessageBy,
      c.messageCount ? String(c.messageCount) : '',
    ].join(' ').toLowerCase()
    return terms.every(t => haystack.includes(t))
  })
})

const paginatedConversations = computed(() =>
  filteredConversations.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)

function initials(name: string): string {
  return name.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase()
}

function isOwn(msg: any): boolean {
  return me.value != null && msg.senderId === me.value.id
}

function onFilePicked(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  if (f.size > MAX_UPLOAD_BYTES) {
    sendError.value = 'File is too large (max 25 MB)'
    return
  }
  sendError.value = ''
  pendingFile.value = f
}

function removePendingFile() {
  pendingFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

async function downloadAttachment(msg: any) {
  try {
    const blob = await useDownloadMessage(msg.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = msg.attachmentFileName || 'attachment'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (err: any) {
    sendError.value = err?.response?.data?.message || 'Failed to download file'
  }
}

// ---------- Attachment preview (shared modal — same surface as Documents) ----------
const previewOpen = ref(false)
const previewDoc = ref<{ kind: 'message'; id: number; title: string; fileName: string } | null>(null)
function previewAttachment(msg: any) {
  previewDoc.value = { kind: 'message', id: msg.id, title: msg.attachmentFileName || 'Attachment', fileName: msg.attachmentFileName || '' }
  previewOpen.value = true
}

// Project row enriched by GET /projects with the per-project message preview
// (latest message body/sender/visibility/file + total count) — see
// ProjectController.list. Lets the inbox build from ONE call instead of one
// /messages?projectId= round-trip per project (the old N+1 was the page's
// main source of slowness).
interface ProjectPreview {
  id: number
  name: string
  latestUpdateBody?: string | null
  latestUpdateAt?: string | null
  latestUpdateSender?: string | null
  latestUpdateVisibility?: string | null
  latestHasFile?: boolean | null
  messageCount?: number | null
}

async function loadConversations() {
  loading.value = true
  loadError.value = ''
  try {
    const meRes = await useGetMe()
    // GET /users/me returns the UserResponse body directly (no envelope)
    me.value = meRes || null
    // Clients see only their own company's projects (their conversations).
    // Staff/admin see every project — the inbox mirrors the dashboard's
    // cross-company read access. Full list: a page-capped fetch would hide
    // a client's older conversations (the 20 newest by created_at only).
    // The response is already enriched with each project's latest message +
    // message count (batched server-side), so no per-project fetch is needed.
    const isClientRole = (meRes as any)?.role === 'CLIENT'
    const page = await useGetProjects({
      companyId: isClientRole ? (me.value?.companyId ?? undefined) : undefined,
      size: 10000,
    })
    const projects = (Array.isArray(page) ? page : (page?.content ?? [])) as ProjectPreview[]

    const convs: Conversation[] = projects.map(p => {
      const lastBody = p.latestUpdateBody
      return {
        id: p.id,
        project: p.name,
        lastMessage: lastBody || 'No messages yet',
        lastMessageBy: p.latestUpdateSender || '',
        lastMessageTime: p.latestUpdateAt ? timeAgo(p.latestUpdateAt) : '',
        messageCount: p.messageCount ?? 0,
        lastHasFile: !!p.latestHasFile,
        lastMessageInternal: p.latestUpdateVisibility === 'INTERNAL',
      }
    })
    conversations.value = convs
    if (convs.length > 0 && selectedId.value == null) {
      await selectConversation(convs[0].id)
    }
  } catch (err: any) {
    loadError.value = err?.response?.data?.message || err?.message || 'Failed to load conversations'
  } finally {
    loading.value = false
  }
}

async function selectConversation(id: number) {
  selectedId.value = id
  sendError.value = ''
  sendInternal.value = true
  pendingFile.value = null
  if (fileInput.value) fileInput.value.value = ''
  try {
    const msgs = await useGetMessages(id)
    messages.value = Array.isArray(msgs) ? msgs : []
  } catch (err: any) {
    loadError.value = err?.response?.data?.message || 'Failed to load messages'
    messages.value = []
  }
}

async function sendMessage() {
  const text = newMessage.value.trim()
  const file = pendingFile.value
  if ((!text && !file) || selectedId.value == null || sending.value) return
  sending.value = true
  sendError.value = ''
  try {
    const internal = effectiveInternal.value
    if (file) {
      await useUploadMessage({ projectId: selectedId.value, body: text || undefined, file, internal })
    } else {
      await useSendMessage(selectedId.value, text, internal)
    }
    newMessage.value = ''
    pendingFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    await selectConversation(selectedId.value)
    await loadConversations()
  } catch (err: any) {
    sendError.value = err?.response?.data?.message || 'Failed to send message'
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  await loadConversations()
  if (isStaff.value) await loadMessageTrash() // keeps the tab count honest
})
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Messages</h1>
      <p class="text-gray-600 mt-1">Project conversations with team members</p>
    </div>

    <!-- Tabs (staff + admin only) — same shape as the Documents view -->
    <div v-if="!isClient" class="flex gap-1 mb-6 border-b border-gray-200">
      <button
        @click="view = 'conversations'"
        :class="['pb-2 px-1 -mb-px text-sm font-medium border-b-2 transition-colors',
          view === 'conversations' ? 'border-emerald-600 text-emerald-700' : 'border-transparent text-gray-500 hover:text-gray-700']"
      >
        Conversations
      </button>
      <button
        @click="view = 'msgtrash'; loadMessageTrash()"
        :class="['pb-2 px-1 -mb-px text-sm font-medium border-b-2 transition-colors',
          view === 'msgtrash' ? 'border-emerald-600 text-emerald-700' : 'border-transparent text-gray-500 hover:text-gray-700']"
      >
        Message trash ({{ trashRows.length }})
      </button>
    </div>

    <!-- Message trash pane (provider staff only — the supported erasure path) -->
    <div v-if="view === 'msgtrash'" class="bg-white rounded-lg shadow overflow-hidden">
      <div class="p-4 border-b border-gray-200 flex items-center justify-between gap-4">
        <div>
          <h2 class="font-semibold text-gray-900">Removed messages</h2>
          <p class="text-sm text-gray-500 mt-0.5">
            Purged automatically {{ retentionDays }} days after removal. Everything here is restorable until then,
            and every step is recorded in the audit log.
          </p>
        </div>
        <button
          v-if="trashRows.length > 0"
          class="bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 transition-colors font-medium text-sm whitespace-nowrap"
          @click="openPasswordModal('all', null)"
        >
          <i class="fas fa-trash mr-1" />Empty trash
        </button>
      </div>
      <p v-if="trashError" class="mx-6 mt-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{{ trashError }}</p>
      <div v-if="trashLoading" class="p-10 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="trashRows.length === 0" class="p-10 text-center text-sm text-gray-500">
        No removed messages. Nothing is recoverable.
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Message</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Project</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Sender</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Removed</th>
              <th class="px-6 py-3"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr v-for="row in trashRows" :key="row.id" class="hover:bg-gray-50 align-top">
              <td class="px-6 py-4 text-sm text-gray-800 max-w-md">
                {{ row.body.length > 120 ? row.body.slice(0, 120) + '…' : row.body }}
                <span v-if="row.attachmentFileName" class="block text-xs text-gray-500 mt-1">
                  <i class="fas fa-paperclip mr-1"></i>{{ row.attachmentFileName }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-600">{{ row.projectName }}</td>
              <td class="px-6 py-4 text-sm text-gray-600 whitespace-nowrap">{{ row.senderName || '—' }}</td>
              <td class="px-6 py-4 text-sm text-gray-600 whitespace-nowrap">
                {{ formatDate(row.deletedAt) }}
                <span class="block text-xs text-gray-400">by {{ row.deletedByName || '—' }}</span>
              </td>
              <td class="px-6 py-4 text-right whitespace-nowrap">
                <button
                  class="text-emerald-600 hover:text-emerald-700 font-medium text-sm mr-3"
                  @click="restoreMsg(row.id)"
                >
                  Restore
                </button>
                <button
                  class="text-red-600 hover:text-red-700 font-medium text-sm"
                  @click="openPasswordModal('one', row.id)"
                >
                  Delete permanently
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="loading" class="flex items-center justify-center py-20">
      <svg class="animate-spin h-8 w-8 text-emerald-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
    </div>

    <div v-else-if="loadError" class="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
      {{ loadError }}
    </div>

    <div v-else-if="conversations.length === 0" class="bg-white rounded-lg shadow p-10 text-center text-gray-500">
      You are not part of any projects yet. Once a project is assigned to you, conversations will appear here.
    </div>

    <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-6 h-[calc(100vh-12rem)]">
      <!-- Conversations List -->
      <div class="bg-white rounded-lg shadow overflow-hidden flex flex-col">
        <div class="p-4 border-b border-gray-200">
          <h2 class="font-semibold text-gray-900 mb-3">Conversations</h2>
          <div class="relative">
            <i class="fas fa-magnifying-glass absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none"></i>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Search conversations — project, message, sender"
              class="w-full pl-9 pr-9 py-2 rounded-lg border border-gray-300 bg-white text-sm text-gray-700 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 focus:outline-none"
            />
            <button
              v-if="searchQuery"
              @click="searchQuery = ''"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              aria-label="Clear search"
            >
              <i class="fas fa-xmark text-sm"></i>
            </button>
          </div>
        </div>
        <div class="divide-y divide-gray-200 overflow-y-auto flex-1">
          <div
            v-if="filteredConversations.length === 0 && searchQuery"
            class="p-4 text-sm text-gray-500"
          >
            No conversations match "{{ searchQuery }}".
          </div>
          <div
            v-for="conv in paginatedConversations"
            :key="conv.id"
            @click="selectConversation(conv.id)"
            :class="[
              'p-4 cursor-pointer hover:bg-gray-50 transition-colors',
              selectedId === conv.id ? 'bg-emerald-50 border-l-4 border-emerald-600' : ''
            ]"
          >
            <div class="flex items-start justify-between mb-1">
              <h3 class="font-medium text-gray-900 text-sm">{{ conv.project }}</h3>
              <span class="text-xs text-gray-500 whitespace-nowrap ml-2">{{ conv.lastMessageTime }}</span>
            </div>
            <p class="text-sm text-gray-600 truncate">
              <i v-if="conv.lastHasFile" class="fas fa-paperclip mr-1 text-emerald-600"></i>
              <i v-if="conv.lastMessageInternal" class="fas fa-lock mr-1 text-slate-500" title="Last message is internal (staff only)"></i>
              {{ conv.lastMessage }}
            </p>
            <div class="flex items-center justify-between mt-2">
              <span class="text-xs text-gray-500">{{ conv.lastMessageBy }}</span>
              <span v-if="conv.messageCount > 0" class="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded-full">
                {{ conv.messageCount }}
              </span>
            </div>
          </div>
        </div>
        <Pagination v-model:page="page" :total="filteredConversations.length" :page-size="pageSize" />
      </div>

      <!-- Messages Area -->
      <div class="lg:col-span-2 bg-white rounded-lg shadow flex flex-col">
        <!-- Message Header -->
        <div class="p-4 border-b border-gray-200">
          <h2 class="font-semibold text-gray-900">{{ selectedConversation?.project }}</h2>
        </div>

        <!-- Messages -->
        <div class="flex-1 overflow-y-auto p-4 space-y-4">
          <div v-if="messages.length === 0" class="text-sm text-gray-500">
            No messages in this conversation yet.
          </div>
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="[
              'flex gap-3',
              isOwn(msg) ? 'flex-row-reverse' : ''
            ]"
          >
            <div class="w-8 h-8 rounded-full bg-emerald-600 flex items-center justify-center text-white text-xs font-medium flex-shrink-0">
              {{ initials(msg.senderName || '?') }}
            </div>
            <div :class="[
              'max-w-[70%] rounded-lg p-3',
              isInternal(msg)
                ? (isOwn(msg) ? 'bg-slate-700 text-white ring-1 ring-dashed ring-slate-400' : 'bg-slate-100 text-gray-900 ring-1 ring-dashed ring-slate-400')
                : (isOwn(msg) ? 'bg-emerald-600 text-white' : 'bg-gray-100 text-gray-900')
            ]">
              <p class="text-sm font-medium mb-0.5 flex items-center gap-1.5">
                {{ msg.senderName }}
                <span
                  v-if="isInternal(msg)"
                  :class="[
                    'inline-flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide px-1.5 py-0.5 rounded border',
                    isOwn(msg) ? 'bg-white/20 text-white border-white/40' : 'bg-slate-200 text-slate-700 border-slate-400/60',
                  ]"
                  title="Internal — only visible to provider staff, not the client"
                >
                  <i class="fas fa-lock"></i> Internal
                </span>
              </p>
              <p class="text-sm">
                {{ msg.body }}
                <span v-if="msg.attachmentFileName" class="text-emerald-300">
                  <i class="fas fa-paperclip"></i>
                </span>
              </p>
              <div
                v-if="msg.attachmentFileName"
                class="mt-2 flex items-center gap-2 px-3 py-2 rounded-lg text-sm"
                :class="isOwn(msg) ? 'bg-emerald-700/60 text-emerald-50' : 'bg-white border border-gray-200 text-gray-800'"
              >
                <i class="fas fa-file text-base"></i>
                <span class="flex-1 truncate">
                  {{ msg.attachmentFileName }}
                  <span class="opacity-70 text-xs">({{ formatFileSize(msg.attachmentFileSize) }})</span>
                </span>
                <button
                  type="button"
                  @click="previewAttachment(msg)"
                  class="text-xs font-medium underline"
                  :class="isOwn(msg) ? 'text-emerald-200 hover:text-white' : 'text-emerald-700 hover:text-emerald-900'"
                >
                  Preview
                </button>
                <button
                  type="button"
                  @click="downloadAttachment(msg)"
                  class="text-xs font-medium underline"
                  :class="isOwn(msg) ? 'text-emerald-200 hover:text-white' : 'text-emerald-700 hover:text-emerald-900'"
                >
                  Download
                </button>
              </div>
              <p :class="[
                'text-xs mt-1',
                isOwn(msg) ? 'text-emerald-100' : 'text-gray-500'
              ]">
                {{ formatDateTime(msg.createdAt) }}
                <button
                  v-if="isStaff"
                  type="button"
                  title="Remove message (SECPhils staff — erasure path)"
                  @click="openTrash(msg)"
                  class="ml-2 opacity-60 hover:opacity-100 underline"
                  :class="isOwn(msg) ? 'text-emerald-200' : 'text-red-600'"
                >
                  Remove
                </button>
              </p>
            </div>
          </div>
        </div>

        <!-- Message Input -->
        <div class="p-4 border-t border-gray-200">
          <div v-if="pendingFile" class="mb-2 flex items-center gap-2">
            <span class="inline-flex items-center gap-2 px-3 py-1.5 bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm rounded-lg">
              <i class="fas fa-paperclip text-emerald-600"></i>
              <span class="max-w-[220px] truncate">{{ pendingFile.name }}</span>
              <span class="text-xs text-emerald-600">{{ formatFileSize(pendingFile.size) }}</span>
              <button
                type="button"
                @click="removePendingFile"
                class="ml-1 text-emerald-500 hover:text-emerald-700"
                title="Remove file"
              >
                <i class="fas fa-times"></i>
              </button>
            </span>
          </div>
          <div
            class="flex flex-col gap-3 rounded-lg border p-3"
            :class="effectiveInternal ? 'border-slate-300 bg-slate-50' : 'border-emerald-300 bg-emerald-50/40'"
          >
            <!-- Audience banner: sender-only — tells staff who will read this message. Clients have no audience choice. -->
            <div
              v-if="!isClient"
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
              v-model="newMessage"
              @keyup.enter.exact="sendMessage"
              rows="2"
              :placeholder="effectiveInternal ? 'Type an internal staff note…' : 'Type a message…'"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent text-sm bg-white"
            ></textarea>

            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  @click="fileInput?.click()"
                  class="inline-flex items-center gap-1.5 px-3 py-2 border border-gray-300 rounded-lg text-gray-500 hover:bg-white hover:text-emerald-600 transition-colors text-sm"
                  title="Attach a file"
                >
                  <i class="fas fa-paperclip"></i>
                  <span class="hidden sm:inline">Attach</span>
                </button>
                <input ref="fileInput" type="file" class="hidden" @change="onFilePicked" />
                <p v-if="sendError" class="text-xs text-red-600">{{ sendError }}</p>
              </div>
              <button
                @click="sendMessage"
                :disabled="sending || (!newMessage.trim() && !pendingFile)"
                class="inline-flex items-center gap-2 px-5 py-2 rounded-lg text-white font-medium transition-colors disabled:opacity-50"
                :class="effectiveInternal ? 'bg-slate-700 hover:bg-slate-800' : 'bg-emerald-600 hover:bg-emerald-700'"
              >
                <i :class="effectiveInternal ? 'fas fa-lock' : 'fas fa-paper-plane'"></i>
                {{ sending ? 'Sending…' : (isClient ? 'Send message' : (effectiveInternal ? 'Send to staff' : 'Send to client')) }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Attachment preview (shared modal with the Documents surfaces) -->
    <DocumentPreviewModal v-model:open="previewOpen" :doc="previewDoc" />

    <!-- Staff-only message removal (erasure path, V33) -->
    <TrashMessageModal
      :open="trashTarget !== null"
      :msg="trashTarget"
      @update:open="(v: boolean) => { if (!v) trashTarget = null }"
      @removed="onMessageRemoved"
    />

    <!-- Password confirmation (shared by "delete permanently" and "empty trash") -->
    <div v-if="passwordModal.open" class="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div class="absolute inset-0 bg-black/30" @click="passwordModal = { open: false, purpose: 'all', msgId: null }" />
      <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-2">Confirm with your password</h2>
        <p class="text-sm text-gray-600 mb-4">
          {{ passwordModal.purpose === 'one'
            ? 'This permanently deletes the message row and its attachment (when no document mirrors it). This cannot be undone.'
            : `This permanently deletes all ${trashRows.length} message(s) in the trash. This cannot be undone.` }}
        </p>
        <input
          v-model="passwordInput"
          type="password"
          placeholder="Your account password"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
          @keyup.enter="confirmPassword"
        />
        <p v-if="passwordError" class="mt-2 text-sm text-red-600">{{ passwordError }}</p>
        <div class="flex justify-end gap-3 mt-5">
          <button
            class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors text-sm font-medium"
            @click="passwordModal = { open: false, purpose: 'all', msgId: null }"
          >
            Cancel
          </button>
          <button
            class="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors text-sm font-medium disabled:opacity-50"
            :disabled="passwordBusy"
            @click="confirmPassword"
          >
            {{ passwordBusy ? 'Deleting…' : 'Delete permanently' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
