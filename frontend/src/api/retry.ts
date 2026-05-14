import type { AxiosError, AxiosInstance } from "axios";
import { retryConfig } from "./endpoints";

export function installRetry(client: AxiosInstance): void {
  client.interceptors.response.use(undefined, async (error: AxiosError) => {
    const config = error.config;
    if (!config || !shouldRetry(error)) {
      throw error;
    }
    const retryState = config as unknown as Record<string, unknown>;
    const attempt = Number(retryState.__retryAttempt ?? 0);
    if (attempt >= retryConfig.attempts) {
      throw error;
    }
    retryState.__retryAttempt = attempt + 1;
    await delay(retryConfig.baseDelayMs * 2 ** attempt);
    return client(config);
  });
}

function shouldRetry(error: AxiosError): boolean {
  const method = error.config?.method?.toUpperCase();
  const status = error.response?.status;
  const safeMethod = method === "GET" || method === "HEAD";
  return Boolean(safeMethod && (!status || status >= 500 || status === 429));
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}
