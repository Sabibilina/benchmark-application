import type { StreamDescriptor } from "../types/playback";
import { api } from "./client";

export async function startStream(songId: string): Promise<StreamDescriptor> {
  const response = await api.streaming.get<StreamDescriptor>(`/stream/${songId}`);
  return response.data;
}

export async function endStream(songId: string): Promise<void> {
  await api.streaming.post(`/stream/${songId}/ended`);
}

export async function skipStream(songId: string): Promise<void> {
  await api.streaming.post(`/stream/${songId}/skipped`);
}
