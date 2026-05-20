import { useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { useAuthStore } from '../../stores/authStore'
import { useNotificationStore } from '../../stores/notificationStore'
import { useNotifications } from '../../hooks/useNotifications'
import { Badge } from '../ui/Badge'
import { Spinner } from '../ui/Spinner'

export function TopNav() {
  const navigate = useNavigate()
  const logout = useAuthStore((s) => s.logout)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const markRead = useNotificationStore((s) => s.markRead)
  const localReadIds = useNotificationStore((s) => s.localReadIds)

  const [notifOpen, setNotifOpen] = useState(false)
  const [userOpen, setUserOpen] = useState(false)

  const { data: notifications, isLoading: notifsLoading } = useNotifications()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="flex items-center justify-between h-14 px-4 bg-zinc-900/80 backdrop-blur border-b border-zinc-800 z-10 relative">
      <button
        onClick={() => navigate('/search')}
        className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-zinc-800 text-zinc-400 hover:bg-zinc-700 hover:text-zinc-100 text-sm transition-colors"
      >
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        Search
      </button>

      <div className="flex items-center gap-3">
        {/* Notification bell */}
        <div className="relative">
          <button
            onClick={() => { setNotifOpen((o) => !o); setUserOpen(false) }}
            className="relative p-2 rounded-full text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800 transition-colors"
            aria-label="Notifications"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
            {unreadCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 flex items-center justify-center w-4 h-4 rounded-full bg-brand-500 text-white text-[10px] font-bold">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          {notifOpen && (
            <div className="absolute right-0 top-full mt-2 w-80 rounded-xl bg-zinc-900 border border-zinc-700 shadow-2xl overflow-hidden z-50">
              <div className="px-4 py-3 border-b border-zinc-800 flex items-center justify-between">
                <span className="text-sm font-semibold text-zinc-100">Notifications</span>
                <button onClick={() => setNotifOpen(false)} className="text-zinc-500 hover:text-zinc-300">
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div className="max-h-80 overflow-y-auto">
                {notifsLoading && (
                  <div className="flex justify-center py-6"><Spinner size="sm" /></div>
                )}
                {!notifsLoading && (!notifications || notifications.length === 0) && (
                  <p className="text-sm text-zinc-500 text-center py-6">No notifications</p>
                )}
                {notifications?.map((n) => {
                  const isRead = n.read || localReadIds.has(n.id)
                  return (
                    <button
                      key={n.id}
                      onClick={() => markRead(n.id)}
                      className={[
                        'w-full text-left px-4 py-3 border-b border-zinc-800 hover:bg-zinc-800 transition-colors',
                        isRead ? 'opacity-60' : '',
                      ].join(' ')}
                    >
                      <div className="flex items-start gap-2">
                        {!isRead && <span className="mt-1.5 w-2 h-2 rounded-full bg-brand-500 flex-shrink-0" />}
                        <div className={isRead ? 'ml-4' : ''}>
                          <p className="text-sm font-medium text-zinc-100 line-clamp-1">{n.title}</p>
                          <p className="text-xs text-zinc-400 line-clamp-2 mt-0.5">{n.message}</p>
                          <p className="text-xs text-zinc-600 mt-1">
                            {new Date(n.createdAt).toLocaleString()}
                          </p>
                        </div>
                      </div>
                    </button>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        {/* User menu */}
        <div className="relative">
          <button
            onClick={() => { setUserOpen((o) => !o); setNotifOpen(false) }}
            className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-zinc-800 text-zinc-300 hover:bg-zinc-700 hover:text-zinc-100 text-sm transition-colors"
          >
            <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd" />
            </svg>
            Account
          </button>

          {userOpen && (
            <div className="absolute right-0 top-full mt-2 w-40 rounded-xl bg-zinc-900 border border-zinc-700 shadow-2xl overflow-hidden z-50">
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-2 px-4 py-3 text-sm text-red-400 hover:bg-zinc-800 transition-colors"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                </svg>
                Log out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
