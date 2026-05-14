import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuthStore } from "./authStore";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.token);
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
