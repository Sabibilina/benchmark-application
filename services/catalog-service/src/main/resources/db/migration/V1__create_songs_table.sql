CREATE TABLE songs (
    id                BIGSERIAL         PRIMARY KEY,
    track_id          VARCHAR(20)       NOT NULL UNIQUE,
    title             VARCHAR(512)      NOT NULL,
    artist            VARCHAR(512)      NOT NULL,
    artist_id         VARCHAR(16)       NOT NULL,
    album             VARCHAR(512),
    release_date      DATE,
    year              INTEGER,
    genre             VARCHAR(50),
    duration_ms       INTEGER,
    popularity        INTEGER,
    danceability      DOUBLE PRECISION,
    energy            DOUBLE PRECISION,
    musical_key       INTEGER,
    loudness          DOUBLE PRECISION,
    mode              INTEGER,
    instrumentalness  DOUBLE PRECISION,
    tempo             DOUBLE PRECISION,
    stream_count      BIGINT,
    country           VARCHAR(100),
    explicit          BOOLEAN,
    label             VARCHAR(255)
);

CREATE INDEX idx_songs_genre     ON songs(genre);
CREATE INDEX idx_songs_year      ON songs(year);
CREATE INDEX idx_songs_tempo     ON songs(tempo);
CREATE INDEX idx_songs_artist    ON songs(artist);
CREATE INDEX idx_songs_artist_id ON songs(artist_id);
CREATE INDEX idx_songs_popularity ON songs(popularity DESC);
