package com.zjsu.lyy.list_service.inbox;

import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("docker")
public class InboxService {

	private final InboxEventRepository inboxEventRepository;

	public InboxService(InboxEventRepository inboxEventRepository) {
		this.inboxEventRepository = inboxEventRepository;
	}

	public boolean tryMarkProcessed(String eventId, String routingKey) {
		if (eventId == null || eventId.isBlank()) {
			return true;
		}
		int inserted = inboxEventRepository.insertIgnore(eventId, routingKey, Instant.now());
		return inserted > 0;
	}
}

