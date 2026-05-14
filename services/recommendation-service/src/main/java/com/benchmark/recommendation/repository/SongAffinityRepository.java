package com.benchmark.recommendation.repository;

import com.benchmark.recommendation.entity.SongAffinity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongAffinityRepository extends JpaRepository<SongAffinity, Long> {

    @Query(value = """
            SELECT related_song_id
            FROM song_affinities
            WHERE source_song_id = :songId
            ORDER BY score DESC, related_song_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findRelatedSongIds(@Param("songId") String songId, @Param("limit") int limit);

    @Modifying
    @Query(value = """
            INSERT INTO song_affinities (source_song_id, related_song_id, score, updated_at)
            VALUES (:sourceSongId, :relatedSongId, 1, :updatedAt)
            ON CONFLICT (source_song_id, related_song_id)
            DO UPDATE SET score = song_affinities.score + 1, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void incrementAffinity(
            @Param("sourceSongId") String sourceSongId,
            @Param("relatedSongId") String relatedSongId,
            @Param("updatedAt") Instant updatedAt);
}
