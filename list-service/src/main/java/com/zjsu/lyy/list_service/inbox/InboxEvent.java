package com.zjsu.lyy.list_service.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
		name = "inbox_event",
		indexes = {
				@Index(name = "idx_inbox_processed", columnList = "processed_at")
		}
)
public class InboxEvent {

	@Id
	@Column(name = "id", nullable = false, length = 36)
	private String id;

	@Column(name = "routing_key", nullable = false, length = 255)
	private String routingKey;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getRoutingKey() {
		return routingKey;
	}

	public void setRoutingKey(String routingKey) {
		this.routingKey = routingKey;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(Instant processedAt) {
		this.processedAt = processedAt;
	}
}

