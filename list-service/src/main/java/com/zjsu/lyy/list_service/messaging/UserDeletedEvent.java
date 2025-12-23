package com.zjsu.lyy.list_service.messaging;

import java.time.Instant;

public record UserDeletedEvent(
		String eventId,
		Instant occurredAt,
		String username
) {
}

