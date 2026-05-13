package com.musicstreaming.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "track_id", nullable = false, unique = true, length = 20)
    private String trackId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(nullable = false, length = 512)
    private String artist;

    @Column(name = "artist_id", nullable = false, length = 16)
    private String artistId;

    @Column(length = 512)
    private String album;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column
    private Integer year;

    @Column(length = 50)
    private String genre;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column
    private Integer popularity;

    @Column
    private Double danceability;

    @Column
    private Double energy;

    @Column(name = "musical_key")
    private Integer musicalKey;

    @Column
    private Double loudness;

    @Column
    private Integer mode;

    @Column
    private Double instrumentalness;

    @Column
    private Double tempo;

    @Column(name = "stream_count")
    private Long streamCount;

    @Column(length = 100)
    private String country;

    @Column
    private Boolean explicit;

    @Column(length = 255)
    private String label;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrackId() { return trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }

    public Integer getPopularity() { return popularity; }
    public void setPopularity(Integer popularity) { this.popularity = popularity; }

    public Double getDanceability() { return danceability; }
    public void setDanceability(Double danceability) { this.danceability = danceability; }

    public Double getEnergy() { return energy; }
    public void setEnergy(Double energy) { this.energy = energy; }

    public Integer getMusicalKey() { return musicalKey; }
    public void setMusicalKey(Integer musicalKey) { this.musicalKey = musicalKey; }

    public Double getLoudness() { return loudness; }
    public void setLoudness(Double loudness) { this.loudness = loudness; }

    public Integer getMode() { return mode; }
    public void setMode(Integer mode) { this.mode = mode; }

    public Double getInstrumentalness() { return instrumentalness; }
    public void setInstrumentalness(Double instrumentalness) { this.instrumentalness = instrumentalness; }

    public Double getTempo() { return tempo; }
    public void setTempo(Double tempo) { this.tempo = tempo; }

    public Long getStreamCount() { return streamCount; }
    public void setStreamCount(Long streamCount) { this.streamCount = streamCount; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Boolean getExplicit() { return explicit; }
    public void setExplicit(Boolean explicit) { this.explicit = explicit; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
