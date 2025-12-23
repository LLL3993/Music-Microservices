package com.zjsu.lyy.list_service.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("docker")
public class RabbitMqDockerConfig {

	@Bean
	public DirectExchange musicEventsExchange() {
		return new DirectExchange(RabbitMqConstants.EXCHANGE_NAME, true, false);
	}

	@Bean
	public Queue listUserDeletedQueue() {
		return new Queue(RabbitMqConstants.QUEUE_LIST_USER_DELETED, true);
	}

	@Bean
	public Queue listSongDeletedQueue() {
		return new Queue(RabbitMqConstants.QUEUE_LIST_SONG_DELETED, true);
	}

	@Bean
	public Binding listUserDeletedBinding(Queue listUserDeletedQueue, DirectExchange musicEventsExchange) {
		return BindingBuilder.bind(listUserDeletedQueue)
				.to(musicEventsExchange)
				.with(RabbitMqConstants.ROUTING_KEY_USER_DELETED);
	}

	@Bean
	public Binding listSongDeletedBinding(Queue listSongDeletedQueue, DirectExchange musicEventsExchange) {
		return BindingBuilder.bind(listSongDeletedQueue)
				.to(musicEventsExchange)
				.with(RabbitMqConstants.ROUTING_KEY_SONG_DELETED);
	}

	@Bean
	public MessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}
}

