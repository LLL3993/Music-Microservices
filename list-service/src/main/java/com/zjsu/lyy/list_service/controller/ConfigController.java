package com.zjsu.lyy.list_service.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RefreshScope
@Profile("docker")
public class ConfigController {

	private final String serviceName;
	private final String message;
	private final boolean validationEnabled;

	public ConfigController(
			@Value("${spring.application.name}") String serviceName,
			@Value("${app.runtime.message:unset}") String message,
			@Value("${services.validation.enabled:true}") boolean validationEnabled
	) {
		this.serviceName = serviceName;
		this.message = message;
		this.validationEnabled = validationEnabled;
	}

	@GetMapping("/info")
	public Map<String, Object> info() {
		return Map.of(
				"service", serviceName,
				"message", message,
				"services.validation.enabled", validationEnabled
		);
	}
}

