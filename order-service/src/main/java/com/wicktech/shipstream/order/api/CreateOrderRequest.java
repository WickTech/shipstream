package com.wicktech.shipstream.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
    @NotBlank @Size(max = 64)     String customerId,
    @NotBlank @Size(max = 65_536) String items,       // JSON string, e.g. [{"sku":"A1","qty":2}]
    @PositiveOrZero               long   totalCents
) {}
