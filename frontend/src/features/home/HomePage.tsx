import { useQuery } from "@tanstack/react-query";
import { fetchGlobalCharts } from "../../api/analyticsApi";
import { fetchDailyMix } from "../../api/recommendationApi";
import { ErrorState } from "../../components/common/ErrorState";
import { LoadingState } from "../../components/common/LoadingState";

export function HomePage() {
  const dailyMix = useQuery({ queryKey: ["daily-mix"], queryFn: fetchDailyMix });
  const charts = useQuery({ queryKey: ["global-charts"], queryFn: fetchGlobalCharts });
  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-3xl font-semibold">Home</h1>
        <p className="mt-2 text-neutral-600">Daily mixes and globally trending tracks.</p>
      </div>
      <section>
        <h2 className="mb-3 text-lg font-semibold">Daily Mix</h2>
        {dailyMix.isLoading ? <LoadingState /> : dailyMix.isError ? <ErrorState message={dailyMix.error.message} /> : (
          <div className="grid gap-3 md:grid-cols-3">
            {(dailyMix.data?.recommendations ?? []).map((item) => (
              <div className="rounded-md border border-line bg-white p-4" key={item.songId}>
                <p className="font-medium">Song {item.songId}</p>
                <p className="text-sm text-neutral-600">{item.reason}</p>
              </div>
            ))}
          </div>
        )}
      </section>
      <section>
        <h2 className="mb-3 text-lg font-semibold">Trending</h2>
        {charts.isLoading ? <LoadingState /> : charts.isError ? <ErrorState message={charts.error.message} /> : (
          <div className="grid gap-3 md:grid-cols-3">
            {(charts.data ?? []).slice(0, 6).map((item) => (
              <div className="rounded-md border border-line bg-white p-4" key={item.songId}>
                <p className="font-medium">#{item.rank} Song {item.songId}</p>
                <p className="text-sm text-neutral-600">{item.playCount} plays</p>
              </div>
            ))}
          </div>
        )}
      </section>
    </section>
  );
}
