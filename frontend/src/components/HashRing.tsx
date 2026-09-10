import React, { useState } from 'react';

export interface VirtualNode {
  positionHex: string;
  position: number;
  nodeId: string;
}

export interface PhysicalNode {
  nodeId: string;
  host: string;
  port: number;
  status: string;
}

interface HashRingProps {
  virtualNodes: VirtualNode[];
  clusterNodes: PhysicalNode[];
  activeKeyRoute: {
    hash: string;
    ownerNodeId: string;
  } | null;
}

const colorPalette = ["#06b6d4", "#e0af68", "#bb9af3", "#9ece6a", "#f7768e", "#7aa2f7", "#7dcfff"];

export const HashRing: React.FC<HashRingProps> = ({
  virtualNodes,
  clusterNodes,
  activeKeyRoute
}) => {
  const [tooltip, setTooltip] = useState<{
    show: boolean;
    x: number;
    y: number;
    title: string;
    nodeId: string;
    hashHex: string;
    decimal: string;
  }>({
    show: false,
    x: 0,
    y: 0,
    title: '',
    nodeId: '',
    hashHex: '',
    decimal: ''
  });

  const nodeColors: Record<string, string> = {};
  clusterNodes.forEach((node, idx) => {
    nodeColors[node.nodeId] = colorPalette[idx % colorPalette.length];
  });

  const ringOffset = 9223372036854775808n;
  const ringMaxRange = 18446744073709551616n;
  const r = 150;
  const center = 200;

  const getCoordinates = (hashVal: bigint) => {
    const positionBig = hashVal + ringOffset;
    const ratio = Number(positionBig) / Number(ringMaxRange);
    const angle = ratio * 2 * Math.PI;
    const cx = center + r * Math.cos(angle);
    const cy = center + r * Math.sin(angle);
    return { cx, cy, angle, ratio };
  };

  const findClosestVnode = (hashVal: bigint) => {
    let closest: VirtualNode | null = null;
    let minDiff = ringMaxRange;

    virtualNodes.forEach(vnode => {
      const vnodePosBig = BigInt(vnode.position) + ringOffset;
      const keyPosBig = hashVal + ringOffset;
      let diff = vnodePosBig - keyPosBig;
      if (diff < 0n) {
        diff += ringMaxRange;
      }
      if (diff < minDiff) {
        minDiff = diff;
        closest = vnode;
      }
    });

    return { closest, minDiff };
  };

  let keyPointerCoords = null;
  let arcPathD = "";

  if (activeKeyRoute && activeKeyRoute.hash) {
    try {
      const keyHashBig = BigInt(activeKeyRoute.hash);
      keyPointerCoords = getCoordinates(keyHashBig);

      const { closest, minDiff } = findClosestVnode(keyHashBig);
      if (closest) {
        const closestHashBig = BigInt((closest as VirtualNode).position);
        const vnodeCoords = getCoordinates(closestHashBig);
        const largeArcFlag = minDiff > (ringMaxRange / 2n) ? 1 : 0;
        arcPathD = `M ${keyPointerCoords.cx} ${keyPointerCoords.cy} A ${r} ${r} 0 ${largeArcFlag} 1 ${vnodeCoords.cx} ${vnodeCoords.cy}`;
      }
    } catch (e) {
      console.error("Failed calculating hash coordinates:", e);
    }
  }

  const handleMouseEnter = (e: React.MouseEvent, vnode: VirtualNode) => {
    setTooltip({
      show: true,
      x: e.pageX + 10,
      y: e.pageY + 10,
      title: 'Virtual Node',
      nodeId: vnode.nodeId,
      hashHex: vnode.positionHex,
      decimal: String(vnode.position)
    });
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    setTooltip(prev => ({
      ...prev,
      x: e.pageX + 10,
      y: e.pageY + 10
    }));
  };

  const handleMouseLeave = () => {
    setTooltip(prev => ({ ...prev, show: false }));
  };

  return (
    <div className="panel">
      <div className="panel-header">
        <h2>Consistent Hash Ring <span className="subtitle">Virtual Nodes Layout (MD5 Key-Space)</span></h2>
      </div>
      <div className="ring-container">
        <svg className="ring-svg" viewBox="0 0 400 400">
          <circle className="ring-bg" cx={center} cy={center} r={160}></circle>
          <circle className="ring-track" cx={center} cy={center} r={r}></circle>

          <g>
            {virtualNodes.map((vnode, idx) => {
              const coords = getCoordinates(BigInt(vnode.position));
              const nodeColor = nodeColors[vnode.nodeId] || '#94a3b8';
              return (
                <circle
                  key={idx}
                  cx={coords.cx}
                  cy={coords.cy}
                  className="node-marker vnode-dot"
                  fill={nodeColor}
                  stroke="#000"
                  strokeWidth="0.5"
                  onMouseEnter={(e) => handleMouseEnter(e, vnode)}
                  onMouseMove={handleMouseMove}
                  onMouseLeave={handleMouseLeave}
                />
              );
            })}
          </g>

          {arcPathD && (
            <path className="key-arc" d={arcPathD} />
          )}

          {keyPointerCoords && (
            <circle
              cx={keyPointerCoords.cx}
              cy={keyPointerCoords.cy}
              className="key-pointer"
            />
          )}
        </svg>

        <div className="ring-legend">
          {clusterNodes.map((node) => (
            <div
              key={node.nodeId}
              className="legend-item"
              style={{
                textDecoration: node.status !== 'UP' ? 'line-through' : 'none',
                opacity: node.status !== 'UP' ? 0.5 : 1
              }}
            >
              <div
                className="legend-color"
                style={{
                  backgroundColor: nodeColors[node.nodeId],
                  opacity: node.status !== 'UP' ? 0.25 : 1
                }}
              />
              <span>{node.nodeId} ({node.status})</span>
            </div>
          ))}
        </div>

        {tooltip.show && (
          <div
            className="tooltip"
            style={{
              display: 'block',
              left: tooltip.x,
              top: tooltip.y
            }}
          >
            <strong>{tooltip.title}</strong><br />
            Node: {tooltip.nodeId}<br />
            Hash: {tooltip.hashHex}<br />
            Decimal: {tooltip.decimal}
          </div>
        )}
      </div>
    </div>
  );
};
