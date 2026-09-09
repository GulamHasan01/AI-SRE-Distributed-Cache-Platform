package com.gateway.routing;

import com.gateway.cluster.model.NodeInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class ConsistentHashRing {

    private final TreeMap<Long, String> ring;
    private final int physicalNodeCount;

    public ConsistentHashRing(Collection<NodeInfo> nodes, int virtualNodesPerNode) {
        this.ring = new TreeMap<>();
        for (NodeInfo node : nodes) {
            for (int i = 0; i < virtualNodesPerNode; i++) {
                long position = hash(node.getId() + "#" + i);
                ring.put(position, node.getId());
            }
        }
        this.physicalNodeCount = nodes.size();
    }

    public Optional<String> getNodeForKey(String key) {
        if (ring.isEmpty()) {
            return Optional.empty();
        }
        long position = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(position);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return Optional.of(entry.getValue());
    }

    public List<String> getNodesForKey(String key, int count) {
        if (ring.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        long position = hash(key);

        Map.Entry<Long, String> entry = ring.ceilingEntry(position);
        if (entry == null) {
            entry = ring.firstEntry();
        }

        while (entry != null && result.size() < Math.min(count, physicalNodeCount)) {
            String nodeId = entry.getValue();
            if (!result.contains(nodeId)) {
                result.add(nodeId);
            }
            entry = ring.higherEntry(entry.getKey());
            if (entry == null) {
                entry = ring.firstEntry();
            }
        }
        return result;
    }

    public Map<Long, String> getRingMap() {
        return Collections.unmodifiableMap(ring);
    }

    private static final ThreadLocal<MessageDigest> MD5_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unexpectedly unavailable", e);
        }
    });

    public static long hash(String input) {
        MessageDigest md = MD5_DIGEST.get();
        md.reset();
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (digest[i] & 0xFFL);
        }
        return result;
    }
}
