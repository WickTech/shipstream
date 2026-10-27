package com.wicktech.shipstream.order;

import com.wicktech.shipstream.order.api.CreateOrderRequest;
import com.wicktech.shipstream.order.api.OrderResponse;
import com.wicktech.shipstream.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "outbox.enabled=false")
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("orderdb")
            .withUsername("shipstream")
            .withPassword("shipstream");

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username",  postgres::getUsername);
        registry.add("spring.datasource.password",  postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    String base() { return "http://localhost:" + port; }

    @Test
    void createGetCancel() {
        // Create
        var req = new CreateOrderRequest("cust-1", "[{\"sku\":\"A\",\"qty\":1}]", 1999L);
        ResponseEntity<OrderResponse> created = rest.postForEntity(base() + "/orders", req, OrderResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        var id = created.getBody().orderId();
        assertThat(created.getBody().status()).isEqualTo("PENDING");

        // Get
        ResponseEntity<OrderResponse> got = rest.getForEntity(base() + "/orders/" + id, OrderResponse.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody().customerId()).isEqualTo("cust-1");

        // Cancel
        ResponseEntity<OrderResponse> cancelled = rest.exchange(
            base() + "/orders/" + id, HttpMethod.DELETE, null, OrderResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(OrderStatus.CANCELLED.name());

        // Get 404
        ResponseEntity<Void> missing = rest.getForEntity(base() + "/orders/00000000-0000-0000-0000-000000000000", Void.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelTwiceIsConflict() {
        var req = new CreateOrderRequest("cust-2", "[]", 500L);
        var id = rest.postForEntity(base() + "/orders", req, OrderResponse.class).getBody().orderId();

        rest.exchange(base() + "/orders/" + id, HttpMethod.DELETE, null, OrderResponse.class);
        ResponseEntity<Void> second = rest.exchange(base() + "/orders/" + id, HttpMethod.DELETE, null, Void.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
