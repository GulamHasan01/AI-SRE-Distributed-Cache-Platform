package com.cache.cluster.registry;

import com.cache.cluster.model.NodeInfo;
import com.cache.cluster.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InMemoryClusterRegistry Unit Tests")
class InMemoryClusterRegistryTest {

    private InMemoryClusterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryClusterRegistry();
    }

    private NodeInfo makeNode(String id, String host, int port) {
        return new NodeInfo(id, host, port);
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register a node and make it findable by ID")
        void shouldRegisterNode() {
            NodeInfo node = makeNode("node-1", "localhost", 8081);

            registry.register(node);

            assertThat(registry.exists("node-1")).isTrue();
            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("should replace existing node on re-registration (idempotent)")
        void shouldReplaceOnReRegistration() {
            registry.register(makeNode("node-1", "host-1", 8081));

            registry.register(makeNode("node-1", "host-2", 8082));

            assertThat(registry.size()).isEqualTo(1);
            Optional<NodeInfo> found = registry.findById("node-1");
            assertThat(found).isPresent();
            assertThat(found.get().getHost()).isEqualTo("host-2");
        }

        @Test
        @DisplayName("should register multiple nodes independently")
        void shouldRegisterMultipleNodes() {
            registry.register(makeNode("node-1", "h1", 8081));
            registry.register(makeNode("node-2", "h2", 8082));
            registry.register(makeNode("node-3", "h3", 8083));

            assertThat(registry.size()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("deregister()")
    class DeregisterTests {

        @Test
        @DisplayName("should remove an existing node and return true")
        void shouldDeregisterExistingNode() {
            registry.register(makeNode("node-1", "localhost", 8081));

            boolean result = registry.deregister("node-1");

            assertThat(result).isTrue();
            assertThat(registry.exists("node-1")).isFalse();
            assertThat(registry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return false when deregistering a non-existent node")
        void shouldReturnFalseForMissingNode() {
            boolean result = registry.deregister("ghost");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should return node when it exists")
        void shouldReturnNodeWhenExists() {
            registry.register(makeNode("node-1", "host", 8081));

            Optional<NodeInfo> result = registry.findById("node-1");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo("node-1");
        }

        @Test
        @DisplayName("should return empty Optional when node not found")
        void shouldReturnEmptyForMissingNode() {
            assertThat(registry.findById("ghost")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAllTests {

        @Test
        @DisplayName("should return all registered nodes")
        void shouldReturnAllNodes() {
            registry.register(makeNode("n1", "h1", 1));
            registry.register(makeNode("n2", "h2", 2));

            Collection<NodeInfo> all = registry.findAll();

            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("should return empty collection when no nodes registered")
        void shouldReturnEmptyWhenNoNodes() {
            assertThat(registry.findAll()).isEmpty();
        }

        @Test
        @DisplayName("findAll() collection should be unmodifiable")
        void findAllShouldBeUnmodifiable() {
            registry.register(makeNode("n1", "h1", 1));
            Collection<NodeInfo> all = registry.findAll();

            assertThatThrownBy(() -> all.add(makeNode("n2", "h2", 2)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("findByStatus()")
    class FindByStatusTests {

        @Test
        @DisplayName("should return only nodes matching the requested status")
        void shouldFilterByStatus() {
            NodeInfo n1 = makeNode("n1", "h1", 1);
            NodeInfo n2 = makeNode("n2", "h2", 2);
            NodeInfo n3 = makeNode("n3", "h3", 3);
            n1.markUp();
            n2.markUp();

            registry.register(n1);
            registry.register(n2);
            registry.register(n3);

            Collection<NodeInfo> upNodes = registry.findByStatus(NodeStatus.UP);
            Collection<NodeInfo> startingNodes = registry.findByStatus(NodeStatus.STARTING);

            assertThat(upNodes).hasSize(2);
            assertThat(startingNodes).hasSize(1);
            assertThat(startingNodes.iterator().next().getId()).isEqualTo("n3");
        }

        @Test
        @DisplayName("should return empty collection when no nodes match status")
        void shouldReturnEmptyWhenNoMatch() {
            registry.register(makeNode("n1", "h1", 1));

            assertThat(registry.findByStatus(NodeStatus.DOWN)).isEmpty();
        }
    }

    @Nested
    @DisplayName("NodeInfo business methods")
    class NodeInfoTests {

        @Test
        @DisplayName("new node should be in STARTING status")
        void newNodeShouldBeStarting() {
            NodeInfo node = makeNode("n1", "h1", 8081);
            assertThat(node.getStatus()).isEqualTo(NodeStatus.STARTING);
            assertThat(node.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("markUp() should transition node to UP status")
        void markUpShouldTransitionToUp() {
            NodeInfo node = makeNode("n1", "h1", 8081);
            node.markUp();

            assertThat(node.getStatus()).isEqualTo(NodeStatus.UP);
            assertThat(node.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("getBaseUrl() should return correct URL")
        void getBaseUrlShouldReturnCorrectFormat() {
            NodeInfo node = makeNode("n1", "cache-node-1.internal", 8082);
            assertThat(node.getBaseUrl()).isEqualTo("http://cache-node-1.internal:8082");
        }

        @Test
        @DisplayName("recordHeartbeat() on SUSPECT node should transition to UP")
        void recordHeartbeatShouldRecoverSuspectNode() {
            NodeInfo node = makeNode("n1", "h1", 8081);
            node.setStatus(NodeStatus.SUSPECT);

            node.recordHeartbeat();

            assertThat(node.getStatus()).isEqualTo(NodeStatus.UP);
        }
    }
}
