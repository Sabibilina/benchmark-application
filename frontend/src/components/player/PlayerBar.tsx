import { PlaybackControls } from "./PlaybackControls";
import { usePlaybackStore } from "../../stores/playbackStore";
import { useQueueStore } from "../../stores/queueStore";

export function PlayerBar() {
  const song = useQueueStore((state) => state.currentSong);
  const status = usePlaybackStore((state) => state.status);
  const descriptor = usePlaybackStore((state) => state.descriptor);
  return (
    <footer className="fixed inset-x-0 bottom-0 border-t border-line bg-white px-4 py-3 shadow-lg">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{song?.title ?? "Nothing playing"}</p>
          <p className="truncate text-xs text-neutral-500">{song?.artist ?? "Choose a track to start streaming"}</p>
        </div>
        <div className="hidden min-w-0 flex-1 items-center gap-3 md:flex">
          <div className="h-2 flex-1 rounded-full bg-neutral-200">
            <div className="h-2 w-1/3 rounded-full bg-accent" />
          </div>
          <span className="w-28 text-xs text-neutral-500">{descriptor ? `${descriptor.segmentCount} segments` : status}</span>
        </div>
        <PlaybackControls />
      </div>
    </footer>
  );
}
