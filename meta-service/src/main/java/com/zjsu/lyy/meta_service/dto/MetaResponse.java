package com.zjsu.lyy.meta_service.dto;

public record MetaResponse(
		Long id,
		String songName,
		String artist,
		boolean deleted
) {
}
