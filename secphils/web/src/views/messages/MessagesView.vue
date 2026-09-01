<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  useGetMe, useGetProjects, useGetMessages, useSendMessage,
  useUploadMessage, useDownloadMessage,
} from '@/services/api'
import { useRole } from '@/composables/useRole'
import { formatDateTime, timeAgo, formatFileSize } from '@/lib/labels'

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
const conversations = ref<Conversation[]>([])
const selectedId = ref<number | null>(null)
const messages = ref<any[]>([])
const loading = ref(true)
const loadError = ref('')
const searchQuery = ref('')
const newMessage = ref('')
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

async function loadConversations() {
  loading.value = true
  loadError.value = ''
  try {
    const meRes = await useGetMe()
    // GET /users/me returns the UserResponse body directly (no envelope)
    me.value = meRes || null
    // Clients see only their own company's projects (their conversations)
    const page = await useGetProjects({
      companyId: me.value?.companyId ?? undefined,
    })
    const projects = Array.isArray(page) ? page : (page?.content ?? [])

    const convs: Conversation[] = []
    for (const p of projects) {
      try {
        const msgs = await useGetMessages(p.id)
        const list = Array.isArray(msgs) ? msgs : []
        const last = list[list.length - 1]
        convs.push({
          id: p.id,
          project: p.name,
          lastMessage: last ? last.body : 'No messages yet',
          lastMessageBy: last ? (last.senderName || '—') : '',
          lastMessageTime: last ? timeAgo(last.createdAt) : '',
          messageCount: list.length,
          lastHasFile: !!(last && last.attachmentFileName),
          lastMessageInternal: !!(last && isInternal(last)),
        })
      } catch {
        convs.push({
          id: p.id,
          project: p.name,
          lastMessage: 'No messages yet',
          lastMessageBy: '',
          lastMessageTime: '',
          messageCount: 0,
          lastHasFile: false,
          lastMessageInternal: false,
        })
      }
    }
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

onMounted(loadConversations)
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Messages</h1>
      <p class="text-gray-600 mt-1">Project conversations with team members</p>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20">
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
            v-for="conv in filteredConversations"
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
            <!-- Audience banner: the single place you see who will see your message. -->
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
                {{ sending ? 'Sending…' : (effectiveInternal ? 'Send to staff' : 'Send to client') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
