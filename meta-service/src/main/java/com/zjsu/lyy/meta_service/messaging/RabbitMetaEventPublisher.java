package com.zjsu.lyy.meta_service.messaging;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("docker")
public class RabbitMetaEventPublisher implements MetaEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(RabbitMetaEventPublisher.class);

	private final RabbitTemplate rabbitTemplate;

	public RabbitMetaEventPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	@Override
	public void publishSongDeleted(String songName) {
		SongDeletedEvent event = new SongDeletedEvent(UUID.randomUUID().toString(), Instant.now(), songName);
		rabbitTemplate.convertAndSend(RabbitMqConstants.EXCHANGE_NAME, RabbitMqConstants.ROUTING_KEY_SONG_DELETED, event);
		log.info("Published meta.song.deleted for songName={}", songName);
	}
}

