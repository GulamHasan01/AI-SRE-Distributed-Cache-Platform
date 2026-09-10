import React from 'react';

interface StatsProps {
  totalKeys: number;
  totalHits: number;
  totalMisses: number;
  totalReplAttempted: number;
  totalReplSucceeded: number;
  totalReplFailed: number;
  activeNodesCount: number;
  totalNodesCount: number;
  avgUsagePercent: number;
}

export const StatsCards: React.FC<StatsProps> = ({
  totalKeys,
  totalHits,
  totalMisses,
  totalReplAttempted,
  totalReplSucceeded,
  totalReplFailed,
  activeNodesCount,
  totalNodesCount,
  avgUsagePercent
}) => {
  const hitRatio = (totalHits + totalMisses) === 0 ? 0.0 : (totalHits * 100.0) / (totalHits + totalMisses);
  const replRatio = totalReplAttempted === 0 ? 100.0 : (totalReplSucceeded * 100.0) / totalReplAttempted;

  return (
    <div className="stats-grid">
      <div className="stat-card">
        <div className="label">Cluster-wide Keys</div>
        <div className="value">{totalKeys}</div>
        <div className="footer-info">Aggregated active keys</div>
      </div>
      <div className="stat-card">
        <div className="label">Aggregate Hit Rate</div>
        <div className="value">{hitRatio.toFixed(1)}%</div>
        <div className="footer-info">
          Hits: {totalHits} | Misses: {totalMisses}
        </div>
      </div>
      <div className="stat-card">
        <div className="label">Replication Health</div>
        <div className="value">{replRatio.toFixed(1)}%</div>
        <div className="footer-info">
          Succeeded: {totalReplSucceeded} | Failed: {totalReplFailed}
        </div>
      </div>
      <div className="stat-card">
        <div className="label">Active Node Ring</div>
        <div className="value">
          {activeNodesCount} / {totalNodesCount}
        </div>
        <div className="footer-info">
          Avg Usage: {avgUsagePercent.toFixed(1)}%
        </div>
      </div>
    </div>
  );
};
