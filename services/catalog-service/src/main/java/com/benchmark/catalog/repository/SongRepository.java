package com.benchmark.catalog.repository;

import com.benchmark.catalog.entity.Song;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SongRepository extends JpaRepository<Song, String> {

    @Query("select song.id from Song song where song.id in :ids")
    Set<String> findExistingIds(Collection<String> ids);
}
