import { create } from 'zustand'
import { streamingApi } from '../api/streaming'
import type { Song } from '../types'

export type PlaybackState = 'idle' | 'loading' | 'playing' | 'paused' | 'ended' | 'skipped'

interface PlayerState {
  currentSong: Song | null
  queue: Song[]
  state: PlaybackState
  progressSeconds: number
  durationSeconds: number
  volume: number

  play: (song: Song) => void
  pause: () => void
  resume: () => void
  skip: () => void
  enqueue: (songs: Song[]) => void
  setProgress: (seconds: number) => void
  setVolume: (v: number) => void
}

let progressTimer: ReturnType<typeof setInterval> | null = null

function clearTimer() {
  if (progressTimer !== null) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

export const usePlayerStore = create<PlayerState>((set, get) => ({
  currentSong: null,
  queue: [],
  state: 'idle',
  progressSeconds: 0,
  durationSeconds: 0,
  volume: 0.8,

  async play(song) {
    clearTimer()
    set({ currentSong: song, state: 'loading', progressSeconds: 0 })

    const durationSeconds = song.durationMs ? song.durationMs / 1000 : 180

    try {
      await streamingApi.getStream(song.id)
    } catch {
      window.__metrics.playbackFailures++
      set({ state: 'idle' })
      return
    }

    set({ state: 'playing', durationSeconds })

    progressTimer = setInterval(() => {
      const { progressSeconds, durationSeconds: dur, state } = get()
      if (state !== 'playing') {
        clearTimer()
        return
      }
      const next = progressSeconds + 1
      if (next >= dur) {
        clearTimer()
        set({ progressSeconds: dur, state: 'ended' })
        streamingApi.complete(song.id).catch(() => {})
        const { queue } = get()
        if (queue.length > 0) {
          const [nextSong, ...rest] = queue
          set({ queue: rest })
          get().play(nextSong)
        }
      } else {
        set({ progressSeconds: next })
      }
    }, 1000)
  },

  pause() {
    clearTimer()
    set({ state: 'paused' })
  },

  resume() {
    const { currentSong, state } = get()
    if (!currentSong || state !== 'paused') return
    set({ state: 'playing' })

    progressTimer = setInterval(() => {
      const { progressSeconds, durationSeconds: dur, state: s } = get()
      if (s !== 'playing') { clearTimer(); return }
      const next = progressSeconds + 1
      if (next >= dur) {
        clearTimer()
        set({ progressSeconds: dur, state: 'ended' })
        streamingApi.complete(currentSong.id).catch(() => {})
        const { queue } = get()
        if (queue.length > 0) {
          const [nextSong, ...rest] = queue
          set({ queue: rest })
          get().play(nextSong)
        }
      } else {
        set({ progressSeconds: next })
      }
    }, 1000)
  },

  skip() {
    clearTimer()
    const { currentSong, queue } = get()
    if (currentSong) {
      streamingApi.skip(currentSong.id).catch(() => {})
    }
    set({ state: 'skipped', progressSeconds: 0 })
    if (queue.length > 0) {
      const [nextSong, ...rest] = queue
      set({ queue: rest })
      get().play(nextSong)
    } else {
      set({ state: 'idle', currentSong: null })
    }
  },

  enqueue(songs) {
    set((s) => ({ queue: [...s.queue, ...songs] }))
  },

  setProgress(seconds) {
    set({ progressSeconds: seconds })
  },

  setVolume(v) {
    set({ volume: Math.max(0, Math.min(1, v)) })
  },
}))
