package com.zjsu.lyy.user_service.messaging;

public final class RabbitMqConstants {

	private RabbitMqConstants() {
	}

	public static final String EXCHANGE_NAME = "music.events";
	public static final String ROUTING_KEY_USER_DELETED = "user.deleted";
}

