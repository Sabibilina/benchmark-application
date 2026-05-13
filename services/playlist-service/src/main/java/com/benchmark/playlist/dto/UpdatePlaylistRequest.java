package com.benchmark.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePlaylistRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description
) {
}
