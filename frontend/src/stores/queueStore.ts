import { create } from "zustand";
import type { Song } from "../types/catalog";

interface QueueState {
  queue: Song[];
  currentSong: Song | null;
  setQueue: (songs: Song[]) => void;
  playNow: (song: Song) => void;
}

export const useQueueStore = create<QueueState>((set) => ({
  queue: [],
  currentSong: null,
  setQueue: (queue) => set({ queue }),
  playNow: (song) => set({ currentSong: song })
}));
