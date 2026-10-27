package com.wicktech.shipstream.order.api;

import com.wicktech.shipstream.order.domain.Order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID    orderId,
    String  customerId,
    String  items,
    long    totalCents,
    String  status,
    Instant createdAt,
    Instant updatedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
            o.getOrderId(),
            o.getCustomerId(),
            o.getItems(),
            o.getTotalCents(),
            o.getStatus().name(),
            o.getCreatedAt(),
            o.getUpdatedAt()
        );
    }
}
