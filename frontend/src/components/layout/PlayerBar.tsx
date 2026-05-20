import { usePlayerStore } from '../../stores/playerStore'

function formatTime(s: number) {
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${sec.toString().padStart(2, '0')}`
}

export function PlayerBar() {
  const { currentSong, state, progressSeconds, durationSeconds, volume, pause, resume, skip, setProgress, setVolume } =
    usePlayerStore()

  const isPlaying = state === 'playing'
  const isLoading = state === 'loading'
  const progress = durationSeconds > 0 ? (progressSeconds / durationSeconds) * 100 : 0

  return (
    <footer className="h-20 bg-zinc-900 border-t border-zinc-800 px-4 flex items-center gap-4">
      {/* Song info */}
      <div className="flex items-center gap-3 w-56 flex-shrink-0">
        <div className="w-10 h-10 rounded bg-zinc-700 flex items-center justify-center flex-shrink-0">
          <svg className="w-5 h-5 text-zinc-400" fill="currentColor" viewBox="0 0 20 20">
            <path d="M18 3a1 1 0 00-1.196-.98l-10 2A1 1 0 006 5v9.114A4.369 4.369 0 005 14c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V7.82l8-1.6v5.894A4.37 4.37 0 0015 12c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V3z" />
          </svg>
        </div>
        <div className="min-w-0">
          <p className="text-sm font-medium text-zinc-100 line-clamp-1">
            {currentSong?.title ?? 'Not playing'}
          </p>
          <p className="text-xs text-zinc-400 line-clamp-1">
            {currentSong?.artist ?? '—'}
          </p>
        </div>
      </div>

      {/* Controls */}
      <div className="flex-1 flex flex-col items-center gap-1">
        <div className="flex items-center gap-4">
          {/* Previous — no-op (no history navigation implemented) */}
          <button
            className="text-zinc-500 hover:text-zinc-200 transition-colors disabled:opacity-30"
            disabled
            aria-label="Previous"
          >
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path d="M8.445 14.832A1 1 0 0010 14v-2.798l5.445 3.63A1 1 0 0017 14V6a1 1 0 00-1.555-.832L10 8.798V6a1 1 0 00-1.555-.832l-6 4a1 1 0 000 1.664l6 4z" />
            </svg>
          </button>

          {/* Play / Pause */}
          <button
            onClick={isPlaying ? pause : resume}
            disabled={isLoading || !currentSong}
            className="w-9 h-9 rounded-full bg-brand-500 flex items-center justify-center hover:bg-brand-400 transition-colors disabled:opacity-40"
            aria-label={isPlaying ? 'Pause' : 'Play'}
          >
            {isLoading ? (
              <svg className="w-4 h-4 animate-spin text-white" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            ) : isPlaying ? (
              <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM7 8a1 1 0 012 0v4a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v4a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
              </svg>
            ) : (
              <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
              </svg>
            )}
          </button>

          {/* Skip */}
          <button
            onClick={skip}
            disabled={!currentSong}
            className="text-zinc-400 hover:text-zinc-100 transition-colors disabled:opacity-30"
            aria-label="Skip"
          >
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path d="M4.555 5.168A1 1 0 003 6v8a1 1 0 001.555.832L10 11.202V14a1 1 0 001.555.832l6-4a1 1 0 000-1.664l-6-4A1 1 0 0010 6v2.798L4.555 5.168z" />
            </svg>
          </button>
        </div>

        {/* Progress scrubber */}
        <div className="flex items-center gap-2 w-full max-w-md">
          <span className="text-xs text-zinc-500 w-8 text-right">{formatTime(progressSeconds)}</span>
          <input
            type="range"
            min={0}
            max={durationSeconds || 100}
            value={progressSeconds}
            onChange={(e) => setProgress(Number(e.target.value))}
            disabled={!currentSong}
            className="flex-1 h-1 rounded-full accent-brand-500 cursor-pointer disabled:opacity-30"
          />
          <span className="text-xs text-zinc-500 w-8">{formatTime(durationSeconds)}</span>
        </div>
      </div>

      {/* Volume */}
      <div className="flex items-center gap-2 w-32 flex-shrink-0 justify-end">
        <svg className="w-4 h-4 text-zinc-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
          <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.707.707L4.586 13H2a1 1 0 01-1-1V8a1 1 0 011-1h2.586l3.707-3.707a1 1 0 011.09-.217zM14.657 2.929a1 1 0 011.414 0A9.972 9.972 0 0119 10a9.972 9.972 0 01-2.929 7.071 1 1 0 01-1.414-1.414A7.971 7.971 0 0017 10c0-2.21-.894-4.208-2.343-5.657a1 1 0 010-1.414zm-2.829 2.828a1 1 0 011.415 0A5.983 5.983 0 0115 10a5.984 5.984 0 01-1.757 4.243 1 1 0 01-1.415-1.415A3.984 3.984 0 0013 10a3.983 3.983 0 00-1.172-2.828 1 1 0 010-1.415z" clipRule="evenodd" />
        </svg>
        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={volume}
          onChange={(e) => setVolume(Number(e.target.value))}
          className="w-20 h-1 rounded-full accent-brand-500 cursor-pointer"
        />
      </div>
    </footer>
  )
}
