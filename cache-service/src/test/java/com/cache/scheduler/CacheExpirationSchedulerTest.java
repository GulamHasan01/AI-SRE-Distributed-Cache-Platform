package com.cache.scheduler;

import com.cache.store.CacheStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheExpirationScheduler Unit Tests")
class CacheExpirationSchedulerTest {

    @Mock
    private CacheStore cacheStore;

    @InjectMocks
    private CacheExpirationScheduler scheduler;

    @Test
    @DisplayName("should call removeExpired on the store during sweep")
    void shouldDelegateToStoreRemoveExpired() {
        when(cacheStore.removeExpired()).thenReturn(0);
        when(cacheStore.size()).thenReturn(100L);

        scheduler.sweepExpiredEntries();

        verify(cacheStore, times(1)).removeExpired();
    }

    @Test
    @DisplayName("should query store size for logging")
    void shouldQueryStoreSizeForLogging() {
        when(cacheStore.removeExpired()).thenReturn(5);
        when(cacheStore.size()).thenReturn(95L);

        scheduler.sweepExpiredEntries();

        verify(cacheStore, atLeast(1)).size();
    }

    @Test
    @DisplayName("should handle removeExpired returning 0 entries removed")
    void shouldHandleZeroRemovedEntries() {
        when(cacheStore.removeExpired()).thenReturn(0);
        when(cacheStore.size()).thenReturn(50L);

        org.assertj.core.api.Assertions.assertThatCode(() -> scheduler.sweepExpiredEntries())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should handle removeExpired returning multiple removed entries")
    void shouldHandleMultipleRemovedEntries() {
        when(cacheStore.removeExpired()).thenReturn(42);
        when(cacheStore.size()).thenReturn(958L);

        org.assertj.core.api.Assertions.assertThatCode(() -> scheduler.sweepExpiredEntries())
                .doesNotThrowAnyException();
        verify(cacheStore).removeExpired();
    }
}
