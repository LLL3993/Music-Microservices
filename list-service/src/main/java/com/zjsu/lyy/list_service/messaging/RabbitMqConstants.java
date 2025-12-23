package com.zjsu.lyy.list_service.messaging;

public final class RabbitMqConstants {

	private RabbitMqConstants() {
	}

	public static final String EXCHANGE_NAME = "music.events";

	public static final String ROUTING_KEY_USER_DELETED = "user.deleted";
	public static final String ROUTING_KEY_SONG_DELETED = "meta.song.deleted";

	public static final String QUEUE_LIST_USER_DELETED = "list.user.deleted";
	public static final String QUEUE_LIST_SONG_DELETED = "list.song.deleted";
}

