package com.zjsu.lyy.list_service.inbox;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface InboxEventRepository extends JpaRepository<InboxEvent, String> {

	@Modifying
	@Transactional
	@Query(
			value = "insert ignore into inbox_event (id, routing_key, processed_at) values (:id, :routingKey, :processedAt)",
			nativeQuery = true
	)
	int insertIgnore(
			@Param("id") String id,
			@Param("routingKey") String routingKey,
			@Param("processedAt") Instant processedAt
	);
}

