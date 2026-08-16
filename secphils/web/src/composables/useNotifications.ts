import { useNotificationsStore } from '../stores/notifications'

export function useNotifications() {
  const store = useNotificationsStore()

  function addNotification(type: string, message: string) {
    store.addNotification({ type, message })
  }

  function markAsRead(id: number) {
    store.markAsRead(id)
  }

  function markAllAsRead() {
    store.markAllAsRead()
  }

  return {
    notifications: store.notifications,
    unreadCount: store.unreadCount,
    addNotification,
    markAsRead,
    markAllAsRead,
  }
}
