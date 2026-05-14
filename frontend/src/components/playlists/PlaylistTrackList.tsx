import { DndContext, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import type { PlaylistTrack } from "../../types/playlist";

export function PlaylistTrackList({ tracks, onReorder }: { tracks: PlaylistTrack[]; onReorder: (activeSongId: string, overSongId: string) => void }) {
  const sorted = [...tracks].sort((a, b) => a.position - b.position);
  const handleDragEnd = (event: DragEndEvent) => {
    if (event.over && event.active.id !== event.over.id) {
      onReorder(String(event.active.id), String(event.over.id));
    }
  };
  return (
    <DndContext onDragEnd={handleDragEnd}>
      <SortableContext items={sorted.map((track) => track.songId)} strategy={verticalListSortingStrategy}>
        <div className="space-y-2">
          {sorted.map((track) => <SortableTrack key={track.songId} track={track} />)}
        </div>
      </SortableContext>
    </DndContext>
  );
}

function SortableTrack({ track }: { track: PlaylistTrack }) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({ id: track.songId });
  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className="flex items-center gap-3 rounded-md border border-line bg-white p-3"
    >
      <button className="focus-ring rounded-md p-1 text-neutral-500" {...attributes} {...listeners} aria-label={`Reorder ${track.songId}`}>
        <GripVertical size={18} />
      </button>
      <div>
        <p className="text-sm font-medium">Song {track.songId}</p>
        <p className="text-xs text-neutral-500">Position {track.position + 1}</p>
      </div>
    </div>
  );
}
