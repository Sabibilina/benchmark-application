import type { AuthResponse, Credentials } from "../types/auth";
import { api } from "./client";

export async function register(credentials: Credentials): Promise<AuthResponse> {
  const response = await api.auth.post<AuthResponse>("/auth/register", credentials);
  return response.data;
}

export async function login(credentials: Credentials): Promise<AuthResponse> {
  const response = await api.auth.post<AuthResponse>("/auth/login", credentials);
  return response.data;
}
