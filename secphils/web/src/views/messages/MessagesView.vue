<script setup lang="ts">
import { ref } from 'vue'

const conversations = ref([
  { id: 1, project: 'Manufacturing Process Optimization', lastMessage: 'Please review the updated process flow diagrams', lastMessageBy: 'John Doe', lastMessageTime: '10:30 AM', unread: 3 },
  { id: 2, project: 'Energy Sector Compliance Audit', lastMessage: 'Compliance documentation has been submitted', lastMessageBy: 'Jane Smith', lastMessageTime: 'Yesterday', unread: 0 },
  { id: 3, project: 'Supply Chain Feasibility Study', lastMessage: 'Final report approved by client', lastMessageBy: 'Bob Wilson', lastMessageTime: 'Aug 12', unread: 1 },
])

const messages = ref([
  { id: 1, sender: 'John Doe', senderAvatar: 'JD', content: 'Hi team, I have updated the process flow diagrams. Please review and provide feedback.', timestamp: '10:15 AM', isOwn: false },
  { id: 2, sender: 'Jane Smith', senderAvatar: 'JS', content: 'Thanks John. I will review them this afternoon.', timestamp: '10:20 AM', isOwn: false },
  { id: 3, sender: 'You', senderAvatar: 'YO', content: 'Great, let me know if you need any clarification on the methodology.', timestamp: '10:25 AM', isOwn: true },
  { id: 4, sender: 'John Doe', senderAvatar: 'JD', content: 'Please review the updated process flow diagrams', timestamp: '10:30 AM', isOwn: false },
])

const newMessage = ref('')
const selectedConversation = ref(conversations.value[0])

const sendMessage = () => {
  if (newMessage.value.trim()) {
    messages.value.push({
      id: messages.value.length + 1,
      sender: 'You',
      senderAvatar: 'YO',
      content: newMessage.value,
      timestamp: new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true }),
      isOwn: true,
    })
    newMessage.value = ''
  }
}
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Messages</h1>
      <p class="text-gray-600 mt-1">Project conversations with team members</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 h-[calc(100vh-12rem)]">
      <!-- Conversations List -->
      <div class="bg-white rounded-lg shadow overflow-hidden">
        <div class="p-4 border-b border-gray-200">
          <h2 class="font-semibold text-gray-900">Conversations</h2>
        </div>
        <div class="divide-y divide-gray-200 overflow-y-auto">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            @click="selectedConversation = conv"
            :class="[
              'p-4 cursor-pointer hover:bg-gray-50 transition-colors',
              selectedConversation?.id === conv.id ? 'bg-blue-50 border-l-4 border-blue-600' : ''
            ]"
          >
            <div class="flex items-start justify-between mb-1">
              <h3 class="font-medium text-gray-900 text-sm">{{ conv.project }}</h3>
              <span class="text-xs text-gray-500">{{ conv.lastMessageTime }}</span>
            </div>
            <p class="text-sm text-gray-600 truncate">{{ conv.lastMessage }}</p>
            <div class="flex items-center justify-between mt-2">
              <span class="text-xs text-gray-500">{{ conv.lastMessageBy }}</span>
              <span
                v-if="conv.unread > 0"
                class="px-2 py-0.5 bg-blue-600 text-white text-xs rounded-full"
              >
                {{ conv.unread }}
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
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="[
              'flex gap-3',
              msg.isOwn ? 'flex-row-reverse' : ''
            ]"
          >
            <div class="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-medium flex-shrink-0">
              {{ msg.senderAvatar }}
            </div>
            <div :class="[
              'max-w-[70%] rounded-lg p-3',
              msg.isOwn ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-900'
            ]">
              <p class="text-sm">{{ msg.content }}</p>
              <p :class="[
                'text-xs mt-1',
                msg.isOwn ? 'text-blue-100' : 'text-gray-500'
              ]">
                {{ msg.timestamp }}
              </p>
            </div>
          </div>
        </div>

        <!-- Message Input -->
        <div class="p-4 border-t border-gray-200">
          <div class="flex gap-2">
            <input
              v-model="newMessage"
              @keyup.enter="sendMessage"
              type="text"
              placeholder="Type a message..."
              class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <button
              @click="sendMessage"
              class="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
            >
              Send
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
