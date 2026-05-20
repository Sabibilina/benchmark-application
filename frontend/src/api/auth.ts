import { authClient } from './client'
import type { AuthResponse } from '../types'

export const authApi = {
  login(email: string, password: string) {
    return authClient.post<AuthResponse>('/login', { email, password })
  },
  register(username: string, email: string, password: string) {
    return authClient.post<AuthResponse>('/register', { username, email, password })
  },
}
