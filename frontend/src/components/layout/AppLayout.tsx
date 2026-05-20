import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { TopNav } from './TopNav'
import { PlayerBar } from './PlayerBar'

export function AppLayout() {
  return (
    <div className="flex flex-col h-screen overflow-hidden">
      <div className="flex flex-1 min-h-0">
        <Sidebar />
        <div className="flex flex-col flex-1 min-w-0">
          <TopNav />
          <main className="flex-1 overflow-y-auto bg-zinc-950 p-6">
            <Outlet />
          </main>
        </div>
      </div>
      <PlayerBar />
    </div>
  )
}
