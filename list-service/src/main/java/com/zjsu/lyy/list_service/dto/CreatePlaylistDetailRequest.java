package com.zjsu.lyy.list_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlaylistDetailRequest(
		@NotBlank @Size(max = 255) String playlistName,
		@NotBlank @Size(max = 255) String songName
) {
}
