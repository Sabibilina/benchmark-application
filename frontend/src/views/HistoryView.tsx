import { useHistory } from '../hooks/useAnalytics'
import { useSong } from '../hooks/useCatalog'
import { Spinner } from '../components/ui/Spinner'
import type { HistoryEntry } from '../types'

function groupByDate(entries: HistoryEntry[]): { label: string; entries: HistoryEntry[] }[] {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)

  const groups = new Map<string, HistoryEntry[]>()
  for (const e of entries) {
    const d = new Date(e.timestamp)
    const date = new Date(d.getFullYear(), d.getMonth(), d.getDate())
    let label: string
    if (date.getTime() === today.getTime()) label = 'Today'
    else if (date.getTime() === yesterday.getTime()) label = 'Yesterday'
    else label = date.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
    const group = groups.get(label) ?? []
    group.push(e)
    groups.set(label, group)
  }
  return Array.from(groups.entries()).map(([label, entries]) => ({ label, entries }))
}

function HistoryEntryRow({ entry }: { entry: HistoryEntry }) {
  const { data: song } = useSong(Number(entry.songId))

  return (
    <div className="flex items-center gap-4 py-3 border-b border-zinc-800/50 last:border-0">
      <div className="w-9 h-9 rounded bg-zinc-800 flex items-center justify-center flex-shrink-0">
        <svg className="w-4 h-4 text-zinc-500" fill="currentColor" viewBox="0 0 20 20">
          <path d="M18 3a1 1 0 00-1.196-.98l-10 2A1 1 0 006 5v9.114A4.369 4.369 0 005 14c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V7.82l8-1.6v5.894A4.37 4.37 0 0015 12c-1.657 0-3 .895-3 2s1.343 2 3 2 3-.895 3-2V3z" />
        </svg>
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-zinc-100 line-clamp-1">
          {song?.title ?? `Song ${entry.songId}`}
        </p>
        <p className="text-xs text-zinc-400 line-clamp-1">
          {song?.artist ?? '—'}
          {song?.album && ` · ${song.album}`}
        </p>
      </div>

      <div className="flex-shrink-0 text-right">
        <p className="text-xs text-zinc-500">
          {new Date(entry.timestamp).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}
        </p>
        {entry.eventType && (
          <span className={[
            'text-[10px] font-medium px-1.5 py-0.5 rounded-full uppercase tracking-wide',
            entry.eventType === 'SKIP' ? 'bg-yellow-900/40 text-yellow-400' :
            entry.eventType === 'COMPLETE' ? 'bg-brand-900/40 text-brand-400' :
            'bg-zinc-800 text-zinc-400',
          ].join(' ')}>
            {entry.eventType}
          </span>
        )}
      </div>
    </div>
  )
}

export default function HistoryView() {
  const { data: history, isLoading } = useHistory()

  const groups = groupByDate(history ?? [])

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Listening History</h1>
        <p className="text-zinc-400 text-sm mt-1">What you've been playing</p>
      </div>

      {isLoading && (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      )}

      {!isLoading && groups.length === 0 && (
        <p className="text-zinc-500">No history yet. Start listening!</p>
      )}

      {groups.map(({ label, entries }) => (
        <section key={label}>
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-2">{label}</h2>
          <div className="rounded-xl bg-zinc-900/50 border border-zinc-800 px-4">
            {entries.map((e, i) => (
              <HistoryEntryRow key={`${e.songId}-${e.timestamp}-${i}`} entry={e} />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
