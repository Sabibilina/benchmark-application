import { useQuery } from "@tanstack/react-query";
import { fetchHistory } from "../../api/analyticsApi";
import { ErrorState } from "../../components/common/ErrorState";
import { LoadingState } from "../../components/common/LoadingState";

export function HistoryPage() {
  const history = useQuery({ queryKey: ["history"], queryFn: () => fetchHistory() });
  if (history.isLoading) return <LoadingState />;
  if (history.isError) return <ErrorState message={history.error.message} />;
  const events = history.data?.content ?? [];
  const grouped = events.reduce<Record<string, typeof events>>((groups, event) => {
    const key = event.timestamp.slice(0, 10);
    groups[key] = [...(groups[key] ?? []), event];
    return groups;
  }, {});
  return (
    <section>
      <h1 className="text-3xl font-semibold">Listening History</h1>
      <div className="mt-6 space-y-6">
        {Object.entries(grouped).map(([date, events]) => (
          <section key={date}>
            <h2 className="mb-2 text-sm font-semibold text-neutral-600">{date}</h2>
            <div className="space-y-2">
              {(events ?? []).map((event) => (
                <div className="rounded-md border border-line bg-white p-3" key={event.eventId}>
                  <p className="font-medium">Song {event.songId}</p>
                  <p className="text-sm text-neutral-600">{event.type} at {new Date(event.timestamp).toLocaleTimeString()}</p>
                </div>
              ))}
            </div>
          </section>
        ))}
      </div>
    </section>
  );
}
