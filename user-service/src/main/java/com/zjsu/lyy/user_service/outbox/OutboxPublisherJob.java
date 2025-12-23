package com.zjsu.lyy.user_service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("docker")
public class OutboxPublisherJob {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

	private final OutboxEventRepository outboxEventRepository;
	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final int batchSize;

	public OutboxPublisherJob(
			OutboxEventRepository outboxEventRepository,
			RabbitTemplate rabbitTemplate,
			ObjectMapper objectMapper,
			@Value("${outbox.publisher.batch-size:50}") int batchSize
	) {
		this.outboxEventRepository = outboxEventRepository;
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:2000}")
	@Transactional
	public void publishPending() {
		Instant now = Instant.now();
		List<OutboxEvent> pending = outboxEventRepository.findPending(now, PageRequest.of(0, batchSize));
		for (OutboxEvent event : pending) {
			try {
				Object payload = objectMapper.readValue(event.getPayloadJson(), Object.class);
				rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), payload);
				event.setStatus(OutboxStatus.SENT);
				event.setSentAt(Instant.now());
				event.setLastError(null);
				log.info("Outbox published id={} routingKey={}", event.getId(), event.getRoutingKey());
			}
			catch (Exception ex) {
				int attempts = event.getAttemptCount() + 1;
				event.setAttemptCount(attempts);
				event.setNextAttemptAt(Instant.now().plusMillis(backoffMs(attempts)));
				event.setLastError(truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
				log.warn("Outbox publish failed id={} routingKey={} attempt={}", event.getId(), event.getRoutingKey(), attempts);
			}
		}
	}

	private static long backoffMs(int attempt) {
		long base = 1000L;
		long max = 60000L;
		long pow = 1L << Math.min(10, Math.max(0, attempt - 1));
		return Math.min(max, base * pow);
	}

	private static String truncate(String value) {
		if (value == null) return null;
		if (value.length() <= 1000) return value;
		return value.substring(0, 1000);
	}
}

