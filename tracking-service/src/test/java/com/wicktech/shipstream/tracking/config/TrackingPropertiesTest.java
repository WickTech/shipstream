package com.wicktech.shipstream.tracking.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Startup must fail closed when the webhook secret is missing or weak. */
class TrackingPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropsConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TrackingProperties.class)
    static class PropsConfig {}

    @Test
    void startupFailsWithoutSecret() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("webhookSecret")
                .hasMessageContaining("TRACKING_WEBHOOK_SECRET");
        });
    }

    @Test
    void startupFailsWithBlankSecret() {
        runner.withPropertyValues("tracking.webhook-secret=   ")
            .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void startupFailsWithShortSecret() {
        runner.withPropertyValues("tracking.webhook-secret=short")
            .run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("at least 16");
            });
    }

    @Test
    void startupFailsWithZeroTolerance() {
        runner.withPropertyValues("tracking.webhook-secret=0123456789abcdef", "tracking.webhook-tolerance=0s")
            .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void startsWithSecretAndDefaultsToleranceToFiveMinutes() {
        runner.withPropertyValues("tracking.webhook-secret=0123456789abcdef")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                TrackingProperties props = ctx.getBean(TrackingProperties.class);
                assertThat(props.webhookSecret()).isEqualTo("0123456789abcdef");
                assertThat(props.webhookTolerance()).isEqualTo(Duration.ofMinutes(5));
                assertThat(props.toString()).doesNotContain("0123456789abcdef");
            });
    }
}
