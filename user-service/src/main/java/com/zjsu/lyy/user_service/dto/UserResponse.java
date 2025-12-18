package com.zjsu.lyy.user_service.dto;

public record UserResponse(
		Long id,
		String username,
		String email,
		boolean isAdmin
) {
}
