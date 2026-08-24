<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  useGetMe, useGetProjects, useGetMessages, useSendMessage,
  useUploadMessage, useDownloadMessage,
} from '@/services/api'
import { formatDateTime, timeAgo, formatFileSize } from '@/lib/labels'

interface Conversation {
  id: number
  project: string
  lastMessage: string
  lastMessageBy: string
  lastMessageTime: string
  messageCount: number
  lastHasFile: boolean
}

const me = ref<{ id: number; fullName: string } | null>(null)
const conversations = ref<Conversation[]>([])
const selectedId = ref<number | null>(null)
const messages = ref<any[]>([])
const loading = ref(true)
const loadError = ref('')
const newMessage = ref('')
const sending = ref(false)
const sendError = ref('')

const pendingFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const MAX_UPLOAD_BYTES = 25 * 1024 * 1024 // matches backend maxUploadMb / nginx cap

const selectedConversation = computed(() =>
  conversations.value.find(c => c.id === selectedId.value) || null
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

async function loadConversations() {
  loading.value = true
  loadError.value = ''
  try {
    const meRes = await useGetMe()
    me.value = meRes.user ?? null

    const page = await useGetProjects()
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
    if (file) {
      await useUploadMessage({ projectId: selectedId.value, body: text || undefined, file })
    } else {
      await useSendMessage(selectedId.value, text)
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
          <h2 class="font-semibold text-gray-900">Conversations</h2>
        </div>
        <div class="divide-y divide-gray-200 overflow-y-auto flex-1">
          <div
            v-for="conv in conversations"
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
              isOwn(msg) ? 'bg-emerald-600 text-white' : 'bg-gray-100 text-gray-900'
            ]">
              <p class="text-sm font-medium mb-0.5">{{ msg.senderName }}</p>
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
          <p v-if="sendError" class="text-xs text-red-600 mb-2">{{ sendError }}</p>
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
          <div class="flex gap-2">
            <button
              type="button"
              @click="fileInput?.click()"
              class="px-3 py-2 border border-gray-300 rounded-lg text-gray-500 hover:bg-gray-50 hover:text-emerald-600 transition-colors"
              title="Attach a file"
            >
              <i class="fas fa-paperclip"></i>
            </button>
            <input
              ref="fileInput"
              type="file"
              class="hidden"
              @change="onFilePicked"
            />
            <input
              v-model="newMessage"
              @keyup.enter="sendMessage"
              type="text"
              placeholder="Type a message..."
              class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
            />
            <button
              @click="sendMessage"
              :disabled="sending"
              class="px-6 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors font-medium disabled:opacity-50"
            >
              Send
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
