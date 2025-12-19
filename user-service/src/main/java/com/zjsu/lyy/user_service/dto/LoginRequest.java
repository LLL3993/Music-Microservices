package com.zjsu.lyy.user_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Size(max = 50) String username,
		@NotBlank @Size(max = 255) String password
) {
}

