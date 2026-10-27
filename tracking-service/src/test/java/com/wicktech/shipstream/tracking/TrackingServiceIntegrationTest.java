package com.wicktech.shipstream.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wicktech.shipstream.tracking.api.IngestRequest;
import com.wicktech.shipstream.tracking.api.TrackingEventResponse;
import com.wicktech.shipstream.tracking.service.HmacVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "tracking.webhook-secret=test-secret")
@Testcontainers
class TrackingServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("trackingdb")
            .withUsername("shipstream")
            .withPassword("shipstream");

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",          postgres::getJdbcUrl);
        registry.add("spring.datasource.username",     postgres::getUsername);
        registry.add("spring.datasource.password",     postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.locations",        () -> "classpath:db/migration");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired HmacVerifier hmacVerifier;
    @Autowired ObjectMapper mapper;

    String base() { return "http://localhost:" + port; }

    @Test
    void ingestAndTimeline() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        var req = new IngestRequest(shipmentId, "FEDEX", "Chicago, IL",
                                    "Package picked up", Instant.now());

        String body = mapper.writeValueAsString(req);
        String sig  = hmacVerifier.sign("test-secret", body.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", sig);

        ResponseEntity<TrackingEventResponse> resp = rest.exchange(
            base() + "/tracking/ingest",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            TrackingEventResponse.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().shipmentId()).isEqualTo(shipmentId);
        assertThat(resp.getBody().carrierCode()).isEqualTo("FEDEX");

        // Timeline
        ResponseEntity<TrackingEventResponse[]> timeline = rest.getForEntity(
            base() + "/tracking/" + shipmentId, TrackingEventResponse[].class);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timeline.getBody()).hasSize(1);
    }

    @Test
    void ingestWithBadHmacReturns401() throws Exception {
        var req  = new IngestRequest(UUID.randomUUID(), "UPS", null, "Test", Instant.now());
        String body = mapper.writeValueAsString(req);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", "sha256=badbadbadbad");

        ResponseEntity<Void> resp = rest.exchange(
            base() + "/tracking/ingest",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            Void.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void timelineEmptyForUnknownShipment() {
        ResponseEntity<TrackingEventResponse[]> resp = rest.getForEntity(
            base() + "/tracking/" + UUID.randomUUID(), TrackingEventResponse[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }
}
