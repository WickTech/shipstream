package com.wicktech.shipstream.order.service;

import com.wicktech.shipstream.order.api.CreateOrderRequest;
import com.wicktech.shipstream.order.domain.Order;
import com.wicktech.shipstream.order.outbox.OutboxEvent;
import com.wicktech.shipstream.order.outbox.OutboxRepository;
import com.wicktech.shipstream.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    public OrderService(OrderRepository orderRepository, OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest req) {
        Order order = new Order(req.customerId(), req.items(), req.totalCents());
        order = orderRepository.save(order);

        OutboxEvent evt = new OutboxEvent(
            "Order",
            order.getOrderId().toString(),
            "OrderCreated",
            orderCreatedPayload(order)
        );
        outboxRepository.save(evt);

        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = getOrder(orderId);
        order.cancel();
        order = orderRepository.save(order);

        OutboxEvent evt = new OutboxEvent(
            "Order",
            order.getOrderId().toString(),
            "OrderCancelled",
            orderCancelledPayload(order)
        );
        outboxRepository.save(evt);

        return order;
    }

    // ── payload builders (plain JSON, schema-compatible with Avro record) ────

    private static String orderCreatedPayload(Order o) {
        return """
            {"orderId":"%s","customerId":"%s","items":%s,"totalCents":%d,"createdAt":"%s"}
            """.formatted(o.getOrderId(), o.getCustomerId(), o.getItems(),
                          o.getTotalCents(), o.getCreatedAt()).strip();
    }

    private static String orderCancelledPayload(Order o) {
        return """
            {"orderId":"%s","customerId":"%s","cancelledAt":"%s"}
            """.formatted(o.getOrderId(), o.getCustomerId(), o.getUpdatedAt()).strip();
    }
}
