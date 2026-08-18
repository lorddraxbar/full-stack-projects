<script setup lang="ts">
import { ref } from 'vue'

const announcements = ref([
  { id: 1, title: 'Q3 Manufacturing Process Updates', body: 'We have completed the initial assessment of the manufacturing processes. Key findings include a 15% efficiency improvement opportunity in the assembly line.', category: 'PROJECT_UPDATE', audience: 'PROJECT', projectId: 1, publishDate: '2026-08-14', author: 'John Doe', read: false },
  { id: 2, title: 'New Compliance Requirements for Energy Sector', body: 'Please be aware of the updated regulatory requirements that will take effect in Q4 2026. All projects must comply with the new standards.', category: 'COMPANY_NEWS', audience: 'COMPANY', projectId: null, publishDate: '2026-08-12', author: 'Admin', read: true },
  { id: 3, title: 'Scheduled Maintenance - August 20', body: 'The client portal will undergo scheduled maintenance on August 20 from 2:00 AM to 6:00 AM EST. Service may be intermittent during this period.', category: 'MAINTENANCE', audience: 'COMPANY', projectId: null, publishDate: '2026-08-10', author: 'System Admin', read: true },
  { id: 4, title: 'Supply Chain Study Final Report Available', body: 'The final feasibility study report has been completed and is now available in the Documents section. Please review before the client meeting.', category: 'PROJECT_UPDATE', audience: 'PROJECT', projectId: 3, publishDate: '2026-08-01', author: 'Bob Wilson', read: false },
])

const categoryColors: Record<string, string> = {
  'PROJECT_UPDATE': 'bg-blue-100 text-blue-800',
  'COMPANY_NEWS': 'bg-purple-100 text-purple-800',
  'MAINTENANCE': 'bg-orange-100 text-orange-800',
}

const markAsRead = (id: number) => {
  const announcement = announcements.value.find(a => a.id === id)
  if (announcement) {
    announcement.read = true
  }
}
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Announcements</h1>
      <p class="text-gray-600 mt-1">Project and company-wide announcements</p>
    </div>

    <!-- Announcements List -->
    <div class="space-y-6">
      <div
        v-for="announcement in announcements"
        :key="announcement.id"
        :class="[
          'bg-white rounded-lg shadow p-6 transition-all',
          !announcement.read ? 'border-l-4 border-blue-600' : ''
        ]"
      >
        <div class="flex items-start justify-between mb-3">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-2">
              <h3 class="font-semibold text-gray-900">{{ announcement.title }}</h3>
              <span :class="['px-2 py-0.5 text-xs font-medium rounded-full', categoryColors[announcement.category]]">
                {{ announcement.category.replace('_', ' ') }}
              </span>
              <span v-if="!announcement.read" class="w-2 h-2 bg-blue-600 rounded-full" />
            </div>
            <p class="text-gray-700 text-sm">{{ announcement.body }}</p>
          </div>
          <button
            v-if="!announcement.read"
            @click="markAsRead(announcement.id)"
            class="ml-4 px-3 py-1 text-sm text-blue-600 hover:text-blue-700 font-medium"
          >
            Mark as read
          </button>
        </div>

        <div class="flex items-center justify-between text-sm text-gray-500 pt-3 border-t border-gray-100">
          <div class="flex items-center gap-4">
            <span>By: {{ announcement.author }}</span>
            <span>Published: {{ announcement.publishDate }}</span>
            <span v-if="announcement.audience === 'PROJECT'" class="text-blue-600">
              Project: {{ announcements.find(a => a.id === announcement.projectId)?.title || 'N/A' }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="announcements.length === 0" class="bg-white rounded-lg shadow p-12 text-center">
      <p class="text-gray-600">No announcements available.</p>
    </div>
  </div>
</template>
