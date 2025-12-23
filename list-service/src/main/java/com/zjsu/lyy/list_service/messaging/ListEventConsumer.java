package com.zjsu.lyy.list_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("docker")
public class ListEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(ListEventConsumer.class);

	private final ListCleanupService cleanupService;

	public ListEventConsumer(ListCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@RabbitListener(queues = RabbitMqConstants.QUEUE_LIST_USER_DELETED)
	public void onUserDeleted(UserDeletedEvent event) {
		if (event == null || event.username() == null || event.username().isBlank()) {
			return;
		}
		cleanupService.cleanupByUsername(event.username());
		log.info("Consumed user.deleted eventId={} username={}", event.eventId(), event.username());
	}

	@RabbitListener(queues = RabbitMqConstants.QUEUE_LIST_SONG_DELETED)
	public void onSongDeleted(SongDeletedEvent event) {
		if (event == null || event.songName() == null || event.songName().isBlank()) {
			return;
		}
		cleanupService.cleanupBySongName(event.songName());
		log.info("Consumed meta.song.deleted eventId={} songName={}", event.eventId(), event.songName());
	}
}

