package com.cache.cluster.routing;

import java.util.List;

public interface KeyRoutingService {

    String getOwnerNodeId(String key);

    List<String> getReplicaNodeIds(String key);

    List<String> getRouteList(String key);
}