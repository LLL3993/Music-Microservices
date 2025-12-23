package com.zjsu.lyy.list_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ListServiceApplication {

	private static final Logger log = LoggerFactory.getLogger(ListServiceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ListServiceApplication.class, args);
	}

	@EventListener(EnvironmentChangeEvent.class)
	public void onEnvironmentChange(EnvironmentChangeEvent event) {
		log.info("Nacos config changed: {}", event.getKeys());
	}

}
