import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { fetchPlaylist, reorderPlaylist } from "../../api/playlistApi";
import { ErrorState } from "../../components/common/ErrorState";
import { LoadingState } from "../../components/common/LoadingState";
import { PlaylistTrackList } from "../../components/playlists/PlaylistTrackList";
import type { PlaylistTrack } from "../../types/playlist";
import { moveTrack, toReorderPayload } from "./playlistDnD";

export function PlaylistDetailPage() {
  const { playlistId = "" } = useParams();
  const queryClient = useQueryClient();
  const playlist = useQuery({ queryKey: ["playlist", playlistId], queryFn: () => fetchPlaylist(playlistId), enabled: Boolean(playlistId) });
  const [localTracks, setLocalTracks] = useState<PlaylistTrack[] | null>(null);
  const tracks = useMemo(() => localTracks ?? playlist.data?.tracks ?? [], [localTracks, playlist.data?.tracks]);
  const reorder = useMutation({
    mutationFn: (songIds: string[]) => reorderPlaylist(playlistId, songIds),
    onSuccess: () => {
      setLocalTracks(null);
      queryClient.invalidateQueries({ queryKey: ["playlist", playlistId] });
    }
  });
  if (playlist.isLoading) return <LoadingState />;
  if (playlist.isError) return <ErrorState message={playlist.error.message} />;
  if (!playlist.data) return <ErrorState message="Playlist not found" />;
  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-3xl font-semibold">{playlist.data.name}</h1>
          <p className="mt-2 text-neutral-600">{playlist.data.description}</p>
        </div>
        <button className="focus-ring rounded-md border border-line px-3 py-2 text-sm" disabled={playlist.data.likedSongs}>
          {playlist.data.likedSongs ? "Liked Songs cannot be deleted" : "Delete"}
        </button>
      </div>
      <PlaylistTrackList
        tracks={tracks}
        onReorder={(active, over) => {
          const moved = moveTrack(tracks, active, over);
          setLocalTracks(moved);
          reorder.mutate(toReorderPayload(moved));
        }}
      />
      {tracks.length === 0 ? <p className="text-sm text-neutral-600">No tracks yet.</p> : null}
      {reorder.isError ? <ErrorState message={reorder.error.message} /> : null}
    </section>
  );
}
