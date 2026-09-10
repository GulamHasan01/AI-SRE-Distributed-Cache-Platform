package com.cache.chaos;

import com.cache.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ChaosController — Dev/Demo only.
 * Only active when cache.chaos.enabled=true.
 * Provides controlled failure injection for demonstrating AI SRE capabilities.
 *
 * SECURITY: This controller must NEVER be enabled in production.
 * It is disabled by default and requires explicit opt-in via environment variable.
 */
@RestController
@RequestMapping("/api/v1/chaos")
@ConditionalOnProperty(name = "cache.chaos.enabled", havingValue = "true")
@Tag(name = "Chaos Engineering", description = "Dev/Demo only — failure injection for AI SRE demonstration")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    // Keep references to allocated memory to prevent GC
    private final List<byte[]> memoryHogs = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> latencyTask;
    private volatile long artificialDelayMs = 0;

    @Operation(
        summary = "[CHAOS] Allocate memory to simulate heap pressure",
        description = "Allocates the specified MB of memory on the heap. " +
                      "Combined with NO_EVICTION policy, this will trigger JVM OOM. " +
                      "DEV/DEMO ONLY — controlled failure for AI SRE demo."
    )
    @PostMapping("/memory-pressure")
    public ResponseEntity<ApiResponse<Map<String, Object>>> memoryPressure(
            @RequestParam(defaultValue = "256") int targetMb) {

        targetMb = Math.max(64, Math.min(targetMb, 1024)); // safety bounds: 64MB–1GB
        log.warn("[CHAOS] MEMORY PRESSURE activated: allocating {}MB", targetMb);

        try {
            byte[] block = new byte[targetMb * 1024 * 1024];
            // Fill to prevent JIT optimization away
            for (int i = 0; i < block.length; i += 4096) {
                block[i] = 1;
            }
            memoryHogs.add(block);

            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long maxMb = rt.maxMemory() / (1024 * 1024);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("allocated_mb", targetMb);
            data.put("total_allocated_mb", memoryHogs.stream().mapToInt(b -> b.length / 1024 / 1024).sum());
            data.put("jvm_used_mb", usedMb);
            data.put("jvm_max_mb", maxMb);
            data.put("heap_usage_pct", Math.round((double) usedMb / maxMb * 100));

            log.warn("[CHAOS] Memory state: {}MB used / {}MB max ({}%)", usedMb, maxMb, data.get("heap_usage_pct"));
            return ResponseEntity.ok(ApiResponse.success("Memory pressure applied", data));

        } catch (OutOfMemoryError oom) {
            log.error("[CHAOS] OutOfMemoryError triggered! {}", oom.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("error", "OutOfMemoryError");
            data.put("message", oom.getMessage());
            return ResponseEntity.status(507).body(ApiResponse.failure("OOM triggered as expected", oom.getMessage()));
        }
    }

    @Operation(
        summary = "[CHAOS] Release previously allocated memory",
        description = "Releases all chaos-allocated memory blocks. DEV/DEMO ONLY."
    )
    @DeleteMapping("/memory-pressure")
    public ResponseEntity<ApiResponse<Map<String, Object>>> releaseMemory() {
        int count = memoryHogs.size();
        memoryHogs.clear();
        System.gc();
        log.warn("[CHAOS] Released {} memory blocks", count);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("released_blocks", count);
        return ResponseEntity.ok(ApiResponse.success("Memory released", data));
    }

    @Operation(
        summary = "[CHAOS] Enable artificial response latency",
        description = "Adds artificial delay to all cache read operations. DEV/DEMO ONLY."
    )
    @PostMapping("/slow-response")
    public ResponseEntity<ApiResponse<Map<String, Object>>> slowResponse(
            @RequestParam(defaultValue = "2000") long delayMs,
            @RequestParam(defaultValue = "30") int durationSeconds) {

        delayMs = Math.max(100, Math.min(delayMs, 10000));
        this.artificialDelayMs = delayMs;

        // Auto-cancel after durationSeconds
        if (latencyTask != null && !latencyTask.isDone()) {
            latencyTask.cancel(false);
        }
        long finalDelayMs = delayMs;
        latencyTask = scheduler.schedule(() -> {
            this.artificialDelayMs = 0;
            log.info("[CHAOS] Artificial latency cleared after {} seconds", durationSeconds);
        }, durationSeconds, TimeUnit.SECONDS);

        log.warn("[CHAOS] SLOW RESPONSE enabled: {}ms delay for {}s", delayMs, durationSeconds);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("delay_ms", delayMs);
        data.put("duration_seconds", durationSeconds);
        data.put("auto_clear_after", durationSeconds + "s");
        return ResponseEntity.ok(ApiResponse.success("Slow response mode enabled", data));
    }

    @Operation(
        summary = "[CHAOS] Disable artificial latency",
        description = "Removes artificial delay immediately. DEV/DEMO ONLY."
    )
    @DeleteMapping("/slow-response")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearSlowResponse() {
        this.artificialDelayMs = 0;
        log.info("[CHAOS] Artificial latency cleared");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("delay_ms", 0);
        return ResponseEntity.ok(ApiResponse.success("Slow response cleared", data));
    }

    @Operation(
        summary = "[CHAOS] Get current chaos status",
        description = "Returns the state of all active chaos injections. DEV/DEMO ONLY."
    )
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChaosStatus() {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memory_hogs_count", memoryHogs.size());
        data.put("allocated_chaos_mb", memoryHogs.stream().mapToInt(b -> b.length / 1024 / 1024).sum());
        data.put("jvm_used_mb", usedMb);
        data.put("jvm_max_mb", maxMb);
        data.put("heap_usage_pct", Math.round((double) usedMb / maxMb * 100));
        data.put("artificial_delay_ms", artificialDelayMs);
        return ResponseEntity.ok(ApiResponse.success("Chaos status", data));
    }

    /**
     * Returns the configured artificial delay (used by filter if wired in).
     */
    public long getArtificialDelayMs() {
        return artificialDelayMs;
    }
}
