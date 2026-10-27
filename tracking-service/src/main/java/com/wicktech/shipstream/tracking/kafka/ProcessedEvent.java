package com.wicktech.shipstream.tracking.kafka;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedEvent() {}

    public ProcessedEvent(String topic, int partition, long offset) {
        this.id = new ProcessedEventId(topic, partition, offset);
    }

    public ProcessedEventId getId() { return id; }

    @Embeddable
    public static class ProcessedEventId implements Serializable {
        @Column(name = "topic",      nullable = false, length = 128)
        private String topic;
        @Column(name = "partition",  nullable = false)
        private int partition;
        @Column(name = "offset_val", nullable = false)
        private long offsetVal;

        protected ProcessedEventId() {}

        public ProcessedEventId(String topic, int partition, long offsetVal) {
            this.topic     = topic;
            this.partition = partition;
            this.offsetVal = offsetVal;
        }
    }
}
