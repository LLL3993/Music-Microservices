package com.zjsu.lyy.list_service.dto;

public record PlaylistResponse(
		Long id,
		String playlistName,
		String username,
		String description,
		boolean isPublic
) {
}
