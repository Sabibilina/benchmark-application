import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";
import { LoginPage } from "../features/auth/LoginPage";
import { ProtectedRoute } from "../features/auth/ProtectedRoute";
import { RegisterPage } from "../features/auth/RegisterPage";
import { CatalogPage } from "../features/catalog/CatalogPage";
import { SongDetailPage } from "../features/catalog/SongDetailPage";
import { HealthPage } from "../features/health/HealthPage";
import { HistoryPage } from "../features/history/ListeningHistoryPage";
import { HomePage } from "../features/home/HomePage";
import { PlaylistDetailPage } from "../features/playlists/PlaylistDetailPage";
import { PlaylistsPage } from "../features/playlists/PlaylistsPage";
import { SearchPage } from "../features/search/SearchPage";

export const router = createBrowserRouter([
  { path: "/health", element: <HealthPage /> },
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  {
    path: "/",
    element: (
      <ProtectedRoute>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: "search", element: <SearchPage /> },
      { path: "catalog", element: <CatalogPage /> },
      { path: "catalog/songs/:songId", element: <SongDetailPage /> },
      { path: "playlists", element: <PlaylistsPage /> },
      { path: "playlists/:playlistId", element: <PlaylistDetailPage /> },
      { path: "history", element: <HistoryPage /> }
    ]
  },
  { path: "*", element: <Navigate to="/" replace /> }
]);
