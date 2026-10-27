package com.wicktech.shipstream.tracking.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotentEventRepository
        extends JpaRepository<ProcessedEvent, ProcessedEvent.ProcessedEventId> {}
