package com.zjsu.lyy.meta_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MetaServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MetaServiceApplication.class, args);
	}

}
