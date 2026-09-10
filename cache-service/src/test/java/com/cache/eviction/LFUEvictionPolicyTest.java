package com.cache.eviction;

import com.cache.model.CacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LFUEvictionPolicy Unit Tests")
class LFUEvictionPolicyTest {

    private LFUEvictionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LFUEvictionPolicy();
    }

    @Test
    @DisplayName("should select entry with lowest access count")
    void shouldSelectLowestAccessCount() {
        CacheEntry rarelyUsed = new CacheEntry("rare-key", "v1");
        CacheEntry frequentlyUsed = new CacheEntry("freq-key", "v2");

        frequentlyUsed.recordAccess();
        frequentlyUsed.recordAccess();
        frequentlyUsed.recordAccess();

        Optional<String> victim = policy.selectVictim(List.of(rarelyUsed, frequentlyUsed));

        assertThat(victim).isPresent().contains("rare-key");
    }

    @Test
    @DisplayName("should break tie using oldest lastAccessed when access counts are equal")
    void shouldBreakTieWithLRU() throws InterruptedException {
        CacheEntry oldest = new CacheEntry("old-key", "v1");
        Thread.sleep(5);

        CacheEntry newest = new CacheEntry("new-key", "v2");
        newest.recordAccess();
        oldest.recordAccess();

        CacheEntry entry1 = new CacheEntry("key1", "v1");
        Thread.sleep(5);
        CacheEntry entry2 = new CacheEntry("key2", "v2");

        Optional<String> victim = policy.selectVictim(List.of(entry1, entry2));

        assertThat(victim).isPresent().contains("key1");
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
        assertThat(policy.policyName()).isEqualTo("LFU");
    }
}
