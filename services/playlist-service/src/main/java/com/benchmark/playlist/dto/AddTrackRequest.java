package com.benchmark.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddTrackRequest(
        @NotBlank @Size(max = 255) String songId
) {
}
