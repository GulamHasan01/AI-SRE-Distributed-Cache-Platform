package com.cache.persistence;

import com.cache.config.CacheProperties;
import com.cache.model.CacheEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class FilePersistenceService implements PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(FilePersistenceService.class);

    private final CacheProperties cacheProperties;
    private final ObjectMapper objectMapper;

    public FilePersistenceService(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public synchronized int saveSnapshot(Collection<CacheEntry> entries) {
        if (!cacheProperties.getPersistence().isEnabled()) {
            log.debug("Persistence is disabled in configuration. Skipping snapshot.");
            return 0;
        }

        if (entries == null) {
            entries = List.of();
        }

        List<CacheSnapshot.SnapshotEntry> validSnapshots = entries.stream()
                .filter(e -> !e.isExpired())
                .map(e -> new CacheSnapshot.SnapshotEntry(
                        e.getKey(), e.getValue(), e.getCreatedAt(), e.getTtlSeconds()))
                .toList();

        CacheSnapshot snapshot = new CacheSnapshot(
                cacheProperties.getNode().getId(),
                Instant.now(),
                validSnapshots
        );

        String filePathStr = cacheProperties.getPersistence().getFilePath();
        Path targetPath = Paths.get(filePathStr);
        Path tempPath = Paths.get(filePathStr + ".tmp");

        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), snapshot);

            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicEx) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Saved cache snapshot to '{}': {} entries persisted", targetPath.toAbsolutePath(), validSnapshots.size());
            return validSnapshots.size();

        } catch (IOException e) {
            log.error("Failed to save cache snapshot to '{}': {}", filePathStr, e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<CacheEntry> loadSnapshot() {
        if (!cacheProperties.getPersistence().isEnabled()) {
            log.info("Persistence is disabled in configuration. Starting with clean cache.");
            return List.of();
        }

        String filePathStr = cacheProperties.getPersistence().getFilePath();
        File snapshotFile = new File(filePathStr);

        if (!snapshotFile.exists() || snapshotFile.length() == 0) {
            log.info("No existing snapshot file found at '{}'. Starting empty cache.", snapshotFile.getAbsolutePath());
            return List.of();
        }

        try {
            CacheSnapshot snapshot = objectMapper.readValue(snapshotFile, CacheSnapshot.class);
            if (snapshot == null || snapshot.entries() == null) {
                log.warn("Corrupted or empty snapshot file at '{}'", filePathStr);
                return List.of();
            }

            List<CacheEntry> recovered = new ArrayList<>();
            for (CacheSnapshot.SnapshotEntry se : snapshot.entries()) {
                CacheEntry entry = new CacheEntry(se.key(), se.value(), se.ttlSeconds());
                if (!entry.isExpired()) {
                    recovered.add(entry);
                }
            }

            log.info("Loaded snapshot from '{}' (created at {} by node '{}'): recovered {} valid entries",
                    filePathStr, snapshot.timestamp(), snapshot.nodeNodeId(), recovered.size());

            return recovered;

        } catch (Exception e) {
            log.error("Failed to load snapshot from '{}': {}", filePathStr, e.getMessage(), e);
            return List.of();
        }
    }
}
