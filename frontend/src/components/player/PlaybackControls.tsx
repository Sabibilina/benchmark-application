import { Pause, Play, SkipForward, Square } from "lucide-react";
import { usePlaybackStore } from "../../stores/playbackStore";

export function PlaybackControls() {
  const status = usePlaybackStore((state) => state.status);
  const pause = usePlaybackStore((state) => state.pause);
  const resume = usePlaybackStore((state) => state.resume);
  const skip = usePlaybackStore((state) => state.skip);
  const complete = usePlaybackStore((state) => state.complete);
  const playing = status === "playing";
  const paused = status === "paused";
  return (
    <div className="flex items-center gap-2">
      <button className="focus-ring rounded-md bg-brand p-2 text-white" onClick={playing ? pause : resume} disabled={!playing && !paused} title={playing ? "Pause" : "Play"}>
        {playing ? <Pause size={18} /> : <Play size={18} />}
      </button>
      <button className="focus-ring rounded-md border border-line p-2" onClick={skip} disabled={!playing && !paused} title="Skip">
        <SkipForward size={18} />
      </button>
      <button className="focus-ring rounded-md border border-line p-2" onClick={complete} disabled={!playing && !paused} title="Complete">
        <Square size={18} />
      </button>
    </div>
  );
}
