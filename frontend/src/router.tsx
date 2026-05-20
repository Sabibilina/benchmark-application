import { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from './stores/authStore'
import { AppLayout } from './components/layout/AppLayout'
import { Spinner } from './components/ui/Spinner'

const HomeView       = lazy(() => import('./views/HomeView'))
const SearchView     = lazy(() => import('./views/SearchView'))
const CatalogView    = lazy(() => import('./views/CatalogView'))
const PlaylistsView  = lazy(() => import('./views/PlaylistsView'))
const PlaylistDetail = lazy(() => import('./views/PlaylistDetail'))
const HistoryView    = lazy(() => import('./views/HistoryView'))
const LoginView      = lazy(() => import('./views/LoginView'))
const RegisterView   = lazy(() => import('./views/RegisterView'))

function ProtectedRoute() {
  const token = useAuthStore((s) => s.token)
  if (!token) return <Navigate to="/login" replace />
  return <Outlet />
}

function Loading() {
  return (
    <div className="flex items-center justify-center h-64">
      <Spinner size="lg" />
    </div>
  )
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <Suspense fallback={<Loading />}>
        <LoginView />
      </Suspense>
    ),
  },
  {
    path: '/register',
    element: (
      <Suspense fallback={<Loading />}>
        <RegisterView />
      </Suspense>
    ),
  },
  {
    path: '/',
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          {
            index: true,
            element: <Suspense fallback={<Loading />}><HomeView /></Suspense>,
          },
          {
            path: 'search',
            element: <Suspense fallback={<Loading />}><SearchView /></Suspense>,
          },
          {
            path: 'catalog',
            element: <Suspense fallback={<Loading />}><CatalogView /></Suspense>,
          },
          {
            path: 'playlists',
            element: <Suspense fallback={<Loading />}><PlaylistsView /></Suspense>,
          },
          {
            path: 'playlists/:id',
            element: <Suspense fallback={<Loading />}><PlaylistDetail /></Suspense>,
          },
          {
            path: 'history',
            element: <Suspense fallback={<Loading />}><HistoryView /></Suspense>,
          },
          {
            path: 'health',
            element: <pre className="text-brand-400 p-4">{'{"status":"ok"}'}</pre>,
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])
