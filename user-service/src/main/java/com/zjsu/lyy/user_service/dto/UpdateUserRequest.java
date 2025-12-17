package com.zjsu.lyy.user_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
		@NotBlank @Size(max = 50) String username,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(max = 255) String password,
		@NotNull Boolean isAdmin
) {
}
