package com.zjsu.lyy.gateway_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayServiceApplication {

	private static final Logger log = LoggerFactory.getLogger(GatewayServiceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

	@EventListener(EnvironmentChangeEvent.class)
	public void onEnvironmentChange(EnvironmentChangeEvent event) {
		log.info("Nacos config changed: {}", event.getKeys());
	}

}
