package com.wicktech.shipstream.order.repository;

import com.wicktech.shipstream.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {}
