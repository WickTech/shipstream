package com.wicktech.shipstream.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wicktech.shipstream.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class ShipmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventConsumer.class);

    private final NotificationService notificationService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Value("${notification.dedup-ttl-seconds:86400}")
    private long dedupTtlSeconds;

    public ShipmentEventConsumer(NotificationService notificationService,
                                  StringRedisTemplate redis,
                                  ObjectMapper mapper) {
        this.notificationService = notificationService;
        this.redis               = redis;
        this.mapper              = mapper;
    }

    @KafkaListener(
        topics = {"shipments.created", "shipments.status_changed"},
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onShipmentEvent(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        String dedupKey = "notif:dedup:%s:%d:%d".formatted(
            record.topic(), record.partition(), record.offset());

        // Valkey/Redis dedup: skip if already processed
        Boolean isNew = redis.opsForValue().setIfAbsent(
            dedupKey, "1", Duration.ofSeconds(dedupTtlSeconds));

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Duplicate event skipped: {}", dedupKey);
            ack.acknowledge();
            return;
        }

        try {
            JsonNode node       = mapper.readTree(record.value());
            UUID     orderId    = UUID.fromString(node.get("orderId").asText());
            String   eventType  = record.topic();
            String   payload    = record.value() != null ? new String(record.value()) : "{}";

            notificationService.send(orderId, eventType, payload);
            ack.acknowledge();
        } catch (Exception e) {
            // Remove dedup key so the event can be retried (DLQ will catch after max retries)
            redis.delete(dedupKey);
            log.error("Failed to process event {}: {}", dedupKey, e.getMessage());
            throw new RuntimeException("Event processing failed, will be routed to DLQ", e);
        }
    }
}
