package com.zjsu.lyy.list_service.messaging;

import java.time.Instant;

public record SongDeletedEvent(
		String eventId,
		Instant occurredAt,
		String songName
) {
}

