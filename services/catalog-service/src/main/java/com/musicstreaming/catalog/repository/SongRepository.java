package com.musicstreaming.catalog.repository;

import com.musicstreaming.catalog.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {
    List<Song> findTop10ByArtistIdOrderByPopularityDesc(String artistId);
}
