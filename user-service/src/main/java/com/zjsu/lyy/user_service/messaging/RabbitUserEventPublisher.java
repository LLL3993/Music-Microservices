package com.zjsu.lyy.user_service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjsu.lyy.user_service.outbox.OutboxService;
import com.zjsu.lyy.user_service.outbox.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("docker")
public class RabbitUserEventPublisher implements UserEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(RabbitUserEventPublisher.class);

	private final OutboxService outboxService;
	private final ObjectMapper objectMapper;

	public RabbitUserEventPublisher(OutboxService outboxService, ObjectMapper objectMapper) {
		this.outboxService = outboxService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void publishUserDeleted(String username) {
		String eventId = UUID.randomUUID().toString();
		UserDeletedEvent event = new UserDeletedEvent(eventId, Instant.now(), username);
		try {
			String payloadJson = objectMapper.writeValueAsString(event);
			outboxService.enqueue(eventId, RabbitMqConstants.EXCHANGE_NAME, RabbitMqConstants.ROUTING_KEY_USER_DELETED, payloadJson);
			log.info("Outbox enqueued routingKey={} id={} username={}", RabbitMqConstants.ROUTING_KEY_USER_DELETED, eventId, username);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to enqueue outbox event", ex);
		}
	}
}
