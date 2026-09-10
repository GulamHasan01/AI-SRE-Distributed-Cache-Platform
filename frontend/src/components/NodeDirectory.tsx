import React from 'react';
import type { PhysicalNode } from './HashRing';

interface NodeStats {
  totalKeys: number;
  usagePercent: number;
}

interface NodeDirectoryProps {
  clusterNodes: PhysicalNode[];
  nodeStatsMap: Record<string, NodeStats | null>;
  onTriggerNodeStateChange: (nodeId: string, state: 'UP' | 'DOWN') => void;
}

const colorPalette = ["#06b6d4", "#e0af68", "#bb9af3", "#9ece6a", "#f7768e", "#7aa2f7", "#7dcfff"];

export const NodeDirectory: React.FC<NodeDirectoryProps> = ({
  clusterNodes,
  nodeStatsMap,
  onTriggerNodeStateChange
}) => {
  const nodeColors: Record<string, string> = {};
  clusterNodes.forEach((node, idx) => {
    nodeColors[node.nodeId] = colorPalette[idx % colorPalette.length];
  });

  return (
    <div className="panel">
      <div className="panel-header">
        <h2>Nodes Directory <span className="subtitle">Active Registry Cluster Membership</span></h2>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table>
          <thead>
            <tr>
              <th>Node ID</th>
              <th>Endpoint</th>
              <th>Status</th>
              <th>Local Keys</th>
              <th>Usage</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {clusterNodes.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', color: '#94a3b8' }}>
                  Querying node directory...
                </td>
              </tr>
            ) : (
              clusterNodes.map(node => {
                const stats = nodeStatsMap[node.nodeId];
                const keysCount = stats ? stats.totalKeys : '-';
                const capacityStr = stats ? `${stats.usagePercent.toFixed(1)}%` : '-';

                return (
                  <tr key={node.nodeId}>
                    <td
                      style={{
                        fontWeight: 600,
                        borderLeft: `4px solid ${nodeColors[node.nodeId] || '#ccc'}`
                      }}
                    >
                      {node.nodeId}
                    </td>
                    <td className="mono">{node.host}:{node.port}</td>
                    <td>
                      <span className={`badge badge-${node.status.toLowerCase()}`}>
                        {node.status}
                      </span>
                    </td>
                    <td>{keysCount}</td>
                    <td>{capacityStr}</td>
                    <td>
                      {node.status === 'UP' ? (
                        <button
                          className="btn-danger"
                          style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                          onClick={() => onTriggerNodeStateChange(node.nodeId, 'DOWN')}
                        >
                          Force Fail
                        </button>
                      ) : (
                        <button
                          style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                          onClick={() => onTriggerNodeStateChange(node.nodeId, 'UP')}
                        >
                          Recover Node
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
