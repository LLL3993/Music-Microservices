package com.zjsu.lyy.meta_service.messaging;

public final class RabbitMqConstants {

	private RabbitMqConstants() {
	}

	public static final String EXCHANGE_NAME = "music.events";
	public static final String ROUTING_KEY_SONG_DELETED = "meta.song.deleted";
}

