package com.musicstreaming.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePlaylistRequest(
        @NotBlank @Size(max = 255) String name
) {}
