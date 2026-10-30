package com.hify.modules.model.api.enums;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康状态由 failCount + lastSuccessAt 四象限派生。
 */
class ProviderHealthStatusTest {

    private final LocalDateTime someTime = LocalDateTime.of(2026, 10, 13, 10, 0);

    @Test
    void neverTestedIsUnknown() {
        assertThat(ProviderHealthStatus.derive(0, null)).isEqualTo(ProviderHealthStatus.UNKNOWN);
        assertThat(ProviderHealthStatus.derive(null, null)).isEqualTo(ProviderHealthStatus.UNKNOWN);
    }

    @Test
    void lastTestSucceededIsUp() {
        assertThat(ProviderHealthStatus.derive(0, someTime)).isEqualTo(ProviderHealthStatus.UP);
    }

    @Test
    void failingAfterSuccessIsDegraded() {
        assertThat(ProviderHealthStatus.derive(2, someTime)).isEqualTo(ProviderHealthStatus.DEGRADED);
    }

    @Test
    void failingWithoutEverSucceededIsDown() {
        assertThat(ProviderHealthStatus.derive(3, null)).isEqualTo(ProviderHealthStatus.DOWN);
    }
}
