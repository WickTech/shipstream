package com.wicktech.shipstream.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiGatewayRoutingTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse().withStatus(200).withBody("{\"id\":\"test\"}").withHeader("Content-Type", "application/json")));
        wireMock.stubFor(get(urlPathMatching("/shipments/.*"))
            .willReturn(aResponse().withStatus(200).withBody("{\"orderId\":\"test\"}").withHeader("Content-Type", "application/json")));
        wireMock.stubFor(get(urlPathMatching("/tracking/.*"))
            .willReturn(aResponse().withStatus(200).withBody("[]").withHeader("Content-Type", "application/json")));
        wireMock.stubFor(get(urlPathMatching("/notifications/.*"))
            .willReturn(aResponse().withStatus(200).withBody("[]").withHeader("Content-Type", "application/json")));
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("ORDER_SERVICE_URL",        () -> "http://localhost:" + wireMock.port());
        registry.add("SHIPMENT_SERVICE_URL",     () -> "http://localhost:" + wireMock.port());
        registry.add("TRACKING_SERVICE_URL",     () -> "http://localhost:" + wireMock.port());
        registry.add("NOTIFICATION_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    WebTestClient webTestClient;

    @Test
    void ordersRouteProxies() {
        webTestClient.get().uri("/api/orders/123")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void shipmentsRouteProxies() {
        webTestClient.get().uri("/api/shipments/123")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void trackingRouteProxies() {
        webTestClient.get().uri("/api/tracking/123")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void notificationsRouteProxies() {
        webTestClient.get().uri("/api/notifications/123")
            .exchange()
            .expectStatus().isOk();
    }
}
