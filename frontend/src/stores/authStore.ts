import { create } from 'zustand'
import { configureClientAuth } from '../api/client'
import { authApi } from '../api/auth'
import type { User } from '../types'

interface AuthState {
  token: string | null
  user: User | null
  login: (email: string, password: string) => Promise<void>
  loginWithToken: (token: string, user: User) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set, get) => {
  configureClientAuth(
    () => get().token,
    () => {
      set({ token: null, user: null })
      window.location.replace('/login')
    },
  )

  return {
    token: null,
    user: null,

    async login(email, password) {
      const res = await authApi.login(email, password)
      set({ token: res.data.token, user: null })
    },

    loginWithToken(token, user) {
      set({ token, user })
    },

    logout() {
      set({ token: null, user: null })
    },
  }
})
