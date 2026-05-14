package com.musicstreaming.recommendation.repository;

import com.musicstreaming.recommendation.model.PlayEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PlayEventRepository extends JpaRepository<PlayEvent, Long> {

    @Query(value =
        "SELECT song_id FROM play_events " +
        "WHERE user_id = :userId AND event_type = 'play.started' AND occurred_at > :since " +
        "GROUP BY song_id ORDER BY COUNT(*) DESC LIMIT :limit",
        nativeQuery = true)
    List<String> findTopSongIdsByUser(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit);

    @Query(value =
        "SELECT song_id FROM play_events " +
        "WHERE event_type = 'play.started' " +
        "GROUP BY song_id ORDER BY COUNT(*) DESC LIMIT :limit",
        nativeQuery = true)
    List<String> findGlobalTopSongIds(@Param("limit") int limit);
}
