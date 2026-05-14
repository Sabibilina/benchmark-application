import type { Song } from "../../types/catalog";
import { SongCard } from "./SongCard";

export function SongList({ songs, onPlay }: { songs: Song[]; onPlay?: (song: Song) => void }) {
  if (songs.length === 0) {
    return <p className="py-8 text-sm text-neutral-600">No songs found.</p>;
  }
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {songs.map((song) => <SongCard key={song.id} song={song} onPlay={onPlay} />)}
    </div>
  );
}
