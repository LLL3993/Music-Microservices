package com.zjsu.lyy.list_service.integration;

import com.zjsu.lyy.list_service.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class MetaClient {

	private final RestClient restClient;
	private final boolean enabled;

	public MetaClient(
			RestClient.Builder builder,
			@Value("${services.meta.base-url}") String baseUrl,
			@Value("${services.validation.enabled:true}") boolean enabled
	) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.enabled = enabled;
	}

	public void assertSongExists(String songName) {
		if (!enabled) {
			return;
		}
		try {
			restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/meta/song-name").queryParam("songName", songName).build())
					.retrieve()
					.toBodilessEntity();
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new NotFoundException("歌曲不存在");
		}
	}
}
