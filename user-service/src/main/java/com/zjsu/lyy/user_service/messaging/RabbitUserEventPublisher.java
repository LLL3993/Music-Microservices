package com.zjsu.lyy.user_service.messaging;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("docker")
public class RabbitUserEventPublisher implements UserEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(RabbitUserEventPublisher.class);

	private final RabbitTemplate rabbitTemplate;

	public RabbitUserEventPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	@Override
	public void publishUserDeleted(String username) {
		UserDeletedEvent event = new UserDeletedEvent(UUID.randomUUID().toString(), Instant.now(), username);
		rabbitTemplate.convertAndSend(RabbitMqConstants.EXCHANGE_NAME, RabbitMqConstants.ROUTING_KEY_USER_DELETED, event);
		log.info("Published user.deleted for username={}", username);
	}
}

