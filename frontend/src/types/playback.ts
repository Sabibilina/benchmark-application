export type PlaybackStatus = "idle" | "loading" | "playing" | "paused" | "ended" | "skipped";

export interface SegmentReference {
  index: number;
  url: string;
  sizeBytes: number;
}

export interface StreamDescriptor {
  songId: string;
  descriptorType: string;
  segmentCount: number;
  segmentSizeBytes: number;
  issuedAt: string;
  segments: SegmentReference[];
  endedUrl: string;
  skippedUrl: string;
}
