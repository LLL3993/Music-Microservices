package com.zjsu.lyy.meta_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMetaRequest(
		@NotBlank @Size(max = 255) String songName,
		@NotBlank @Size(max = 255) String artist
) {
}
