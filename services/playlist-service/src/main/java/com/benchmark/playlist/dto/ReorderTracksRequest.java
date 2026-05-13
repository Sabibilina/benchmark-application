package com.benchmark.playlist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReorderTracksRequest(
        @NotEmpty @Size(max = 10000) List<String> songIds
) {
}
