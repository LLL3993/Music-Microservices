package com.zjsu.lyy.meta_service.messaging;

import org.springframework.amqp.core.DirectExchange;
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
	public MessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}
}

