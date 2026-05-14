CREATE TABLE play_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(255) NOT NULL,
    song_id     VARCHAR(255) NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    occurred_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_play_events_user ON play_events (user_id, occurred_at DESC);
CREATE INDEX idx_play_events_song ON play_events (song_id);
