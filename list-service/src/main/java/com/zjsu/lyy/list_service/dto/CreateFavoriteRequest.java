package com.zjsu.lyy.list_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFavoriteRequest(
		@NotBlank @Size(max = 50) String username,
		@NotBlank @Size(max = 255) String songName
) {
}
