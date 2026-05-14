import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { fetchSong } from "../../api/catalogApi";
import { ErrorState } from "../../components/common/ErrorState";
import { LoadingState } from "../../components/common/LoadingState";

export function SongDetailPage() {
  const { songId = "" } = useParams();
  const song = useQuery({ queryKey: ["song", songId], queryFn: () => fetchSong(songId), enabled: Boolean(songId) });
  if (song.isLoading) return <LoadingState />;
  if (song.isError) return <ErrorState message={song.error.message} />;
  if (!song.data) return <ErrorState message="Song not found" />;
  return (
    <section className="space-y-2">
      <h1 className="text-3xl font-semibold">{song.data.title}</h1>
      <p className="text-neutral-600">{song.data.artist}</p>
      <dl className="grid gap-3 rounded-md border border-line bg-white p-4 sm:grid-cols-2">
        <div><dt className="text-xs text-neutral-500">Album</dt><dd>{song.data.album ?? "Unknown"}</dd></div>
        <div><dt className="text-xs text-neutral-500">Genre</dt><dd>{song.data.genre ?? "Unknown"}</dd></div>
        <div><dt className="text-xs text-neutral-500">BPM</dt><dd>{song.data.bpm ?? "Unknown"}</dd></div>
        <div><dt className="text-xs text-neutral-500">Year</dt><dd>{song.data.releaseYear ?? "Unknown"}</dd></div>
      </dl>
    </section>
  );
}
