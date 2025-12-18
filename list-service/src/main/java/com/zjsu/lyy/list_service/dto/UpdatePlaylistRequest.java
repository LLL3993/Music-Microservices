package com.zjsu.lyy.list_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePlaylistRequest(
		@NotBlank @Size(max = 255) String playlistName,
		@Size(max = 255) String description,
		@NotNull Boolean isPublic
) {
}
