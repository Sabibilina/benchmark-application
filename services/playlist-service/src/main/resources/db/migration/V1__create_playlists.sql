CREATE TABLE playlists (
    id UUID PRIMARY KEY,
    owner_user_id VARCHAR(255) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    liked_songs BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_playlists_owner_created
    ON playlists (owner_user_id, created_at);

CREATE TABLE playlist_tracks (
    id UUID PRIMARY KEY,
    playlist_id UUID NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    song_id VARCHAR(255) NOT NULL,
    track_position INTEGER NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_playlist_tracks_playlist_song UNIQUE (playlist_id, song_id)
);

CREATE INDEX ix_playlist_tracks_playlist_position
    ON playlist_tracks (playlist_id, track_position);
