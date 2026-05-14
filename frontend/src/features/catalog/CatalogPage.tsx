import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { fetchSongs } from "../../api/catalogApi";
import { ErrorState } from "../../components/common/ErrorState";
import { LoadingState } from "../../components/common/LoadingState";
import { SongList } from "../../components/songs/SongList";
import { usePlaybackStore } from "../../stores/playbackStore";
import { useQueueStore } from "../../stores/queueStore";
import type { Song } from "../../types/catalog";

export function CatalogPage() {
  const [page, setPage] = useState(0);
  const songs = useQuery({ queryKey: ["songs", page], queryFn: () => fetchSongs(page, 12) });
  const songPage = songs.data;
  const playNow = useQueueStore((state) => state.playNow);
  const start = usePlaybackStore((state) => state.start);
  const play = (song: Song) => {
    playNow(song);
    void start(song.id);
  };
  return (
    <section>
      <h1 className="text-3xl font-semibold">Catalog</h1>
      <p className="mt-2 text-neutral-600">Browse seeded catalog songs.</p>
      <div className="mt-6">
        {songs.isLoading ? <LoadingState /> : songs.isError ? <ErrorState message={songs.error.message} /> : <SongList songs={songPage?.content ?? []} onPlay={play} />}
      </div>
      <div className="mt-6 flex items-center gap-2">
        <button className="focus-ring rounded-md border border-line px-3 py-2" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>Previous</button>
        <span className="text-sm text-neutral-600">Page {page + 1}</span>
        <button className="focus-ring rounded-md border border-line px-3 py-2" disabled={songPage ? page + 1 >= (songPage.totalPages ?? 1) : true} onClick={() => setPage((value) => value + 1)}>Next</button>
      </div>
    </section>
  );
}
