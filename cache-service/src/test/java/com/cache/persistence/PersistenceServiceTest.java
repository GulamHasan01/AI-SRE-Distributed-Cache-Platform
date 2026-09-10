package com.cache.persistence;

import com.cache.config.CacheProperties;
import com.cache.model.CacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersistenceService Unit Tests")
class PersistenceServiceTest {

    @TempDir
    Path tempDir;

    private CacheProperties cacheProperties;
    private FilePersistenceService persistenceService;
    private File snapshotFile;

    @BeforeEach
    void setUp() {
        snapshotFile = tempDir.resolve("test-snapshot.json").toFile();

        cacheProperties = new CacheProperties();
        cacheProperties.getPersistence().setEnabled(true);
        cacheProperties.getPersistence().setFilePath(snapshotFile.getAbsolutePath());

        persistenceService = new FilePersistenceService(cacheProperties);
    }

    @Nested
    @DisplayName("saveSnapshot()")
    class SaveSnapshotTests {

        @Test
        @DisplayName("saves active non-expired entries to JSON file")
        void savesActiveEntries() {
            CacheEntry valid1 = new CacheEntry("k1", "v1");
            CacheEntry valid2 = new CacheEntry("k2", "v2", 3600);
            CacheEntry expired = new CacheEntry("k3", "v3", 1);
            try { Thread.sleep(1100); } catch (InterruptedException ignored) {}

            int savedCount = persistenceService.saveSnapshot(List.of(valid1, valid2, expired));

            assertThat(savedCount).isEqualTo(2);
            assertThat(snapshotFile).exists();
            assertThat(snapshotFile.length()).isGreaterThan(0);
        }

        @Test
        @DisplayName("skips saving when persistence is disabled")
        void skipsWhenDisabled() {
            cacheProperties.getPersistence().setEnabled(false);

            int savedCount = persistenceService.saveSnapshot(List.of(new CacheEntry("k1", "v1")));

            assertThat(savedCount).isZero();
            assertThat(snapshotFile).doesNotExist();
        }
    }

    @Nested
    @DisplayName("loadSnapshot()")
    class LoadSnapshotTests {

        @Test
        @DisplayName("recovers non-expired entries from saved snapshot")
        void recoversValidEntries() {
            CacheEntry entry1 = new CacheEntry("user:1", "Alice", 3600);
            CacheEntry entry2 = new CacheEntry("user:2", "Bob", 3600);
            persistenceService.saveSnapshot(List.of(entry1, entry2));

            List<CacheEntry> recovered = persistenceService.loadSnapshot();

            assertThat(recovered).hasSize(2);
            assertThat(recovered).extracting(CacheEntry::getKey).containsExactlyInAnyOrder("user:1", "user:2");
            assertThat(recovered).extracting(CacheEntry::getValue).containsExactlyInAnyOrder("Alice", "Bob");
        }

        @Test
        @DisplayName("returns empty list when snapshot file does not exist")
        void returnsEmptyWhenNoFile() {
            File nonExistent = tempDir.resolve("non-existent.json").toFile();
            cacheProperties.getPersistence().setFilePath(nonExistent.getAbsolutePath());

            List<CacheEntry> recovered = persistenceService.loadSnapshot();

            assertThat(recovered).isEmpty();
        }

        @Test
        @DisplayName("handles corrupted JSON snapshot gracefully without throwing")
        void handlesCorruptedFileGracefully() throws Exception {
            Files.writeString(snapshotFile.toPath(), "{ invalid json content }");

            List<CacheEntry> recovered = persistenceService.loadSnapshot();

            assertThat(recovered).isEmpty();
        }
    }
}
