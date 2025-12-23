package com.zjsu.lyy.meta_service.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
		name = "outbox_event",
		indexes = {
				@Index(name = "idx_outbox_status_next", columnList = "status,next_attempt_at"),
				@Index(name = "idx_outbox_created", columnList = "created_at")
		}
)
public class OutboxEvent {

	@Id
	@Column(name = "id", nullable = false, length = 36)
	private String id;

	@Column(name = "exchange_name", nullable = false, length = 255)
	private String exchangeName;

	@Column(name = "routing_key", nullable = false, length = 255)
	private String routingKey;

	@Lob
	@Column(name = "payload_json", nullable = false)
	private String payloadJson;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private OutboxStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at")
	private Instant nextAttemptAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getExchangeName() {
		return exchangeName;
	}

	public void setExchangeName(String exchangeName) {
		this.exchangeName = exchangeName;
	}

	public String getRoutingKey() {
		return routingKey;
	}

	public void setRoutingKey(String routingKey) {
		this.routingKey = routingKey;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public void setPayloadJson(String payloadJson) {
		this.payloadJson = payloadJson;
	}

	public OutboxStatus getStatus() {
		return status;
	}

	public void setStatus(OutboxStatus status) {
		this.status = status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public void setNextAttemptAt(Instant nextAttemptAt) {
		this.nextAttemptAt = nextAttemptAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public void setSentAt(Instant sentAt) {
		this.sentAt = sentAt;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}
}

