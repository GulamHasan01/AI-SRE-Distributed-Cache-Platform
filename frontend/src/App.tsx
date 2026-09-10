import React, { useState, useEffect, useRef } from 'react';
import { Header } from './components/Header';
import { StatsCards } from './components/StatsCards';
import { HashRing } from './components/HashRing';
import type { PhysicalNode, VirtualNode } from './components/HashRing';
import { NodeDirectory } from './components/NodeDirectory';
import { CacheConsole } from './components/CacheConsole';
import { LogConsole } from './components/LogConsole';
import type { LogEntry } from './components/LogConsole';
import { SreIncidentPanel } from './components/SreIncidentPanel';

interface NodeStats {
  totalKeys: number;
  usagePercent: number;
  totalHits: number;
  totalMisses: number;
  replicationStats?: {
    totalAttempted: number;
    succeeded: number;
    failed: number;
  };
}

export const App: React.FC = () => {
  const [targetGateway, setTargetGateway] = useState('http://localhost:8080');
  const [clusterNodes, setClusterNodes] = useState<PhysicalNode[]>([]);
  const [virtualNodes, setVirtualNodes] = useState<VirtualNode[]>([]);
  const [nodeStatsMap, setNodeStatsMap] = useState<Record<string, NodeStats | null>>({});
  const [activeKeyRoute, setActiveKeyRoute] = useState<{ hash: string; ownerNodeId: string } | null>(null);

  const [logs, setLogs] = useState<LogEntry[]>([
    {
      time: new Date().toTimeString().split(' ')[0],
      level: 'INFO',
      msg: 'Dashboard Console Initialized. Polling gateway at http://localhost:8080...'
    }
  ]);

  const [healthInfo, setHealthInfo] = useState({
    upCount: 0,
    totalNodes: 0,
    clusterHealthy: false,
    downCount: 0,
    suspectCount: 0
  });

  const targetGatewayRef = useRef(targetGateway);
  useEffect(() => {
    targetGatewayRef.current = targetGateway;
  }, [targetGateway]);

  const writeLog = (level: LogEntry['level'], msg: string, traceId?: string) => {
    const time = new Date().toTimeString().split(' ')[0];
    setLogs(prev => [...prev, { time, level, msg, traceId }]);
  };

  const clearLogs = () => {
    setLogs([]);
  };

  const pollClusterState = async () => {
    const gateway = targetGatewayRef.current;
    try {
      const res = await fetch(`${gateway}/api/v1/gateway/cluster/status`);
      if (!res.ok) throw new Error(`Gateway HTTP ${res.status}`);
      const body = await res.json();

      if (body.success && body.data) {
        const nodesData: PhysicalNode[] = body.data.nodes.map((n: any) => ({
          nodeId: n.nodeId,
          host: n.host,
          port: n.port,
          status: n.status
        }));

        setClusterNodes(nodesData);
        setHealthInfo({
          upCount: body.data.upCount,
          totalNodes: body.data.totalNodes,
          clusterHealthy: body.data.clusterHealthy,
          downCount: body.data.downCount,
          suspectCount: body.data.suspectCount
        });

        await fetchRingLayout(gateway);

        await fetchNodesStats(nodesData);
      }
    } catch (err: any) {
      console.error("Polling error:", err);
      writeLog('ERROR', `Failed to poll cluster health via gateway: ${err.message}`);
    }
  };

  const fetchRingLayout = async (gateway: string) => {
    try {
      const res = await fetch(`${gateway}/api/v1/gateway/cluster/ring`);
      if (!res.ok) return;
      const body = await res.json();
      if (body.success && body.data) {
        setVirtualNodes(body.data);
      }
    } catch (err: any) {
      console.error("Ring fetch error:", err);
    }
  };

  const fetchNodesStats = async (nodes: PhysicalNode[]) => {
    const statsMap: Record<string, NodeStats | null> = {};
    for (const node of nodes) {
      if (node.status === 'UP') {
        const nodeUrl = `http://${node.host}:${node.port}`;
        try {
          const statsRes = await fetch(`${nodeUrl}/api/v1/cache/stats`, { signal: AbortSignal.timeout(1200) });
          if (statsRes.ok) {
            const statsBody = await statsRes.json();
            if (statsBody.success) {
              statsMap[node.nodeId] = statsBody.data;
              continue;
            }
          }
        } catch (e) {
          console.warn(`Could not fetch stats from node ${node.nodeId}:`, e);
        }
      }
      statsMap[node.nodeId] = null;
    }
    setNodeStatsMap(statsMap);
  };

  const handleTriggerNodeStateChange = async (nodeId: string, state: 'UP' | 'DOWN') => {
    writeLog('WARN', `Manually changing node '${nodeId}' status to ${state}...`);
    try {
      const res = await fetch(`${targetGateway}/api/v1/gateway/cluster/nodes/${nodeId}/status?status=${state}`, {
        method: 'PUT'
      });
      const body = await res.json();
      if (res.ok && body.success) {
        writeLog('INFO', `Node '${nodeId}' successfully marked ${state}. Ring rebuilt.`, res.headers.get('X-Trace-Id') || undefined);
        await pollClusterState();
      } else {
        writeLog('ERROR', `Failed to transition node status: ${body.message}`);
      }
    } catch (err: any) {
      writeLog('ERROR', `Failed to trigger state change: ${err.message}`);
    }
  };

  const handleRouteKey = async (key: string) => {
    try {
      const res = await fetch(`${targetGateway}/api/v1/gateway/cluster/hash-key?key=${encodeURIComponent(key)}`);
      const body = await res.json();
      if (res.ok && body.success) {
        const routing = body.data;
        writeLog('GATEWAY', `[Trace Route] Key: "${key}" | Hash: ${routing.hashHex} (${routing.hash})`);
        writeLog('GATEWAY', `Mapped Node Owner: ${routing.ownerNodeId} | Replicas: [${routing.replicas.join(', ')}]`);

        setActiveKeyRoute({
          hash: routing.hash,
          ownerNodeId: routing.ownerNodeId
        });
      } else {
        writeLog('ERROR', `Failed to route key: ${body.message}`);
      }
    } catch (err: any) {
      writeLog('ERROR', `Failed to query key route: ${err.message}`);
    }
  };

  const handleExecuteGet = async (key: string, onValueFound: (val: string, ttl: string) => void) => {
    writeLog('GATEWAY', `GET (Gateway) key='${key}'...`);
    try {
      const res = await fetch(`${targetGateway}/api/v1/gateway/cache/${encodeURIComponent(key)}`);
      const traceId = res.headers.get('X-Trace-Id') || undefined;
      const body = await res.json();

      if (res.ok && body.success) {
        const data = body.data;
        writeLog('INFO', `GET SUCCESS: value="${data.value}" | TTL remaining: ${data.remainingTtlSeconds}s | Source node: ${data.nodeId}`, traceId);
        onValueFound(data.value, String(data.remainingTtlSeconds));

        const hashRes = await fetch(`${targetGateway}/api/v1/gateway/cluster/hash-key?key=${encodeURIComponent(key)}`);
        const hashBody = await hashRes.json();
        if (hashRes.ok && hashBody.success) {
          setActiveKeyRoute({
            hash: hashBody.data.hash,
            ownerNodeId: hashBody.data.ownerNodeId
          });
        }
      } else {
        writeLog('WARN', `GET MISS/FAIL: ${body.message}`, traceId);
      }
    } catch (err: any) {
      writeLog('ERROR', `GET Request Failed: ${err.message}`);
    }
  };

  const handleExecutePut = async (key: string, value: string, ttl: string) => {
    const ttlSeconds = ttl ? parseInt(ttl) : null;
    const payload = { key, value, ttlSeconds };

    writeLog('GATEWAY', `PUT (Gateway) key='${key}' value='${value}' TTL=${ttlSeconds || 'persistent'}...`);
    try {
      const res = await fetch(`${targetGateway}/api/v1/gateway/cache`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const traceId = res.headers.get('X-Trace-Id') || undefined;
      const body = await res.json();

      if (res.ok && body.success) {
        const data = body.data;
        writeLog('INFO', `PUT SUCCESS: Key stored on primary node ${data.nodeId}`, traceId);

        const hashRes = await fetch(`${targetGateway}/api/v1/gateway/cluster/hash-key?key=${encodeURIComponent(key)}`);
        const hashBody = await hashRes.json();
        if (hashRes.ok && hashBody.success) {
          setActiveKeyRoute({
            hash: hashBody.data.hash,
            ownerNodeId: hashBody.data.ownerNodeId
          });
        }
      } else {
        writeLog('ERROR', `PUT FAILED: ${body.message}`, traceId);
      }
    } catch (err: any) {
      writeLog('ERROR', `PUT Request Failed: ${err.message}`);
    }
  };

  const handleExecuteDelete = async (key: string, onValueCleared: () => void) => {
    writeLog('GATEWAY', `DELETE (Gateway) key='${key}'...`);
    try {
      const res = await fetch(`${targetGateway}/api/v1/gateway/cache/${encodeURIComponent(key)}`, {
        method: 'DELETE'
      });
      const traceId = res.headers.get('X-Trace-Id') || undefined;
      const body = await res.json();

      if (res.ok && body.success) {
        writeLog('INFO', `DELETE SUCCESS: key removed from cluster`, traceId);
        onValueCleared();
        setActiveKeyRoute(null);
      } else {
        writeLog('WARN', `DELETE FAILED: ${body.message}`, traceId);
      }
    } catch (err: any) {
      writeLog('ERROR', `DELETE Request Failed: ${err.message}`);
    }
  };

  useEffect(() => {
    pollClusterState();
    const interval = setInterval(pollClusterState, 2500);
    return () => clearInterval(interval);
  }, []);

  let aggregatedKeys = 0;
  let aggregatedHits = 0;
  let aggregatedMisses = 0;
  let aggregatedReplAttempted = 0;
  let aggregatedReplSucceeded = 0;
  let aggregatedReplFailed = 0;
  let aggregatedUsagePctSum = 0;
  let upCount = 0;

  clusterNodes.forEach(node => {
    const stats = nodeStatsMap[node.nodeId];
    if (stats) {
      aggregatedKeys += stats.totalKeys;
      aggregatedHits += stats.totalHits;
      aggregatedMisses += stats.totalMisses;
      aggregatedUsagePctSum += stats.usagePercent;

      if (stats.replicationStats) {
        aggregatedReplAttempted += stats.replicationStats.totalAttempted;
        aggregatedReplSucceeded += stats.replicationStats.succeeded;
        aggregatedReplFailed += stats.replicationStats.failed;
      }
    }
    if (node.status === 'UP') {
      upCount++;
    }
  });

  const avgUsagePercent = clusterNodes.length === 0 ? 0.0 : aggregatedUsagePctSum / clusterNodes.length;

  return (
    <div>
      <Header
        upCount={healthInfo.upCount}
        totalNodes={healthInfo.totalNodes}
        clusterHealthy={healthInfo.clusterHealthy}
        downCount={healthInfo.downCount}
        suspectCount={healthInfo.suspectCount}
        targetGateway={targetGateway}
        onGatewayChange={setTargetGateway}
      />

      <StatsCards
        totalKeys={aggregatedKeys}
        totalHits={aggregatedHits}
        totalMisses={aggregatedMisses}
        totalReplAttempted={aggregatedReplAttempted}
        totalReplSucceeded={aggregatedReplSucceeded}
        totalReplFailed={aggregatedReplFailed}
        activeNodesCount={upCount}
        totalNodesCount={clusterNodes.length}
        avgUsagePercent={avgUsagePercent}
      />

      <SreIncidentPanel
        targetGateway={targetGateway}
      />

      <div className="dashboard-grid">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          <HashRing
            virtualNodes={virtualNodes}
            clusterNodes={clusterNodes}
            activeKeyRoute={activeKeyRoute}
          />

          <NodeDirectory
            clusterNodes={clusterNodes}
            nodeStatsMap={nodeStatsMap}
            onTriggerNodeStateChange={handleTriggerNodeStateChange}
          />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          <CacheConsole
            onRouteKey={handleRouteKey}
            onExecuteGet={handleExecuteGet}
            onExecutePut={handleExecutePut}
            onExecuteDelete={handleExecuteDelete}
          />

          <LogConsole
            logs={logs}
            onClear={clearLogs}
          />
        </div>
      </div>
    </div>
  );
};

export default App;
