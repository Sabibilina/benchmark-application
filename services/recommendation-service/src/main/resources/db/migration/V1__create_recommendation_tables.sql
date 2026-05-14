CREATE TABLE user_song_interactions (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    song_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    positive_signal BOOLEAN NOT NULL
);

CREATE INDEX idx_user_song_interactions_user_positive
    ON user_song_interactions (user_id, positive_signal, occurred_at DESC);

CREATE INDEX idx_user_song_interactions_song_positive
    ON user_song_interactions (song_id, positive_signal, occurred_at DESC);

CREATE TABLE song_affinities (
    id BIGSERIAL PRIMARY KEY,
    source_song_id VARCHAR(255) NOT NULL,
    related_song_id VARCHAR(255) NOT NULL,
    score BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_song_affinity_pair UNIQUE (source_song_id, related_song_id)
);

CREATE INDEX idx_song_affinities_source_score
    ON song_affinities (source_song_id, score DESC, related_song_id ASC);
