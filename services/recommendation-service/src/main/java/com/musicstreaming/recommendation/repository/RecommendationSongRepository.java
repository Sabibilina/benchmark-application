package com.musicstreaming.recommendation.repository;

import com.musicstreaming.recommendation.model.RecommendationSong;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationSongRepository extends JpaRepository<RecommendationSong, Long> {

    @Query(value =
        "SELECT * FROM recommendation_songs " +
        "WHERE genre = :genre AND ABS(tempo - :tempo) <= :tolerance AND id <> :excludeId " +
        "ORDER BY ABS(tempo - :tempo)",
        nativeQuery = true)
    List<RecommendationSong> findSimilar(
            @Param("genre") String genre,
            @Param("tempo") double tempo,
            @Param("tolerance") double tolerance,
            @Param("excludeId") long excludeId,
            Pageable pageable);

    @Query(value =
        "SELECT * FROM recommendation_songs " +
        "WHERE genre = :genre AND id NOT IN (:excludeIds) " +
        "ORDER BY RANDOM()",
        nativeQuery = true)
    List<RecommendationSong> findByGenreExcluding(
            @Param("genre") String genre,
            @Param("excludeIds") List<Long> excludeIds,
            Pageable pageable);

    @Query(value = "SELECT * FROM recommendation_songs ORDER BY RANDOM()", nativeQuery = true)
    List<RecommendationSong> findRandom(Pageable pageable);

    @Query("SELECT s.genre FROM RecommendationSong s WHERE s.id IN :ids AND s.genre IS NOT NULL")
    List<String> findGenresByIds(@Param("ids") List<Long> ids);
}
