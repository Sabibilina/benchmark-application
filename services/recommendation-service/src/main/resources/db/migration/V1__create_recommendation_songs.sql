CREATE TABLE recommendation_songs (
    id      BIGSERIAL PRIMARY KEY,
    track_id VARCHAR(255) UNIQUE NOT NULL,
    title    VARCHAR(500) NOT NULL,
    artist   VARCHAR(255),
    genre    VARCHAR(100),
    tempo    DOUBLE PRECISION,
    year     INTEGER
);

CREATE INDEX idx_rec_songs_genre       ON recommendation_songs (genre);
CREATE INDEX idx_rec_songs_genre_tempo ON recommendation_songs (genre, tempo);
