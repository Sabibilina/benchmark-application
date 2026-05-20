import { NavLink } from 'react-router-dom'
import { usePlaylists } from '../../hooks/usePlaylists'
import { Spinner } from '../ui/Spinner'

interface NavItem {
  to: string
  label: string
  icon: JSX.Element
}

const navItems: NavItem[] = [
  {
    to: '/',
    label: 'Home',
    icon: (
      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
        <path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7A1 1 0 003 11h1v6a1 1 0 001 1h4v-4h2v4h4a1 1 0 001-1v-6h1a1 1 0 00.707-1.707l-7-7z" />
      </svg>
    ),
  },
  {
    to: '/search',
    label: 'Search',
    icon: (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
      </svg>
    ),
  },
  {
    to: '/catalog',
    label: 'Catalog',
    icon: (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
      </svg>
    ),
  },
  {
    to: '/history',
    label: 'History',
    icon: (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  {
    to: '/playlists',
    label: 'Playlists',
    icon: (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 10h16M4 14h10" />
      </svg>
    ),
  },
]

export function Sidebar() {
  const { data: playlists, isLoading } = usePlaylists()

  return (
    <aside className="flex flex-col w-60 min-h-0 bg-zinc-900 border-r border-zinc-800">
      <div className="p-4 border-b border-zinc-800">
        <span className="text-brand-400 font-bold text-lg tracking-tight">MusicStream</span>
      </div>

      <nav className="flex flex-col gap-1 p-2">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              [
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-brand-600 text-white'
                  : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100',
              ].join(' ')
            }
          >
            {item.icon}
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="flex-1 min-h-0 overflow-y-auto p-2 border-t border-zinc-800 mt-2">
        <p className="px-3 py-1 text-xs font-semibold text-zinc-500 uppercase tracking-wider">
          Your Playlists
        </p>
        {isLoading && (
          <div className="flex justify-center mt-4">
            <Spinner size="sm" />
          </div>
        )}
        {playlists?.map((pl) => (
          <NavLink
            key={pl.id}
            to={`/playlists/${pl.id}`}
            className={({ isActive }) =>
              [
                'flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm transition-colors',
                isActive
                  ? 'bg-zinc-700 text-zinc-100'
                  : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100',
              ].join(' ')
            }
          >
            {pl.likedSongs ? (
              <span className="text-brand-400">♥</span>
            ) : (
              <span className="text-zinc-600">♪</span>
            )}
            <span className="line-clamp-1">{pl.name}</span>
          </NavLink>
        ))}
      </div>
    </aside>
  )
}
