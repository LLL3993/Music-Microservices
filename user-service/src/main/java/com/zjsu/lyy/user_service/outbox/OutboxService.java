package com.zjsu.lyy.user_service.outbox;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

	private final OutboxEventRepository outboxEventRepository;

	public OutboxService(OutboxEventRepository outboxEventRepository) {
		this.outboxEventRepository = outboxEventRepository;
	}

	@Transactional
	public void enqueue(String eventId, String exchangeName, String routingKey, String payloadJson) {
		OutboxEvent event = new OutboxEvent();
		event.setId(eventId);
		event.setExchangeName(exchangeName);
		event.setRoutingKey(routingKey);
		event.setPayloadJson(payloadJson);
		event.setStatus(OutboxStatus.PENDING);
		event.setAttemptCount(0);
		event.setCreatedAt(Instant.now());
		outboxEventRepository.save(event);
	}
}

