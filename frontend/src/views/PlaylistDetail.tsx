import { useParams } from 'react-router-dom'
import { useState } from 'react'
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
  arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { usePlaylist, useRemoveTrack, useReorderTracks, useAddTrack } from '../hooks/usePlaylists'
import { useSong } from '../hooks/useCatalog'
import { useSearch } from '../hooks/useSearch'
import { usePlayerStore } from '../stores/playerStore'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Spinner } from '../components/ui/Spinner'
import type { PlaylistTrack, Song } from '../types'

function TrackRow({
  track,
  playlistId,
  onPlay,
}: {
  track: PlaylistTrack
  playlistId: string
  onPlay: (song: Song) => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: track.songId })
  const { data: song } = useSong(Number(track.songId))
  const removeTrack = useRemoveTrack()

  const style = { transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 }

  return (
    <tr ref={setNodeRef} style={style} className="group hover:bg-zinc-900/60 transition-colors">
      <td className="px-3 py-2.5 text-zinc-600 cursor-grab active:cursor-grabbing" {...attributes} {...listeners}>
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8h16M4 16h16" />
        </svg>
      </td>
      <td className="px-3 py-2.5 text-zinc-100">
        <div>
          <p className="text-sm line-clamp-1">{song?.title ?? track.songId}</p>
          <p className="text-xs text-zinc-500 line-clamp-1">{song?.artist}</p>
        </div>
      </td>
      <td className="px-3 py-2.5 text-zinc-400 text-sm hidden md:table-cell">{song?.album ?? '—'}</td>
      <td className="px-3 py-2.5 text-zinc-500 text-xs hidden lg:table-cell">
        {new Date(track.addedAt).toLocaleDateString()}
      </td>
      <td className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          {song && (
            <Button size="sm" variant="ghost" onClick={() => onPlay(song)}>Play</Button>
          )}
          <button
            onClick={() => removeTrack.mutate({ playlistId, songId: track.songId })}
            className="opacity-0 group-hover:opacity-100 p-1.5 text-zinc-500 hover:text-red-400 transition-all"
            aria-label="Remove track"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </td>
    </tr>
  )
}

function AddTracksPanel({ playlistId }: { playlistId: string }) {
  const [q, setQ] = useState('')
  const addTrack = useAddTrack()
  const { data: results, isFetching } = useSearch({ q: q || undefined }, !!q)

  return (
    <div className="rounded-xl border border-zinc-800 p-4 flex flex-col gap-3">
      <h3 className="font-semibold text-zinc-200">Add Tracks</h3>
      <Input
        placeholder="Search to add…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      {isFetching && <Spinner size="sm" />}
      {results && results.length > 0 && (
        <ul className="flex flex-col gap-1 max-h-48 overflow-y-auto">
          {results.slice(0, 20).map((r) => (
            <li key={r.songId} className="flex items-center justify-between gap-2 px-2 py-1.5 rounded-lg hover:bg-zinc-800">
              <div className="min-w-0">
                <p className="text-sm text-zinc-100 line-clamp-1">{r.title}</p>
                <p className="text-xs text-zinc-400">{r.artist}</p>
              </div>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => addTrack.mutate({ playlistId, songId: String(r.songId) })}
              >
                Add
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function PlaylistDetail() {
  const { id } = useParams<{ id: string }>()
  const { data: playlist, isLoading } = usePlaylist(id)
  const reorder = useReorderTracks()
  const play = usePlayerStore((s) => s.play)

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const [localTracks, setLocalTracks] = useState<PlaylistTrack[] | null>(null)
  const tracks = localTracks ?? playlist?.tracks?.slice().sort((a, b) => a.position - b.position) ?? []

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over || active.id === over.id) return

    const oldIndex = tracks.findIndex((t) => t.songId === active.id)
    const newIndex = tracks.findIndex((t) => t.songId === over.id)
    const reordered = arrayMove(tracks, oldIndex, newIndex)
    setLocalTracks(reordered)
    reorder.mutate({ playlistId: id!, orderedSongIds: reordered.map((t) => t.songId) })
  }

  if (isLoading) {
    return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  }

  if (!playlist) {
    return <p className="text-zinc-500">Playlist not found.</p>
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <div className="flex items-center gap-3">
          {playlist.likedSongs && (
            <svg className="w-6 h-6 text-brand-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" clipRule="evenodd" />
            </svg>
          )}
          <h1 className="text-2xl font-bold text-zinc-100">{playlist.name}</h1>
        </div>
        <p className="text-zinc-500 text-sm mt-1">{tracks.length} tracks</p>
      </div>

      {tracks.length === 0 ? (
        <p className="text-zinc-600">No tracks yet. Add some below.</p>
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={tracks.map((t) => t.songId)} strategy={verticalListSortingStrategy}>
            <div className="overflow-x-auto rounded-xl border border-zinc-800">
              <table className="w-full text-sm">
                <thead className="bg-zinc-900 text-zinc-400">
                  <tr>
                    <th className="px-3 py-3 w-8" />
                    <th className="text-left px-3 py-3 font-medium">Title</th>
                    <th className="text-left px-3 py-3 font-medium hidden md:table-cell">Album</th>
                    <th className="text-left px-3 py-3 font-medium hidden lg:table-cell">Added</th>
                    <th className="px-3 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-800">
                  {tracks.map((track) => (
                    <TrackRow
                      key={track.songId}
                      track={track}
                      playlistId={id!}
                      onPlay={play}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          </SortableContext>
        </DndContext>
      )}

      <AddTracksPanel playlistId={id!} />
    </div>
  )
}
