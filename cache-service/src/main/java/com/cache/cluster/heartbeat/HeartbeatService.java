package com.cache.cluster.heartbeat;

import com.cache.cluster.model.NodeInfo;

public interface HeartbeatService {

    void emitHeartbeats();

    boolean pingNode(NodeInfo node);
}
