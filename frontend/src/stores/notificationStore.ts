import { create } from 'zustand'

interface NotificationState {
  unreadCount: number
  setUnreadCount: (n: number) => void
  markRead: (id: string) => void
  localReadIds: Set<string>
}

export const useNotificationStore = create<NotificationState>((set) => ({
  unreadCount: 0,
  localReadIds: new Set(),

  setUnreadCount(n) {
    set({ unreadCount: n })
  },

  markRead(id) {
    set((s) => {
      const next = new Set(s.localReadIds)
      next.add(id)
      return { localReadIds: next, unreadCount: Math.max(0, s.unreadCount - 1) }
    })
  },
}))
