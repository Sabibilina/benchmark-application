import { useState, useEffect, useRef } from 'react'
import { useSearch } from '../hooks/useSearch'
import { usePlaylists, useAddTrack } from '../hooks/usePlaylists'
import { usePlayerStore } from '../stores/playerStore'
import { Input } from '../components/ui/Input'
import { Button } from '../components/ui/Button'
import { Spinner } from '../components/ui/Spinner'
import type { Song, SongSearchResult } from '../types'

function searchResultToSong(r: SongSearchResult): Song {
  return {
    id: r.songId,
    trackId: r.trackId,
    title: r.title,
    artist: r.artist,
    album: r.album,
    genre: r.genre,
    year: r.year,
    tempo: r.bpm,
    durationMs: r.durationMs,
  }
}

function AddToPlaylistMenu({ songId }: { songId: string }) {
  const { data: playlists } = usePlaylists()
  const addTrack = useAddTrack()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  return (
    <div className="relative" ref={ref}>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setOpen((o) => !o)}
      >
        + Playlist
      </Button>
      {open && (
        <div className="absolute right-0 top-full mt-1 w-48 rounded-xl bg-zinc-900 border border-zinc-700 shadow-2xl z-20 overflow-hidden">
          {!playlists?.length && (
            <p className="text-xs text-zinc-500 px-3 py-2">No playlists</p>
          )}
          {playlists?.map((pl) => (
            <button
              key={pl.id}
              onClick={() => {
                addTrack.mutate({ playlistId: pl.id, songId })
                setOpen(false)
              }}
              className="w-full text-left px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-800 transition-colors line-clamp-1"
            >
              {pl.likedSongs ? '♥ ' : ''}{pl.name}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default function SearchView() {
  const [q, setQ] = useState('')
  const [genre, setGenre] = useState('')
  const [bpmMin, setBpmMin] = useState('')
  const [bpmMax, setBpmMax] = useState('')
  const [year, setYear] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')

  const play = usePlayerStore((s) => s.play)

  useEffect(() => {
    const id = setTimeout(() => setDebouncedQ(q), 300)
    return () => clearTimeout(id)
  }, [q])

  const params = {
    q: debouncedQ || undefined,
    genre: genre || undefined,
    bpm_min: bpmMin ? Number(bpmMin) : undefined,
    bpm_max: bpmMax ? Number(bpmMax) : undefined,
    year: year ? Number(year) : undefined,
  }

  const { data: results, isFetching } = useSearch(params)

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Search</h1>
      </div>

      {/* Search + filters */}
      <div className="flex flex-col gap-3">
        <Input
          placeholder="Search songs, artists, albums…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          className="text-base"
        />
        <div className="flex flex-wrap gap-3">
          <Input placeholder="Genre" value={genre} onChange={(e) => setGenre(e.target.value)} className="w-36" />
          <Input placeholder="BPM min" type="number" value={bpmMin} onChange={(e) => setBpmMin(e.target.value)} className="w-28" />
          <Input placeholder="BPM max" type="number" value={bpmMax} onChange={(e) => setBpmMax(e.target.value)} className="w-28" />
          <Input placeholder="Year" type="number" value={year} onChange={(e) => setYear(e.target.value)} className="w-24" />
        </div>
      </div>

      {/* Results */}
      {isFetching && (
        <div className="flex items-center gap-2 text-zinc-500">
          <Spinner size="sm" /> Searching…
        </div>
      )}

      {!isFetching && results && results.length === 0 && (
        <p className="text-zinc-500">No results found.</p>
      )}

      {results && results.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-zinc-800">
          <table className="w-full text-sm">
            <thead className="bg-zinc-900 text-zinc-400">
              <tr>
                <th className="text-left px-4 py-3 font-medium">Title</th>
                <th className="text-left px-4 py-3 font-medium">Artist</th>
                <th className="text-left px-4 py-3 font-medium hidden md:table-cell">Album</th>
                <th className="text-left px-4 py-3 font-medium hidden lg:table-cell">Genre</th>
                <th className="text-left px-4 py-3 font-medium hidden lg:table-cell">BPM</th>
                <th className="text-left px-4 py-3 font-medium hidden xl:table-cell">Year</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {results.map((r) => {
                const song = searchResultToSong(r)
                return (
                  <tr key={r.songId} className="hover:bg-zinc-900/60 transition-colors group">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <button
                          onClick={() => play(song)}
                          className="opacity-0 group-hover:opacity-100 w-7 h-7 rounded-full bg-brand-500 flex items-center justify-center flex-shrink-0 transition-opacity"
                        >
                          <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                          </svg>
                        </button>
                        <span className="text-zinc-100 line-clamp-1">{r.title}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-zinc-300 line-clamp-1">{r.artist}</td>
                    <td className="px-4 py-3 text-zinc-400 hidden md:table-cell line-clamp-1">{r.album ?? '—'}</td>
                    <td className="px-4 py-3 text-zinc-400 hidden lg:table-cell">{r.genre ?? '—'}</td>
                    <td className="px-4 py-3 text-zinc-400 hidden lg:table-cell">{r.bpm ?? '—'}</td>
                    <td className="px-4 py-3 text-zinc-400 hidden xl:table-cell">{r.year ?? '—'}</td>
                    <td className="px-4 py-3">
                      <AddToPlaylistMenu songId={String(r.songId)} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
