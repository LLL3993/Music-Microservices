package com.zjsu.lyy.list_service.integration;

import com.zjsu.lyy.list_service.exception.NotFoundException;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MetaClient {

	private final RestTemplate restTemplate;
	private final DiscoveryClient discoveryClient;
	private final String baseUrl;
	private final String serviceName;
	private final boolean enabled;

	public MetaClient(
			RestTemplateBuilder builder,
			ObjectProvider<DiscoveryClient> discoveryClientProvider,
			@Value("${services.meta.base-url:}") String baseUrl,
			@Value("${services.meta.service-name:meta-service}") String serviceName,
			@Value("${services.validation.enabled:true}") boolean enabled
	) {
		this.restTemplate = builder.build();
		this.discoveryClient = discoveryClientProvider.getIfAvailable();
		this.baseUrl = baseUrl;
		this.serviceName = serviceName;
		this.enabled = enabled;
	}

	public void assertSongExists(String songName) {
		if (!enabled) {
			return;
		}
		try {
			URI baseUri = resolveBaseUri();
			URI uri = UriComponentsBuilder.fromUri(baseUri)
					.path("/api/meta/song-name")
					.queryParam("songName", songName)
					.build()
					.toUri();

			restTemplate.getForEntity(uri, Void.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new NotFoundException("歌曲不存在");
		}
	}

	private URI resolveBaseUri() {
		if (discoveryClient != null) {
			List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
			if (!instances.isEmpty()) {
				return instances.getFirst().getUri();
			}
		}

		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("meta-service 不可用");
		}
		return URI.create(baseUrl);
	}
}
