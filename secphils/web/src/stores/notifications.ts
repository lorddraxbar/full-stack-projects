import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useNotificationsStore = defineStore('notifications', () => {
  const notifications = ref([
    { id: 1, type: 'PROJECT_CREATED', message: 'New project "Energy Audit" assigned to you', read: false, timestamp: '2026-08-15 10:30:00' },
    { id: 2, type: 'NEW_MESSAGE', message: 'New message in "Manufacturing Process Optimization"', read: false, timestamp: '2026-08-15 10:25:00' },
    { id: 3, type: 'DOCUMENT_UPLOADED', message: 'New document uploaded to "Supply Chain Feasibility Study"', read: true, timestamp: '2026-08-15 10:15:00' },
  ])

  const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

  function addNotification(notification: Record<string, unknown>) {
    notifications.value.unshift({ id: Date.now(), ...notification, read: false } as typeof notifications.value[0])
  }

  function markAsRead(id: number) {
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.read = true
    }
  }

  function markAllAsRead() {
    notifications.value.forEach(n => {
      n.read = true
    })
  }

  return { notifications, unreadCount, addNotification, markAsRead, markAllAsRead }
})
