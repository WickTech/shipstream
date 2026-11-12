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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "tracking.webhook-secret=" + TrackingServiceIntegrationTest.SECRET)
@Testcontainers
class TrackingServiceIntegrationTest {

    static final String SECRET = "integration-test-secret-not-real";

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

    private ResponseEntity<String> post(String body, String timestamp, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (timestamp != null) headers.set(HmacVerifier.TIMESTAMP_HEADER, timestamp);
        if (signature != null) headers.set(HmacVerifier.SIGNATURE_HEADER, signature);
        return rest.exchange(base() + "/tracking/ingest", HttpMethod.POST,
                             new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> postSigned(String body, Instant at) {
        String ts = Long.toString(at.getEpochSecond());
        return post(body, ts, hmacVerifier.sign(ts, body.getBytes(StandardCharsets.UTF_8)));
    }

    private String sampleBody(UUID shipmentId) throws Exception {
        return mapper.writeValueAsString(
            new IngestRequest(shipmentId, "UPS", null, "Test", Instant.now()));
    }

    @Test
    void ingestAndTimeline() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        var req = new IngestRequest(shipmentId, "FEDEX", "Chicago, IL",
                                    "Package picked up", Instant.now());

        ResponseEntity<String> resp = postSigned(mapper.writeValueAsString(req), Instant.now());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TrackingEventResponse created = mapper.readValue(resp.getBody(), TrackingEventResponse.class);
        assertThat(created.shipmentId()).isEqualTo(shipmentId);
        assertThat(created.carrierCode()).isEqualTo("FEDEX");

        // Timeline
        ResponseEntity<TrackingEventResponse[]> timeline = rest.getForEntity(
            base() + "/tracking/" + shipmentId, TrackingEventResponse[].class);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timeline.getBody()).hasSize(1);
    }

    @Test
    void unsignedRequestReturns401() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        assertThat(post(sampleBody(shipmentId), null, null).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(sampleBody(shipmentId), Long.toString(Instant.now().getEpochSecond()), null)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNothingStored(shipmentId);
    }

    @Test
    void wrongSignatureReturns401() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        String ts = Long.toString(Instant.now().getEpochSecond());
        assertThat(post(sampleBody(shipmentId), ts, "sha256=badbadbadbad").getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNothingStored(shipmentId);
    }

    @Test
    void staleTimestampReturns401() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        // Correctly signed, but 10 minutes old (default tolerance is 5 minutes).
        ResponseEntity<String> resp = postSigned(sampleBody(shipmentId), Instant.now().minusSeconds(600));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNothingStored(shipmentId);
    }

    @Test
    void futureTimestampReturns401() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        ResponseEntity<String> resp = postSigned(sampleBody(shipmentId), Instant.now().plusSeconds(600));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertNothingStored(shipmentId);
    }

    @Test
    void signedButInvalidBodyReturns400() {
        // Valid signature, but missing required fields.
        ResponseEntity<String> resp = postSigned("{\"carrierCode\":\"UPS\"}", Instant.now());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> malformed = postSigned("{not json", Instant.now());
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void assertNothingStored(UUID shipmentId) {
        ResponseEntity<TrackingEventResponse[]> timeline = rest.getForEntity(
            base() + "/tracking/" + shipmentId, TrackingEventResponse[].class);
        assertThat(timeline.getBody()).isEmpty();
    }

    @Test
    void timelineEmptyForUnknownShipment() {
        ResponseEntity<TrackingEventResponse[]> resp = rest.getForEntity(
            base() + "/tracking/" + UUID.randomUUID(), TrackingEventResponse[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }
}
