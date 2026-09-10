package com.cache.eviction;

import com.cache.model.CacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FIFOEvictionPolicy Unit Tests")
class FIFOEvictionPolicyTest {

    private FIFOEvictionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new FIFOEvictionPolicy();
    }

    @Test
    @DisplayName("should select entry created first regardless of recent access")
    void shouldSelectFirstInserted() throws InterruptedException {
        CacheEntry first = new CacheEntry("first-key", "v1");
        Thread.sleep(5);
        CacheEntry second = new CacheEntry("second-key", "v2");

        first.recordAccess();
        first.recordAccess();
        first.recordAccess();

        Optional<String> victim = policy.selectVictim(List.of(first, second));

        assertThat(victim).isPresent().contains("first-key");
    }

    @Test
    @DisplayName("should return empty Optional for empty collection")
    void shouldReturnEmptyForEmptyCollection() {
        assertThat(policy.selectVictim(List.of())).isEmpty();
    }

    @Test
    @DisplayName("should return empty Optional for null collection")
    void shouldReturnEmptyForNullCollection() {
        assertThat(policy.selectVictim(null)).isEmpty();
    }

    @Test
    @DisplayName("should report correct policy name")
    void shouldReportPolicyName() {
        assertThat(policy.policyName()).isEqualTo("FIFO");
    }
}
