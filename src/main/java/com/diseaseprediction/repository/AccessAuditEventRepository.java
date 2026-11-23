package com.diseaseprediction.repository;

import com.diseaseprediction.domain.AccessAuditEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Append-only store of {@link AccessAuditEvent}s; the database rejects updates and deletes. */
public interface AccessAuditEventRepository extends JpaRepository<AccessAuditEvent, UUID> {

    List<AccessAuditEvent> findByActorUserIdOrderByOccurredAtAsc(UUID actorUserId);
}
