CREATE INDEX IF NOT EXISTS idx_songs_bpm
    ON songs (bpm);

CREATE INDEX IF NOT EXISTS idx_songs_genre_year_bpm
    ON songs (genre, release_year, bpm);

CREATE INDEX IF NOT EXISTS idx_songs_title_id
    ON songs (title, id);
