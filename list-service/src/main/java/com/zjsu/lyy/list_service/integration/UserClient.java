package com.zjsu.lyy.list_service.integration;

import com.zjsu.lyy.list_service.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class UserClient {

	private final RestClient restClient;
	private final boolean enabled;

	public UserClient(
			RestClient.Builder builder,
			@Value("${services.user.base-url}") String baseUrl,
			@Value("${services.validation.enabled:true}") boolean enabled
	) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.enabled = enabled;
	}

	public void assertUserExists(String username) {
		if (!enabled) {
			return;
		}
		try {
			restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/users/username").queryParam("username", username).build())
					.retrieve()
					.toBodilessEntity();
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new NotFoundException("用户不存在");
		}
	}
}
