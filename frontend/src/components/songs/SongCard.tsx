import { Play } from "lucide-react";
import type { Song } from "../../types/catalog";

export function SongCard({ song, onPlay }: { song: Song; onPlay?: (song: Song) => void }) {
  return (
    <article className="rounded-md border border-line bg-white p-4 shadow-sm">
      <div className="mb-3 flex h-20 items-center justify-center rounded-md bg-teal-50 text-2xl font-semibold text-brand">
        {song.title.slice(0, 1).toUpperCase()}
      </div>
      <h3 className="line-clamp-2 text-sm font-semibold">{song.title}</h3>
      <p className="mt-1 text-sm text-neutral-600">{song.artist}</p>
      <p className="mt-2 text-xs text-neutral-500">{song.genre ?? "Unknown genre"} · {song.releaseYear ?? "Unknown year"}</p>
      {onPlay ? (
        <button className="focus-ring mt-4 inline-flex items-center gap-2 rounded-md bg-brand px-3 py-2 text-sm font-medium text-white" onClick={() => onPlay(song)}>
          <Play size={16} /> Play
        </button>
      ) : null}
    </article>
  );
}
