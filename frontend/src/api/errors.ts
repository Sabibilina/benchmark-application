import axios from "axios";

export interface ApiError {
  status?: number;
  message: string;
  retryable: boolean;
}

export function normalizeError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const message = extractMessage(error.response?.data) ?? error.message;
    return {
      status,
      message,
      retryable: !status || status >= 500 || status === 429
    };
  }
  if (error instanceof Error) {
    return { message: error.message, retryable: false };
  }
  return { message: "Unexpected error", retryable: false };
}

function extractMessage(data: unknown): string | undefined {
  if (typeof data === "string") {
    return data;
  }
  if (data && typeof data === "object" && "message" in data && typeof data.message === "string") {
    return data.message;
  }
  return undefined;
}
