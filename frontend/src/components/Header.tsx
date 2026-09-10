import React from 'react';
import { Activity } from 'lucide-react';

interface HeaderProps {
  upCount: number;
  totalNodes: number;
  clusterHealthy: boolean;
  downCount: number;
  suspectCount: number;
  targetGateway: string;
  onGatewayChange: (url: string) => void;
}

export const Header: React.FC<HeaderProps> = ({
  upCount,
  totalNodes,
  clusterHealthy,
  downCount,
  suspectCount,
  targetGateway,
  onGatewayChange
}) => {
  return (
    <header>
      <div className="header-title">
        <h1>Distributed Cache Console</h1>
        <p>Decoupled React Client & Consistent Hashing Ring Monitor</p>
      </div>
      <div className="header-controls">
        <div
          id="cluster-health-badge"
          className={`cluster-status-badge ${!clusterHealthy ? 'degraded' : ''}`}
        >
          <Activity size={16} className="status-dot" />
          <span>
            {clusterHealthy
              ? `Cluster Healthy (${upCount}/${totalNodes} UP)`
              : `Cluster Degraded (DOWN: ${downCount} | SUSPECT: ${suspectCount})`
            }
          </span>
        </div>
        <div>
          <select
            value={targetGateway}
            onChange={(e) => onGatewayChange(e.target.value)}
          >
            <option value="http://localhost:8080">Unified API Gateway (Port 8080)</option>
          </select>
        </div>
      </div>
    </header>
  );
};
