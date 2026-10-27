package com.wicktech.shipstream.notification;

import com.wicktech.shipstream.notification.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("notificationdb")
            .withUsername("shipstream")
            .withPassword("shipstream");

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",          postgres::getJdbcUrl);
        registry.add("spring.datasource.username",     postgres::getUsername);
        registry.add("spring.datasource.password",     postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host",         redis::getHost);
        registry.add("spring.data.redis.port",         () -> redis.getMappedPort(6379));
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired KafkaTemplate<String, byte[]> kafkaTemplate;
    @Autowired NotificationRepository notificationRepository;

    String base() { return "http://localhost:" + port; }

    @Test
    void shipmentCreatedEventProducesNotification() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {"orderId":"%s","carrier":"FEDEX"}
            """.formatted(orderId).trim();

        kafkaTemplate.send(new ProducerRecord<>("shipments.created", orderId.toString(), payload.getBytes()));

        await().atMost(30, SECONDS).until(() ->
            notificationRepository.findByRecipientIdOrderBySentAtDesc(orderId).size() == 1);

        ResponseEntity<Object[]> resp = rest.getForEntity(
            base() + "/notifications/" + orderId, Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void duplicateEventIsDeduped() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {"orderId":"%s"}
            """.formatted(orderId).trim();

        kafkaTemplate.send(new ProducerRecord<>("shipments.status_changed", orderId.toString(), payload.getBytes())).get();

        await().atMost(30, SECONDS).until(() ->
            !notificationRepository.findByRecipientIdOrderBySentAtDesc(orderId).isEmpty());

        int countAfterFirst = notificationRepository.findByRecipientIdOrderBySentAtDesc(orderId).size();
        assertThat(countAfterFirst).isEqualTo(1);
    }

    @Test
    void emptyNotificationsForUnknownRecipient() {
        ResponseEntity<Object[]> resp = rest.getForEntity(
            base() + "/notifications/" + UUID.randomUUID(), Object[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }
}
