import { useQuery } from '@tanstack/react-query'
import { notificationApi } from '../api/notifications'
import { useNotificationStore } from '../stores/notificationStore'
import { useEffect } from 'react'

export function useNotifications() {
  const setUnreadCount = useNotificationStore((s) => s.setUnreadCount)
  const localReadIds = useNotificationStore((s) => s.localReadIds)

  const query = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationApi.getAll().then((r) => r.data),
    refetchInterval: 30_000,
  })

  useEffect(() => {
    if (query.data) {
      const unread = query.data.filter((n) => !n.read && !localReadIds.has(n.id)).length
      setUnreadCount(unread)
    }
  }, [query.data, localReadIds, setUnreadCount])

  return query
}
