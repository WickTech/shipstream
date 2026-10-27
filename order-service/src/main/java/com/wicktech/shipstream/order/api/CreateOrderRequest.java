package com.wicktech.shipstream.order.api;

public record CreateOrderRequest(
    String customerId,
    String items,       // JSON string, e.g. [{"sku":"A1","qty":2}]
    long   totalCents
) {}
