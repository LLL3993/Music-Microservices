package com.zjsu.lyy.user_service.messaging;

public interface UserEventPublisher {
	void publishUserDeleted(String username);
}

