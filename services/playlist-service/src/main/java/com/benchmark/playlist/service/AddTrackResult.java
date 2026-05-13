package com.benchmark.playlist.service;

import com.benchmark.playlist.dto.PlaylistTrackResponse;

public record AddTrackResult(PlaylistTrackResponse track, boolean created) {
}
