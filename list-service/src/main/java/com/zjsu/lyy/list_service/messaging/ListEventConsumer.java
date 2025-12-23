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

	private final ListEventHandlerService handlerService;

	public ListEventConsumer(ListEventHandlerService handlerService) {
		this.handlerService = handlerService;
	}

	@RabbitListener(queues = RabbitMqConstants.QUEUE_LIST_USER_DELETED)
	public void onUserDeleted(UserDeletedEvent event) {
		boolean handled = handlerService.handleUserDeleted(event);
		if (handled) {
			log.info("Consumed user.deleted eventId={} username={}", event.eventId(), event.username());
		}
	}

	@RabbitListener(queues = RabbitMqConstants.QUEUE_LIST_SONG_DELETED)
	public void onSongDeleted(SongDeletedEvent event) {
		boolean handled = handlerService.handleSongDeleted(event);
		if (handled) {
			log.info("Consumed meta.song.deleted eventId={} songName={}", event.eventId(), event.songName());
		}
	}
}
