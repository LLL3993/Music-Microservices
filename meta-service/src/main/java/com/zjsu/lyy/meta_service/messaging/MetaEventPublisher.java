package com.zjsu.lyy.meta_service.messaging;

public interface MetaEventPublisher {
	void publishSongDeleted(String songName);
}

