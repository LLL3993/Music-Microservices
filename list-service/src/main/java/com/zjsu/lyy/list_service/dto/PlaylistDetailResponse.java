package com.zjsu.lyy.list_service.dto;

public record PlaylistDetailResponse(
		Long id,
		String username,
		String playlistName,
		String songName
) {
}
