import React from 'react';

export interface LogEntry {
  time: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'GATEWAY';
  msg: string;
  traceId?: string;
}

interface LogConsoleProps {
  logs: LogEntry[];
  onClear: () => void;
}

export const LogConsole: React.FC<LogConsoleProps> = ({ logs, onClear }) => {
  return (
    <div className="panel" style={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
      <div className="panel-header">
        <h2>Operational Trace Logs <span className="subtitle">Real-time Hops and API Trace IDs</span></h2>
        <button
          className="btn-secondary"
          style={{ padding: '0.3rem 0.6rem', fontSize: '0.75rem' }}
          onClick={onClear}
        >
          Clear
        </button>
      </div>
      <div className="console-logs">
        {logs.map((log, idx) => (
          <div key={idx} className="log-line">
            <span className="log-time">{log.time}</span>
            <span className={`log-level ${log.level.toLowerCase()}`}>
              {log.level}
            </span>
            <span className="log-msg">{log.msg}</span>
            {log.traceId && (
              <span className="log-trace">
                [traceId={log.traceId.substring(0, 8)}]
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
