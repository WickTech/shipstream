package com.wicktech.shipstream.order.kafka;

import com.wicktech.shipstream.order.outbox.OutboxEvent;
import com.wicktech.shipstream.order.outbox.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void poll() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(topicFor(event.getEventType()),
                                   event.getAggregateId(),
                                   event.getPayload().getBytes())
                             .get();
                event.markPublished();
                outboxRepository.save(event);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}: {}", event.getEventId(), ex.getMessage());
            }
        }
    }

    private static String topicFor(String eventType) {
        return switch (eventType) {
            case "OrderCreated"   -> "orders.created";
            case "OrderCancelled" -> "orders.cancelled";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
