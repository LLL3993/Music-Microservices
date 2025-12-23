package com.zjsu.lyy.meta_service.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

	@Query(
			"select e from OutboxEvent e "
					+ "where e.status = com.zjsu.lyy.meta_service.outbox.OutboxStatus.PENDING "
					+ "and (e.nextAttemptAt is null or e.nextAttemptAt <= :now) "
					+ "order by e.createdAt asc"
	)
	List<OutboxEvent> findPending(@Param("now") Instant now, Pageable pageable);
}

