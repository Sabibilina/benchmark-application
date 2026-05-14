import { useAuthStore } from "../features/auth/authStore";
import { usePlaybackStore } from "../stores/playbackStore";

describe("playback store", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: "test-token", user: { id: "user-1", email: "test@example.com" } });
    usePlaybackStore.getState().reset();
  });

  it("moves through required playback states", async () => {
    await usePlaybackStore.getState().start("song-1");
    expect(usePlaybackStore.getState().status).toBe("playing");
    usePlaybackStore.getState().pause();
    expect(usePlaybackStore.getState().status).toBe("paused");
    usePlaybackStore.getState().resume();
    expect(usePlaybackStore.getState().status).toBe("playing");
    await usePlaybackStore.getState().complete();
    expect(usePlaybackStore.getState().status).toBe("ended");
    await usePlaybackStore.getState().start("song-1");
    await usePlaybackStore.getState().skip();
    expect(usePlaybackStore.getState().status).toBe("skipped");
  });
});
