package com.zjsu.lyy.user_service.dto;

public record AuthResponse(
		String token,
		UserResponse user
) {
}

