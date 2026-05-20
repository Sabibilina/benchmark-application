import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { usePlaylists, useCreatePlaylist, useDeletePlaylist } from '../hooks/usePlaylists'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Modal } from '../components/ui/Modal'
import { Spinner } from '../components/ui/Spinner'

const schema = z.object({
  name: z.string().min(1, 'Name is required'),
  description: z.string().optional(),
})
type FormData = z.infer<typeof schema>

function NewPlaylistModal({ onClose }: { onClose: () => void }) {
  const create = useCreatePlaylist()
  const navigate = useNavigate()

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit(data: FormData) {
    const pl = await create.mutateAsync({ name: data.name, description: data.description })
    onClose()
    navigate(`/playlists/${pl.id}`)
  }

  return (
    <Modal open onClose={onClose} title="New Playlist">
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
        <Input id="name" label="Name" placeholder="My playlist" error={errors.name?.message} {...register('name')} />
        <Input id="description" label="Description (optional)" placeholder="Optional description" {...register('description')} />
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Creating…' : 'Create'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export default function PlaylistsView() {
  const navigate = useNavigate()
  const { data: playlists, isLoading } = usePlaylists()
  const deletePlaylist = useDeletePlaylist()
  const [creating, setCreating] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-zinc-100">Playlists</h1>
        <Button onClick={() => setCreating(true)}>+ New Playlist</Button>
      </div>

      {isLoading && (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {playlists?.map((pl) => (
          <div
            key={pl.id}
            className="group relative flex flex-col gap-3 p-4 rounded-xl bg-zinc-900 border border-zinc-800 hover:bg-zinc-800 transition-colors cursor-pointer"
            onClick={() => navigate(`/playlists/${pl.id}`)}
          >
            <div className={['w-full aspect-square rounded-lg flex items-center justify-center', pl.likedSongs ? 'bg-gradient-to-br from-brand-600 to-purple-700' : 'bg-gradient-to-br from-zinc-700 to-zinc-800'].join(' ')}>
              {pl.likedSongs ? (
                <svg className="w-12 h-12 text-white" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" clipRule="evenodd" />
                </svg>
              ) : (
                <svg className="w-10 h-10 text-zinc-500" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M18 3a1 1 0 00-1.196-.98l-10 2A1 1 0 006 5v9.114A4.369 4.369 0 005 14c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V7.82l8-1.6v5.894A4.37 4.37 0 0015 12c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V3z" />
                </svg>
              )}
            </div>
            <div>
              <p className="font-semibold text-zinc-100 line-clamp-1">{pl.name}</p>
              <p className="text-xs text-zinc-500 mt-0.5">{pl.trackCount} tracks</p>
            </div>

            {!pl.likedSongs && (
              <button
                onClick={(e) => { e.stopPropagation(); setConfirmDelete(pl.id) }}
                className="absolute top-3 right-3 opacity-0 group-hover:opacity-100 p-1.5 rounded-lg text-zinc-500 hover:text-red-400 hover:bg-zinc-700 transition-all"
                aria-label="Delete playlist"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            )}
          </div>
        ))}
      </div>

      {creating && <NewPlaylistModal onClose={() => setCreating(false)} />}

      {confirmDelete && (
        <Modal open onClose={() => setConfirmDelete(null)} title="Delete playlist?">
          <p className="text-sm text-zinc-400 mb-6">This action cannot be undone.</p>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setConfirmDelete(null)}>Cancel</Button>
            <Button
              variant="danger"
              onClick={() => {
                deletePlaylist.mutate(confirmDelete)
                setConfirmDelete(null)
              }}
            >
              Delete
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
