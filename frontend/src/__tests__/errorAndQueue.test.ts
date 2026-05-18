import axios from "axios";
import { normalizeError } from "../api/errors";
import { useQueueStore } from "../stores/queueStore";
import type { Song } from "../types/catalog";

const song: Song = {
  id: "song-1",
  title: "Song One",
  artist: "Artist",
  album: "Album",
  genre: "pop",
  bpm: 120,
  releaseYear: 2020,
  metadata: {}
};

describe("error normalization and queue store", () => {
  it("normalizes axios, Error, and unknown failures", () => {
    const serverError = new axios.AxiosError("failed", "ERR_BAD_RESPONSE", undefined, undefined, {
      status: 503,
      statusText: "Service Unavailable",
      headers: {},
      config: { headers: new axios.AxiosHeaders() },
      data: { message: "temporarily unavailable" }
    });

    const clientError = new axios.AxiosError("bad request", "ERR_BAD_REQUEST", undefined, undefined, {
      status: 400,
      statusText: "Bad Request",
      headers: {},
      config: { headers: new axios.AxiosHeaders() },
      data: "invalid request"
    });

    expect(normalizeError(serverError)).toEqual({
      status: 503,
      message: "temporarily unavailable",
      retryable: true
    });
    expect(normalizeError(clientError)).toEqual({
      status: 400,
      message: "invalid request",
      retryable: false
    });
    expect(normalizeError(new Error("plain failure"))).toEqual({
      message: "plain failure",
      retryable: false
    });
    expect(normalizeError("surprise")).toEqual({
      message: "Unexpected error",
      retryable: false
    });
  });

  it("stores queue entries and current song independently", () => {
    useQueueStore.setState({ queue: [], currentSong: null });

    useQueueStore.getState().setQueue([song]);
    expect(useQueueStore.getState().queue).toEqual([song]);
    expect(useQueueStore.getState().currentSong).toBeNull();

    useQueueStore.getState().playNow(song);
    expect(useQueueStore.getState().currentSong).toEqual(song);
  });
});
