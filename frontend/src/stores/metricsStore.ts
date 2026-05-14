import { create } from "zustand";

interface MetricsState {
  pageLoadStartedAt: number;
  pageLoadMs: number | null;
  apiErrors: string[];
  playbackFailures: number;
  markPageLoaded: () => void;
  recordApiError: (message: string) => void;
  recordPlaybackFailure: () => void;
}

export const useMetricsStore = create<MetricsState>((set, get) => ({
  pageLoadStartedAt: performance.now(),
  pageLoadMs: null,
  apiErrors: [],
  playbackFailures: 0,
  markPageLoaded: () => set({ pageLoadMs: Math.round(performance.now() - get().pageLoadStartedAt) }),
  recordApiError: (message) => set((state) => ({ apiErrors: [...state.apiErrors.slice(-9), message] })),
  recordPlaybackFailure: () => set((state) => ({ playbackFailures: state.playbackFailures + 1 }))
}));
