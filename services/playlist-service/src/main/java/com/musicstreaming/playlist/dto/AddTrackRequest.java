package com.musicstreaming.playlist.dto;

import jakarta.validation.constraints.NotBlank;

public record AddTrackRequest(
        @NotBlank String songId
) {}
