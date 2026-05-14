import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { createPlaylist, fetchPlaylists } from "../../api/playlistApi";
import { ErrorState } from "../../components/common/ErrorState";
import { LoadingState } from "../../components/common/LoadingState";

export function PlaylistsPage() {
  const queryClient = useQueryClient();
  const playlists = useQuery({ queryKey: ["playlists"], queryFn: fetchPlaylists });
  const create = useMutation({
    mutationFn: createPlaylist,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["playlists"] })
  });
  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-3xl font-semibold">Playlists</h1>
        <p className="mt-2 text-neutral-600">Your playlists, including Liked Songs.</p>
      </div>
      <form
        className="flex flex-col gap-2 rounded-md border border-line bg-white p-4 sm:flex-row"
        onSubmit={(event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          create.mutate({ name: String(data.get("name") ?? ""), description: String(data.get("description") ?? "") });
          event.currentTarget.reset();
        }}
      >
        <input name="name" aria-label="Playlist name" className="focus-ring flex-1 rounded-md border border-line px-3 py-2" placeholder="Playlist name" required />
        <input name="description" aria-label="Playlist description" className="focus-ring flex-1 rounded-md border border-line px-3 py-2" placeholder="Description" />
        <button className="focus-ring rounded-md bg-brand px-4 py-2 font-medium text-white">Create</button>
      </form>
      {create.isError ? <ErrorState message={create.error.message} /> : null}
      {playlists.isLoading ? <LoadingState /> : playlists.isError ? <ErrorState message={playlists.error.message} /> : (
        <div className="grid gap-3 md:grid-cols-3">
          {(playlists.data ?? []).map((playlist) => (
            <Link key={playlist.id} to={`/playlists/${playlist.id}`} className="rounded-md border border-line bg-white p-4 hover:border-brand">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="font-semibold">{playlist.name}</h2>
                  <p className="mt-1 text-sm text-neutral-600">{playlist.description}</p>
                </div>
                {playlist.likedSongs ? <span className="rounded-full bg-amber-100 px-2 py-1 text-xs text-amber-900">Liked Songs</span> : null}
              </div>
              <p className="mt-3 text-sm text-neutral-500">{playlist.trackCount} tracks</p>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
