package com.zjsu.lyy.user_service.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!docker")
public class NoopUserEventPublisher implements UserEventPublisher {
	@Override
	public void publishUserDeleted(String username) {
	}
}

