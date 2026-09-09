package com.gateway.routing;

import com.gateway.cluster.model.NodeInfo;
import com.gateway.cluster.model.NodeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsistentHashRing (gateway)")
class ConsistentHashRingTest {

    private static NodeInfo node(String id, int port) {
        return new NodeInfo(id, "localhost", port, NodeStatus.UP);
    }

    @Nested
    @DisplayName("Empty ring")
    class EmptyRingTests {

        @Test
        @DisplayName("getNodeForKey returns empty when ring is empty")
        void emptyRingReturnsEmpty() {
            ConsistentHashRing ring = new ConsistentHashRing(Collections.emptyList(), 150);
            assertThat(ring.getNodeForKey("any-key")).isEmpty();
        }

        @Test
        @DisplayName("getNodesForKey returns empty list when ring is empty")
        void emptyRingReturnsEmptyList() {
            ConsistentHashRing ring = new ConsistentHashRing(Collections.emptyList(), 150);
            assertThat(ring.getNodesForKey("any-key", 3)).isEmpty();
        }

        @Test
        @DisplayName("getRingMap returns empty map when no nodes added")
        void emptyRingMapIsEmpty() {
            ConsistentHashRing ring = new ConsistentHashRing(Collections.emptyList(), 150);
            assertThat(ring.getRingMap()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Single node")
    class SingleNodeTests {

        @Test
        @DisplayName("single node always owns every key")
        void singleNodeOwnsEveryKey() {
            ConsistentHashRing ring = new ConsistentHashRing(List.of(node("node-1", 8081)), 10);

            assertThat(ring.getNodeForKey("key-a")).contains("node-1");
            assertThat(ring.getNodeForKey("key-b")).contains("node-1");
            assertThat(ring.getNodeForKey("key-c")).contains("node-1");
        }

        @Test
        @DisplayName("ring map has exactly virtualNodesPerNode entries")
        void ringMapSizeEqualsVirtualNodes() {
            int vnodes = 10;
            ConsistentHashRing ring = new ConsistentHashRing(List.of(node("node-1", 8081)), vnodes);
            assertThat(ring.getRingMap()).hasSize(vnodes);
        }
    }

    @Nested
    @DisplayName("Multi-node ring")
    class MultiNodeTests {

        @Test
        @DisplayName("ring map size = nodes × virtualNodesPerNode (no hash collisions)")
        void ringMapSizeIsCorrect() {
            int vnodes = 10;
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081), node("node-2", 8082), node("node-3", 8083)), vnodes);
            assertThat(ring.getRingMap()).hasSize(30);
        }

        @Test
        @DisplayName("getNodeForKey always returns one of the registered nodes")
        void routingReturnsValidNode() {
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081), node("node-2", 8082), node("node-3", 8083)), 150);

            Optional<String> owner = ring.getNodeForKey("user:12345");
            assertThat(owner).isPresent();
            assertThat(owner.get()).isIn("node-1", "node-2", "node-3");
        }

        @Test
        @DisplayName("same key always routes to same node (determinism)")
        void routingIsDeterministic() {
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081), node("node-2", 8082), node("node-3", 8083)), 150);

            String key = "consistent-key-test";
            String firstOwner = ring.getNodeForKey(key).orElseThrow();
            for (int i = 0; i < 20; i++) {
                assertThat(ring.getNodeForKey(key)).contains(firstOwner);
            }
        }

        @Test
        @DisplayName("all registered nodes are reachable — no node is excluded from routing")
        void allNodesReachable() {
            List<NodeInfo> nodes = List.of(
                    node("node-1", 8081), node("node-2", 8082), node("node-3", 8083));
            ConsistentHashRing ring = new ConsistentHashRing(nodes, 150);

            Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ring.getNodeForKey("key-" + i).ifPresent(seen::add);
            }
            assertThat(seen).containsExactlyInAnyOrder("node-1", "node-2", "node-3");
        }

        @Test
        @DisplayName("ring map is read-only (unmodifiable view)")
        void ringMapIsUnmodifiable() {
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081)), 5);

            var map = ring.getRingMap();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> map.put(0L, "injected"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("getNodesForKey() — replica selection")
    class ReplicaSelectionTests {

        @Test
        @DisplayName("returns up to count distinct physical nodes")
        void returnsDistinctNodes() {
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081), node("node-2", 8082), node("node-3", 8083)), 150);

            List<String> replicas = ring.getNodesForKey("replica-key", 3);
            assertThat(replicas).hasSize(3);
            assertThat(replicas).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("never returns more nodes than are registered")
        void neverExceedsRegisteredCount() {
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081), node("node-2", 8082)), 150);

            List<String> replicas = ring.getNodesForKey("some-key", 5);
            assertThat(replicas.size()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("count=0 returns empty list")
        void countZeroReturnsEmpty() {
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081)), 10);
            assertThat(ring.getNodesForKey("key", 0)).isEmpty();
        }

        @Test
        @DisplayName("count=1 returns just the primary owner")
        void countOneReturnsPrimary() {
            ConsistentHashRing ring = new ConsistentHashRing(
                    List.of(node("node-1", 8081), node("node-2", 8082), node("node-3", 8083)), 150);

            String primary = ring.getNodeForKey("test-key").orElseThrow();
            List<String> one = ring.getNodesForKey("test-key", 1);
            assertThat(one).containsExactly(primary);
        }
    }

    @Nested
    @DisplayName("hash() — static hash function")
    class HashFunctionTests {

        @Test
        @DisplayName("same input always produces same hash")
        void hashIsDeterministic() {
            long h1 = ConsistentHashRing.hash("some-key");
            long h2 = ConsistentHashRing.hash("some-key");
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("different inputs produce different hashes (no trivial collisions)")
        void differentInputsDifferentHashes() {
            long h1 = ConsistentHashRing.hash("node-1#0");
            long h2 = ConsistentHashRing.hash("node-2#0");
            assertThat(h1).isNotEqualTo(h2);
        }
    }
}
