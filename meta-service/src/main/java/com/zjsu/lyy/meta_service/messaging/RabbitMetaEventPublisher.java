package com.zjsu.lyy.meta_service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjsu.lyy.meta_service.outbox.OutboxService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("docker")
public class RabbitMetaEventPublisher implements MetaEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(RabbitMetaEventPublisher.class);

	private final OutboxService outboxService;
	private final ObjectMapper objectMapper;

	public RabbitMetaEventPublisher(OutboxService outboxService, ObjectMapper objectMapper) {
		this.outboxService = outboxService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void publishSongDeleted(String songName) {
		String eventId = UUID.randomUUID().toString();
		SongDeletedEvent event = new SongDeletedEvent(eventId, Instant.now(), songName);
		try {
			String payloadJson = objectMapper.writeValueAsString(event);
			outboxService.enqueue(eventId, RabbitMqConstants.EXCHANGE_NAME, RabbitMqConstants.ROUTING_KEY_SONG_DELETED, payloadJson);
			log.info("Outbox enqueued routingKey={} id={} songName={}", RabbitMqConstants.ROUTING_KEY_SONG_DELETED, eventId, songName);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to enqueue outbox event", ex);
		}
	}
}
