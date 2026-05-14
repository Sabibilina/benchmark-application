package com.benchmark.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.benchmark.recommendation.entity.UserSongInteraction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class UserSongInteractionRepositoryTest {

    @Autowired
    UserSongInteractionRepository repository;

    @Test
    void ranksUserAndGlobalPositiveSongs() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        repository.save(new UserSongInteraction(
                UUID.randomUUID(), "play.started", userId.toString(), "song-a", Instant.parse("2026-01-01T00:00:00Z"), true));
        repository.save(new UserSongInteraction(
                UUID.randomUUID(), "play.ended", userId.toString(), "song-a", Instant.parse("2026-01-01T00:01:00Z"), true));
        repository.save(new UserSongInteraction(
                UUID.randomUUID(), "play.skipped", userId.toString(), "song-c", Instant.parse("2026-01-01T00:02:00Z"), false));
        repository.save(new UserSongInteraction(
                UUID.randomUUID(), "play.started", otherUserId.toString(), "song-b", Instant.parse("2026-01-01T00:03:00Z"), true));

        List<String> userSongs = repository.findTopPositiveSongsForUser(userId.toString(), 5);
        List<String> globalSongs = repository.findGlobalPositiveSongs(5);

        assertThat(userSongs).containsExactly("song-a");
        assertThat(globalSongs).containsExactly("song-a", "song-b");
    }
}
