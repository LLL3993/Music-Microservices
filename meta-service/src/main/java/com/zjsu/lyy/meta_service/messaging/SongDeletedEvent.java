package com.zjsu.lyy.meta_service.messaging;

import java.time.Instant;

public record SongDeletedEvent(
		String eventId,
		Instant occurredAt,
		String songName
) {
}

