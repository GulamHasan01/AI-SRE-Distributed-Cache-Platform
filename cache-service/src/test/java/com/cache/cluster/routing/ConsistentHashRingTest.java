package com.cache.cluster.routing;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsistentHashRing")
class ConsistentHashRingTest {

    private static final int VNODES = 150;

    private static NodeInfo makeNode(String id) {
        NodeInfo node = new NodeInfo(id, "localhost", 8080);
        node.markUp();
        return node;
    }

    @Nested
    @DisplayName("Empty ring")
    class EmptyRingTests {

        @Test
        @DisplayName("getNodeForKey returns empty when no nodes")
        void shouldReturnEmptyForEmptyRing() {
            ConsistentHashRing ring = new ConsistentHashRing(Collections.emptyList(), VNODES);
            assertThat(ring.getNodeForKey("any-key")).isEmpty();
        }

        @Test
        @DisplayName("size returns 0 for empty ring")
        void shouldReturnZeroSize() {
            ConsistentHashRing ring = new ConsistentHashRing(Collections.emptyList(), VNODES);
            assertThat(ring.size()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Single node")
    class SingleNodeTests {

        @Test
        @DisplayName("all keys map to the only node")
        void allKeysMapToSingleNode() {
            NodeInfo node = makeNode("node-1");
            ConsistentHashRing ring = new ConsistentHashRing(List.of(node), VNODES);

            assertThat(ring.getNodeForKey("key-a")).contains("node-1");
            assertThat(ring.getNodeForKey("key-b")).contains("node-1");
            assertThat(ring.getNodeForKey("user:9999")).contains("node-1");
            assertThat(ring.getNodeForKey("")).contains("node-1");
        }

        @Test
        @DisplayName("size returns 1")
        void sizeShouldBeOne() {
            ConsistentHashRing ring = new ConsistentHashRing(List.of(makeNode("node-1")), VNODES);
            assertThat(ring.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Determinism")
    class DeterminismTests {

        @Test
        @DisplayName("same key always maps to same node across independent ring instances")
        void sameKeyAlwaysMapsSameNode() {
            NodeInfo n1 = makeNode("node-1");
            NodeInfo n2 = makeNode("node-2");
            NodeInfo n3 = makeNode("node-3");
            List<NodeInfo> nodes = List.of(n1, n2, n3);

            ConsistentHashRing ring1 = new ConsistentHashRing(nodes, VNODES);
            ConsistentHashRing ring2 = new ConsistentHashRing(nodes, VNODES);

            String[] keys = {"user:1001", "session:abc", "product:42", "order:999"};
            for (String key : keys) {
                assertThat(ring1.getNodeForKey(key))
                        .isEqualTo(ring2.getNodeForKey(key));
            }
        }
    }

    @Nested
    @DisplayName("Distribution")
    class DistributionTests {

        @Test
        @DisplayName("two nodes each receive at least 20%% of 1000 keys (rough fairness check)")
        void twoNodesShouldShareKeysRoughlyEvenly() {
            NodeInfo n1 = makeNode("node-1");
            NodeInfo n2 = makeNode("node-2");
            ConsistentHashRing ring = new ConsistentHashRing(List.of(n1, n2), VNODES);

            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                String key = "key:" + i;
                String owner = ring.getNodeForKey(key).orElseThrow();
                counts.merge(owner, 1, Integer::sum);
            }

            assertThat(counts.getOrDefault("node-1", 0)).isGreaterThan(200);
            assertThat(counts.getOrDefault("node-2", 0)).isGreaterThan(200);
        }

        @Test
        @DisplayName("all nodes receive at least some keys with 3 nodes and 10000 keys")
        void allThreeNodesShouldReceiveKeys() {
            NodeInfo n1 = makeNode("node-1");
            NodeInfo n2 = makeNode("node-2");
            NodeInfo n3 = makeNode("node-3");
            ConsistentHashRing ring = new ConsistentHashRing(List.of(n1, n2, n3), VNODES);

            Set<String> nodesSeen = new HashSet<>();
            for (int i = 0; i < 10000; i++) {
                ring.getNodeForKey("k:" + i).ifPresent(nodesSeen::add);
            }

            assertThat(nodesSeen).containsExactlyInAnyOrder("node-1", "node-2", "node-3");
        }
    }

    @Nested
    @DisplayName("Minimal disruption on membership change")
    class MinimalDisruptionTests {

        @Test
        @DisplayName("adding a node moves roughly 1/N keys (core consistent hashing property)")
        void addingNodeShouldMoveMinimalKeys() {
            NodeInfo n1 = makeNode("node-1");
            NodeInfo n2 = makeNode("node-2");

            ConsistentHashRing ringBefore = new ConsistentHashRing(List.of(n1, n2), VNODES);

            NodeInfo n3 = makeNode("node-3");
            ConsistentHashRing ringAfter = new ConsistentHashRing(List.of(n1, n2, n3), VNODES);

            int total = 10000;
            int moved = 0;
            for (int i = 0; i < total; i++) {
                String key = "key:" + i;
                String before = ringBefore.getNodeForKey(key).orElseThrow();
                String after  = ringAfter.getNodeForKey(key).orElseThrow();
                if (!before.equals(after)) moved++;
            }

            double movedRatio = (double) moved / total;
            assertThat(movedRatio).isBetween(0.15, 0.55);
        }

        @Test
        @DisplayName("removing a node only reassigns keys from the removed node")
        void removingNodeShouldOnlyReassignRemovedNodesKeys() {
            NodeInfo n1 = makeNode("node-1");
            NodeInfo n2 = makeNode("node-2");
            NodeInfo n3 = makeNode("node-3");

            ConsistentHashRing ringBefore = new ConsistentHashRing(List.of(n1, n2, n3), VNODES);
            ConsistentHashRing ringAfter  = new ConsistentHashRing(List.of(n1, n2), VNODES);

            int wrongReassignment = 0;
            for (int i = 0; i < 10000; i++) {
                String key = "key:" + i;
                String before = ringBefore.getNodeForKey(key).orElseThrow();
                String after  = ringAfter.getNodeForKey(key).orElseThrow();
                if (!before.equals(after)) {
                    if (!before.equals("node-3")) wrongReassignment++;
                }
            }

            assertThat(wrongReassignment).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Replication Lookups")
    class ReplicationLookupTests {

        @Test
        @DisplayName("returns unique nodes clockwise up to count")
        void shouldReturnUniqueNodesClockwise() {
            NodeInfo n1 = makeNode("node-1");
            NodeInfo n2 = makeNode("node-2");
            NodeInfo n3 = makeNode("node-3");
            ConsistentHashRing ring = new ConsistentHashRing(List.of(n1, n2, n3), VNODES);

            List<String> nodes = ring.getNodesForKey("user:1001", 2);
            assertThat(nodes).hasSize(2);
            assertThat(nodes.get(0)).isNotEqualTo(nodes.get(1));

            List<String> threeNodes = ring.getNodesForKey("user:1001", 3);
            assertThat(threeNodes).containsExactlyInAnyOrder("node-1", "node-2", "node-3");
        }

        @Test
        @DisplayName("caps return list size at physical node count even if requested count is larger")
        void shouldCapAtPhysicalNodeCount() {
            NodeInfo n1 = makeNode("node-1");
            NodeInfo n2 = makeNode("node-2");
            ConsistentHashRing ring = new ConsistentHashRing(List.of(n1, n2), VNODES);

            List<String> nodes = ring.getNodesForKey("user:1001", 5);
            assertThat(nodes).hasSize(2);
            assertThat(nodes).containsExactlyInAnyOrder("node-1", "node-2");
        }
    }

    @Nested
    @DisplayName("hash() internal function")
    class HashFunctionTests {

        @Test
        @DisplayName("same input always produces same hash")
        void hashIsDeterministic() {
            assertThat(ConsistentHashRing.hash("hello")).isEqualTo(ConsistentHashRing.hash("hello"));
        }

        @Test
        @DisplayName("different inputs produce different hashes")
        void hashCollidesRarely() {
            assertThat(ConsistentHashRing.hash("node-1#0"))
                    .isNotEqualTo(ConsistentHashRing.hash("node-1#1"));
        }
    }
}