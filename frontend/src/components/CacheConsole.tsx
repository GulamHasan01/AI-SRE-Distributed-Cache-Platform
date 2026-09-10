import React, { useState } from 'react';

interface CacheConsoleProps {
  onRouteKey: (key: string) => void;
  onExecuteGet: (key: string, onValueFound: (val: string, ttl: string) => void) => void;
  onExecutePut: (key: string, value: string, ttl: string) => void;
  onExecuteDelete: (key: string, onValueCleared: () => void) => void;
}

export const CacheConsole: React.FC<CacheConsoleProps> = ({
  onRouteKey,
  onExecuteGet,
  onExecutePut,
  onExecuteDelete
}) => {
  const [keyInput, setKeyInput] = useState('');
  const [valueInput, setValueInput] = useState('');
  const [ttlInput, setTtlInput] = useState('');

  const handleRoute = () => {
    if (!keyInput.trim()) return;
    onRouteKey(keyInput.trim());
  };

  const handleGet = () => {
    if (!keyInput.trim()) return;
    onExecuteGet(keyInput.trim(), (val, ttl) => {
      setValueInput(val);
      setTtlInput(ttl);
    });
  };

  const handlePut = () => {
    if (!keyInput.trim() || !valueInput.trim()) return;
    onExecutePut(keyInput.trim(), valueInput.trim(), ttlInput.trim());
  };

  const handleDelete = () => {
    if (!keyInput.trim()) return;
    onExecuteDelete(keyInput.trim(), () => {
      setValueInput('');
      setTtlInput('');
    });
  };

  return (
    <div className="panel">
      <div className="panel-header">
        <h2>Gateway Cache Operations <span className="subtitle">Interacts via Unified Router Gateway</span></h2>
      </div>
      <form className="crud-form" onSubmit={(e) => e.preventDefault()}>
        <div className="form-row">
          <label htmlFor="cache-key">Key</label>
          <input
            type="text"
            id="cache-key"
            placeholder="Enter key (e.g. user:profile:1001)"
            value={keyInput}
            onChange={(e) => setKeyInput(e.target.value)}
            required
          />
        </div>
        <div className="form-row" id="value-row">
          <label htmlFor="cache-value">Value</label>
          <input
            type="text"
            id="cache-value"
            placeholder="Enter string value (for PUT)"
            value={valueInput}
            onChange={(e) => setValueInput(e.target.value)}
          />
        </div>
        <div className="form-row" id="ttl-row">
          <label htmlFor="cache-ttl">TTL (sec)</label>
          <input
            type="number"
            id="cache-ttl"
            placeholder="Optional TTL in seconds (for PUT)"
            value={ttlInput}
            onChange={(e) => setTtlInput(e.target.value)}
          />
        </div>
        <div className="form-row-actions">
          <button type="button" className="btn-secondary" onClick={handleRoute}>
            Route Key
          </button>
          <button type="button" className="btn-secondary" onClick={handleGet}>
            GET
          </button>
          <button type="button" onClick={handlePut}>
            PUT
          </button>
          <button type="button" className="btn-danger" onClick={handleDelete}>
            DELETE
          </button>
        </div>
      </form>
    </div>
  );
};
