CREATE INDEX IF NOT EXISTS idx_user_song_interactions_positive_song_time
    ON user_song_interactions (positive_signal, song_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_song_interactions_user_song_positive
    ON user_song_interactions (user_id, song_id, positive_signal);
