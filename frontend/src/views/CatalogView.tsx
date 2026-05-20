import { useState } from 'react'
import { useCatalogSongs, useArtistTopTracks } from '../hooks/useCatalog'
import { usePlayerStore } from '../stores/playerStore'
import { Button } from '../components/ui/Button'
import { Spinner } from '../components/ui/Spinner'
import { Modal } from '../components/ui/Modal'
import type { Song } from '../types'

const SORT_OPTIONS = [
  { label: 'Title', value: 'title' },
  { label: 'Artist', value: 'artist' },
  { label: 'BPM', value: 'tempo' },
  { label: 'Popularity', value: 'popularity' },
]

function ArtistModal({ artistId, onClose }: { artistId: string; onClose: () => void }) {
  const { data: tracks, isLoading } = useArtistTopTracks(artistId)
  const play = usePlayerStore((s) => s.play)

  return (
    <Modal open onClose={onClose} title={`Top Tracks`}>
      {isLoading ? (
        <div className="flex justify-center py-6"><Spinner /></div>
      ) : !tracks?.length ? (
        <p className="text-zinc-500 text-sm">No tracks found.</p>
      ) : (
        <ul className="flex flex-col gap-2 max-h-72 overflow-y-auto">
          {tracks.map((t) => (
            <li key={t.id} className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm text-zinc-100 line-clamp-1">{t.title}</p>
                <p className="text-xs text-zinc-400">{t.genre ?? '—'} · {t.tempo ? `${Math.round(t.tempo)} BPM` : ''}</p>
              </div>
              <Button size="sm" variant="ghost" onClick={() => { play(t); onClose() }}>
                Play
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}

export default function CatalogView() {
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState<string | undefined>(undefined)
  const [artistId, setArtistId] = useState<string | null>(null)

  const { data, isLoading, isFetching } = useCatalogSongs(page, 20, sort)
  const play = usePlayerStore((s) => s.play)

  const songs: Song[] = data?.content ?? []
  const totalPages = data?.totalPages ?? 1

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold text-zinc-100">Catalog</h1>

        <div className="flex items-center gap-2">
          <span className="text-sm text-zinc-400">Sort by:</span>
          <div className="flex gap-1">
            {SORT_OPTIONS.map((o) => (
              <Button
                key={o.value}
                size="sm"
                variant={sort === o.value ? 'primary' : 'ghost'}
                onClick={() => { setSort(o.value); setPage(0) }}
              >
                {o.label}
              </Button>
            ))}
            {sort && (
              <Button size="sm" variant="ghost" onClick={() => { setSort(undefined); setPage(0) }}>
                Clear
              </Button>
            )}
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-zinc-800">
          <table className="w-full text-sm">
            <thead className="bg-zinc-900 text-zinc-400">
              <tr>
                <th className="text-left px-4 py-3 font-medium">#</th>
                <th className="text-left px-4 py-3 font-medium">Title</th>
                <th className="text-left px-4 py-3 font-medium">Artist</th>
                <th className="text-left px-4 py-3 font-medium hidden md:table-cell">Album</th>
                <th className="text-left px-4 py-3 font-medium hidden lg:table-cell">Genre</th>
                <th className="text-left px-4 py-3 font-medium hidden lg:table-cell">BPM</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {songs.map((song, idx) => (
                <tr key={song.id} className="hover:bg-zinc-900/60 transition-colors group cursor-pointer" onClick={() => play(song)}>
                  <td className="px-4 py-3 text-zinc-500">{page * 20 + idx + 1}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <button
                        onClick={(e) => { e.stopPropagation(); play(song) }}
                        className="opacity-0 group-hover:opacity-100 w-7 h-7 rounded-full bg-brand-500 flex items-center justify-center flex-shrink-0 transition-opacity"
                      >
                        <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                        </svg>
                      </button>
                      <span className="text-zinc-100 line-clamp-1">{song.title}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={(e) => { e.stopPropagation(); if (song.artistId) setArtistId(song.artistId) }}
                      className={['text-zinc-300 hover:text-brand-400 transition-colors line-clamp-1', song.artistId ? 'underline-offset-2 hover:underline' : ''].join(' ')}
                    >
                      {song.artist}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-zinc-400 hidden md:table-cell line-clamp-1">{song.album ?? '—'}</td>
                  <td className="px-4 py-3 text-zinc-400 hidden lg:table-cell">{song.genre ?? '—'}</td>
                  <td className="px-4 py-3 text-zinc-400 hidden lg:table-cell">{song.tempo ? Math.round(song.tempo) : '—'}</td>
                  <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                    <Button size="sm" variant="ghost" onClick={() => play(song)}>Play</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      <div className="flex items-center justify-between">
        <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          ← Previous
        </Button>
        <span className="text-sm text-zinc-400">
          Page {page + 1} of {totalPages}
          {isFetching && <Spinner size="sm" className="inline ml-2" />}
        </span>
        <Button variant="ghost" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
          Next →
        </Button>
      </div>

      {artistId && (
        <ArtistModal artistId={artistId} onClose={() => setArtistId(null)} />
      )}
    </div>
  )
}
