-- V1 already creates idx_songs_genre, idx_songs_artist, idx_songs_year,
-- idx_songs_tempo, idx_songs_artist_id, idx_songs_popularity.
-- V2 adds the two missing indexes needed for the browsing hot path:
--   title search and the (genre, title) composite used by paginated genre browsing.

CREATE INDEX IF NOT EXISTS idx_songs_title       ON songs(title);
CREATE INDEX IF NOT EXISTS idx_songs_genre_title ON songs(genre, title);
