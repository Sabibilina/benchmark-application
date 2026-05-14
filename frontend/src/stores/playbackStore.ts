import { create } from "zustand";
import { endStream, skipStream, startStream } from "../api/streamingApi";
import type { PlaybackStatus, StreamDescriptor } from "../types/playback";
import { useMetricsStore } from "./metricsStore";

interface PlaybackState {
  status: PlaybackStatus;
  songId: string | null;
  descriptor: StreamDescriptor | null;
  error: string | null;
  start: (songId: string) => Promise<void>;
  pause: () => void;
  resume: () => void;
  complete: () => Promise<void>;
  skip: () => Promise<void>;
  reset: () => void;
}

export const usePlaybackStore = create<PlaybackState>((set, get) => ({
  status: "idle",
  songId: null,
  descriptor: null,
  error: null,
  start: async (songId) => {
    set({ status: "loading", songId, descriptor: null, error: null });
    try {
      const descriptor = await startStream(songId);
      set({ status: "playing", descriptor });
    } catch (error) {
      useMetricsStore.getState().recordPlaybackFailure();
      set({ status: "idle", error: error instanceof Error ? error.message : "Playback failed" });
    }
  },
  pause: () => {
    if (get().status === "playing") {
      set({ status: "paused" });
    }
  },
  resume: () => {
    if (get().status === "paused") {
      set({ status: "playing" });
    }
  },
  complete: async () => {
    const songId = get().songId;
    if (songId) {
      await endStream(songId);
    }
    set({ status: "ended" });
  },
  skip: async () => {
    const songId = get().songId;
    if (songId) {
      await skipStream(songId);
    }
    set({ status: "skipped" });
  },
  reset: () => set({ status: "idle", songId: null, descriptor: null, error: null })
}));
