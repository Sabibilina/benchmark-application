package com.musicstreaming.recommendation.model;

import jakarta.persistence.*;

@Entity
@Table(name = "recommendation_songs")
public class RecommendationSong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "track_id", unique = true, nullable = false)
    private String trackId;

    @Column(nullable = false, length = 500)
    private String title;

    private String artist;
    private String genre;
    private Double tempo;

    @Column(name = "year")
    private Integer year;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrackId() { return trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public Double getTempo() { return tempo; }
    public void setTempo(Double tempo) { this.tempo = tempo; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
}
