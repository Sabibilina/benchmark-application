CREATE TABLE playlists (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id        VARCHAR(255) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    is_liked_songs BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_playlists_user_id ON playlists (user_id);
CREATE UNIQUE INDEX idx_playlists_user_name ON playlists (user_id, name);
