import axios, { type AxiosInstance } from "axios";
import { useAuthStore } from "../features/auth/authStore";
import { useMetricsStore } from "../stores/metricsStore";
import { endpoints } from "./endpoints";
import { normalizeError } from "./errors";
import { installRetry } from "./retry";

const publicAuthPaths = ["/auth/register", "/auth/login"];

function createServiceClient(baseURL: string): AxiosInstance {
  const client = axios.create({ baseURL, timeout: 10000 });
  client.interceptors.request.use((config) => {
    const token = useAuthStore.getState().token;
    const path = `${config.url ?? ""}`;
    if (token && !publicAuthPaths.some((publicPath) => path.endsWith(publicPath))) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });
  installRetry(client);
  client.interceptors.response.use(
    (response) => response,
    (error) => {
      const normalized = normalizeError(error);
      useMetricsStore.getState().recordApiError(normalized.message);
      throw normalized;
    }
  );
  return client;
}

export const api = {
  auth: createServiceClient(endpoints.auth),
  catalog: createServiceClient(endpoints.catalog),
  playlist: createServiceClient(endpoints.playlist),
  streaming: createServiceClient(endpoints.streaming),
  search: createServiceClient(endpoints.search),
  analytics: createServiceClient(endpoints.analytics),
  recommendation: createServiceClient(endpoints.recommendation)
};
