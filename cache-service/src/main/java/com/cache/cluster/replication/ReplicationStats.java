package com.cache.cluster.replication;

public record ReplicationStats(
        long totalAttempted,
        long succeeded,
        long failed,
        double successRatePercent
) {

    public static ReplicationStats of(long totalAttempted, long succeeded, long failed) {
        double rate = totalAttempted == 0
                ? 100.0
                : Math.round((succeeded * 100.0 / totalAttempted) * 10.0) / 10.0;
        return new ReplicationStats(totalAttempted, succeeded, failed, rate);
    }

    public static ReplicationStats empty() {
        return new ReplicationStats(0L, 0L, 0L, 100.0);
    }
}
