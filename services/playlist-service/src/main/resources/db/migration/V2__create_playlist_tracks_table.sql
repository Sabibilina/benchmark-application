CREATE TABLE playlist_tracks (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    playlist_id UUID         NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    song_id     VARCHAR(255) NOT NULL,
    position    INTEGER      NOT NULL,
    added_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_playlist_tracks_unique_song ON playlist_tracks (playlist_id, song_id);
CREATE INDEX idx_playlist_tracks_order ON playlist_tracks (playlist_id, position);
