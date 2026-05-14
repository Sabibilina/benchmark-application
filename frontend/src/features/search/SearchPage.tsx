import { useMutation } from "@tanstack/react-query";
import { searchSongs, type SearchFilters } from "../../api/searchApi";
import { ErrorState } from "../../components/common/ErrorState";
import { SongList } from "../../components/songs/SongList";
import { normalizeSearchFilters } from "./searchFilters";

export function SearchPage() {
  const mutation = useMutation({ mutationFn: (filters: SearchFilters) => searchSongs(filters) });
  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-3xl font-semibold">Search</h1>
        <p className="mt-2 text-neutral-600">Combine text, genre, BPM, and release year filters.</p>
      </div>
      <form
        className="grid gap-3 rounded-md border border-line bg-white p-4 md:grid-cols-6"
        onSubmit={(event) => {
          event.preventDefault();
          mutation.mutate(normalizeSearchFilters(Object.fromEntries(new FormData(event.currentTarget))));
        }}
      >
        <input name="q" aria-label="Search text" className="focus-ring rounded-md border border-line px-3 py-2 md:col-span-2" placeholder="Search songs" />
        <input name="genre" aria-label="Genre" className="focus-ring rounded-md border border-line px-3 py-2" placeholder="Genre" />
        <input name="bpmMin" aria-label="BPM min" className="focus-ring rounded-md border border-line px-3 py-2" placeholder="BPM min" type="number" />
        <input name="bpmMax" aria-label="BPM max" className="focus-ring rounded-md border border-line px-3 py-2" placeholder="BPM max" type="number" />
        <input name="year" aria-label="Year" className="focus-ring rounded-md border border-line px-3 py-2" placeholder="Year" type="number" />
        <button className="focus-ring rounded-md bg-brand px-4 py-2 font-medium text-white md:col-span-6">Search</button>
      </form>
      {mutation.isError ? <ErrorState message={mutation.error.message} /> : null}
      {mutation.data ? <SongList songs={mutation.data.content} /> : null}
    </section>
  );
}
