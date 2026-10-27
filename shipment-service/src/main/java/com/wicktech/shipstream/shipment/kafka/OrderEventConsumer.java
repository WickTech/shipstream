package com.wicktech.shipstream.shipment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wicktech.shipstream.shipment.service.ShipmentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ShipmentService shipmentService;
    private final IdempotentEventRepository processedRepo;
    private final ObjectMapper mapper;

    public OrderEventConsumer(ShipmentService shipmentService,
                              IdempotentEventRepository processedRepo,
                              ObjectMapper mapper) {
        this.shipmentService = shipmentService;
        this.processedRepo   = processedRepo;
        this.mapper          = mapper;
    }

    @KafkaListener(topics = "orders.created", groupId = "shipment-service")
    @Transactional
    public void onOrderCreated(ConsumerRecord<String, byte[]> record) {
        var key = new ProcessedEvent.ProcessedEventId(record.topic(), record.partition(), record.offset());
        if (processedRepo.existsById(key)) {
            log.debug("Duplicate event skipped: {}/{}/{}", record.topic(), record.partition(), record.offset());
            return;
        }

        try {
            JsonNode node    = mapper.readTree(record.value());
            UUID orderId     = UUID.fromString(node.get("orderId").asText());
            shipmentService.createForOrder(orderId);
            processedRepo.save(new ProcessedEvent(record.topic(), record.partition(), record.offset()));
        } catch (Exception ex) {
            log.error("Failed processing orders.created offset {}: {}", record.offset(), ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
