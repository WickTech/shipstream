package com.wicktech.shipstream.shipment;

import com.wicktech.shipstream.shipment.api.ShipmentResponse;
import com.wicktech.shipstream.shipment.domain.ShipmentStatus;
import com.wicktech.shipstream.shipment.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {"outbox.enabled=false", "spring.kafka.consumer.auto-offset-reset=earliest"})
@Testcontainers
class ShipmentServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("shipmentdb")
            .withUsername("shipstream")
            .withPassword("shipstream");

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",         postgres::getJdbcUrl);
        registry.add("spring.datasource.username",    postgres::getUsername);
        registry.add("spring.datasource.password",    postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.locations",       () -> "classpath:db/migration");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ShipmentService shipmentService;

    String base() { return "http://localhost:" + port; }

    @Test
    void createAndGetShipment() {
        UUID orderId = UUID.randomUUID();

        // Direct service call — simulates what the Kafka consumer does
        var shipment = shipmentService.createForOrder(orderId);
        assertThat(shipment.getOrderId()).isEqualTo(orderId);
        assertThat(shipment.getCarrier()).isEqualTo("FEDEX");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);

        // REST GET
        ResponseEntity<ShipmentResponse> resp =
            rest.getForEntity(base() + "/shipments/" + orderId, ShipmentResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().shipmentId()).isEqualTo(shipment.getShipmentId());
    }

    @Test
    void createIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        var s1 = shipmentService.createForOrder(orderId);
        var s2 = shipmentService.createForOrder(orderId);
        assertThat(s1.getShipmentId()).isEqualTo(s2.getShipmentId());
    }

    @Test
    void getUnknownOrderReturns404() {
        ResponseEntity<Void> resp =
            rest.getForEntity(base() + "/shipments/" + UUID.randomUUID(), Void.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateStatus() {
        UUID orderId  = UUID.randomUUID();
        var shipment  = shipmentService.createForOrder(orderId);
        var updated   = shipmentService.updateStatus(shipment.getShipmentId(), ShipmentStatus.IN_TRANSIT);
        assertThat(updated.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }
}
