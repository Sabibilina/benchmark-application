import { create } from "zustand";
import type { AuthResponse, User } from "../../types/auth";

interface AuthState {
  token: string | null;
  user: User | null;
  setSession: (response: AuthResponse) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  setSession: (response) => set({ token: response.accessToken, user: response.user }),
  logout: () => set({ token: null, user: null })
}));
