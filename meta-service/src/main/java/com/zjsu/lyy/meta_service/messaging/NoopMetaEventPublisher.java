package com.zjsu.lyy.meta_service.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!docker")
public class NoopMetaEventPublisher implements MetaEventPublisher {
	@Override
	public void publishSongDeleted(String songName) {
	}
}

