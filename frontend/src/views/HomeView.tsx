import { useDailyMix, useSimilarSongs } from '../hooks/useRecommendations'
import { useGlobalCharts } from '../hooks/useAnalytics'
import { useHistory } from '../hooks/useAnalytics'
import { useSong } from '../hooks/useCatalog'
import { usePlayerStore } from '../stores/playerStore'
import { Spinner } from '../components/ui/Spinner'
import type { Song, SongRecommendation } from '../types'

function recToSong(r: SongRecommendation): Song {
  return { id: r.id, trackId: String(r.id), title: r.title, artist: r.artist, genre: r.genre, tempo: r.tempo }
}

function SongCard({ song, onPlay }: { song: Song; onPlay: (s: Song) => void }) {
  return (
    <div className="group relative flex flex-col gap-2 p-3 rounded-xl bg-zinc-900 border border-zinc-800 hover:bg-zinc-800 transition-colors min-w-[160px]">
      <div className="w-full aspect-square rounded-lg bg-gradient-to-br from-brand-700 to-zinc-800 flex items-center justify-center">
        <svg className="w-8 h-8 text-brand-300 opacity-60" fill="currentColor" viewBox="0 0 20 20">
          <path d="M18 3a1 1 0 00-1.196-.98l-10 2A1 1 0 006 5v9.114A4.369 4.369 0 005 14c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V7.82l8-1.6v5.894A4.37 4.37 0 0015 12c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V3z" />
        </svg>
      </div>
      <div className="min-w-0">
        <p className="text-sm font-medium text-zinc-100 line-clamp-1">{song.title}</p>
        <p className="text-xs text-zinc-400 line-clamp-1">{song.artist}</p>
      </div>
      <button
        onClick={() => onPlay(song)}
        className="absolute bottom-3 right-3 opacity-0 group-hover:opacity-100 w-8 h-8 rounded-full bg-brand-500 flex items-center justify-center shadow-lg transition-opacity"
        aria-label={`Play ${song.title}`}
      >
        <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
        </svg>
      </button>
    </div>
  )
}

function HorizontalRail({ title, songs, onPlay }: { title: string; songs: Song[]; onPlay: (s: Song) => void }) {
  if (songs.length === 0) return null
  return (
    <section>
      <h2 className="text-lg font-semibold text-zinc-100 mb-3">{title}</h2>
      <div className="flex gap-4 overflow-x-auto pb-2">
        {songs.map((s) => (
          <SongCard key={s.id} song={s} onPlay={onPlay} />
        ))}
      </div>
    </section>
  )
}

function SimilarSection() {
  const { data: history } = useHistory()
  const firstSongId = history?.[0]?.songId
  const { data: similar } = useSimilarSongs(firstSongId ? Number(firstSongId) : undefined)
  const { data: seedSong } = useSong(firstSongId ? Number(firstSongId) : undefined)
  const play = usePlayerStore((s) => s.play)

  if (!similar?.songs?.length) return null

  return (
    <HorizontalRail
      title={`Because you listened to ${seedSong?.title ?? '…'}`}
      songs={similar.songs.map(recToSong)}
      onPlay={play}
    />
  )
}

export default function HomeView() {
  const { data: dailyMix, isLoading: dmLoading } = useDailyMix()
  const { data: charts, isLoading: chartsLoading } = useGlobalCharts()
  const play = usePlayerStore((s) => s.play)

  const chartsAsSongs: Song[] = (charts ?? []).map((c) => ({
    id: Number(c.songId),
    trackId: c.songId,
    title: `Track #${c.rank}`,
    artist: `${c.playCount.toLocaleString()} plays`,
  }))

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Good day</h1>
        <p className="text-zinc-400 text-sm mt-1">Here's what's waiting for you</p>
      </div>

      {dmLoading ? (
        <div className="flex items-center gap-2 text-zinc-500">
          <Spinner size="sm" /> Loading daily mix…
        </div>
      ) : (
        <HorizontalRail
          title="Daily Mix"
          songs={(dailyMix?.songs ?? []).map(recToSong)}
          onPlay={play}
        />
      )}

      {chartsLoading ? (
        <div className="flex items-center gap-2 text-zinc-500">
          <Spinner size="sm" /> Loading trending…
        </div>
      ) : (
        <HorizontalRail
          title="Trending Now"
          songs={chartsAsSongs}
          onPlay={play}
        />
      )}

      <SimilarSection />
    </div>
  )
}
