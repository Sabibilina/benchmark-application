CREATE TABLE songs (
    id VARCHAR(120) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    artist VARCHAR(500) NOT NULL,
    album VARCHAR(500),
    genre VARCHAR(200),
    bpm NUMERIC(8, 3),
    release_year INTEGER,
    release_date DATE,
    popularity INTEGER,
    duration_ms INTEGER,
    metadata TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_songs_title ON songs (title);
CREATE INDEX idx_songs_artist ON songs (artist);
CREATE INDEX idx_songs_genre ON songs (genre);
CREATE INDEX idx_songs_release_year ON songs (release_year);
