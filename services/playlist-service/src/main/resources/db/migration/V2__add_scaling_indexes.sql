CREATE INDEX IF NOT EXISTS ix_playlists_owner_liked
    ON playlists (owner_user_id, liked_songs);

CREATE INDEX IF NOT EXISTS ix_playlists_owner_id
    ON playlists (owner_user_id, id);

CREATE INDEX IF NOT EXISTS ix_playlist_tracks_playlist_song
    ON playlist_tracks (playlist_id, song_id);
