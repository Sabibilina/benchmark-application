package com.musicstreaming.playlist.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderTracksRequest(
        @NotEmpty List<String> songIds
) {}
