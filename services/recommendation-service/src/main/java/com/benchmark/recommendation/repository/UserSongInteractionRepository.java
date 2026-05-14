package com.benchmark.recommendation.repository;

import com.benchmark.recommendation.entity.UserSongInteraction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSongInteractionRepository extends JpaRepository<UserSongInteraction, Long> {

    boolean existsByEventId(UUID eventId);

    @Query(value = """
            SELECT song_id
            FROM user_song_interactions
            WHERE user_id = :userId
              AND positive_signal = true
            GROUP BY song_id
            ORDER BY COUNT(*) DESC, MAX(occurred_at) DESC, song_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findTopPositiveSongsForUser(@Param("userId") String userId, @Param("limit") int limit);

    @Query(value = """
            SELECT song_id
            FROM user_song_interactions
            WHERE positive_signal = true
            GROUP BY song_id
            ORDER BY COUNT(*) DESC, MAX(occurred_at) DESC, song_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findGlobalPositiveSongs(@Param("limit") int limit);

    @Query(value = """
            SELECT DISTINCT song_id
            FROM user_song_interactions
            WHERE user_id = :userId
              AND positive_signal = true
              AND song_id <> :songId
            ORDER BY song_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findOtherPositiveSongsForUser(
            @Param("userId") String userId,
            @Param("songId") String songId,
            @Param("limit") int limit);
}
