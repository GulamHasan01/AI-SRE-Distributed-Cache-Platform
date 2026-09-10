import React, { useState, useEffect } from 'react';
import { ShieldAlert, AlertTriangle, CheckCircle, Flame, RefreshCw, Cpu, Server, FileText } from 'lucide-react';

interface EvidenceItem {
  source: string;
  tool: string;
  collected_at: string;
  summary: string;
}

interface OodaStep {
  phase: string;
  summary: string;
  timestamp: string;
}

interface Incident {
  incident_id: string;
  affected_service: string;
  severity: string;
  status: string;
  detected_at: string;
  resolved_at?: string;
  resolution_time_seconds?: number;
  alert_name?: string;
  alert_description?: string;
  symptoms: string[];
  evidence: EvidenceItem[];
  ooda_steps: OodaStep[];
  root_cause?: string;
  confidence?: string;
  diagnosis_summary?: string;
  planned_actions: string[];
  approval_id?: string;
  approval_status?: string;
  actions_executed: string[];
  tools_called: string[];
  verification_result?: string;
  postmortem_path?: string;
}

interface SreIncidentPanelProps {
  targetGateway: string;
  targetAgent?: string;
}

export const SreIncidentPanel: React.FC<SreIncidentPanelProps> = ({
  targetGateway,
  targetAgent = "http://localhost:9090"
}) => {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [activeIncident, setActiveIncident] = useState<Incident | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const fetchIncidents = async () => {
    try {
      const res = await fetch(`${targetAgent}/api/sre/incidents`);
      if (res.ok) {
        const data = await res.json();
        const list: Incident[] = data.incidents || [];
        setIncidents(list);
        const active = list.find(i => !['RESOLVED', 'REJECTED', 'FAILED'].includes(i.status));
        setActiveIncident(active || (list.length > 0 ? list[0] : null));
      }
    } catch (e) {
      // Agent offline or running standalone
    }
  };

  useEffect(() => {
    fetchIncidents();
    const interval = setInterval(fetchIncidents, 2000);
    return () => clearInterval(interval);
  }, [targetAgent]);

  const handleApprove = async (incidentId: string) => {
    setLoading(true);
    try {
      const res = await fetch(`${targetAgent}/api/sre/incidents/${incidentId}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decided_by: 'dashboard_operator' }),
      });
      if (res.ok) {
        setActionMessage('Remediation APPROVED! Agent will execute actions.');
      } else {
        setActionMessage('Failed to approve remediation.');
      }
      await fetchIncidents();
    } catch (err: any) {
      setActionMessage(`Approval failed: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async (incidentId: string) => {
    setLoading(true);
    try {
      const res = await fetch(`${targetAgent}/api/sre/incidents/${incidentId}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decided_by: 'dashboard_operator' }),
      });
      if (res.ok) {
        setActionMessage('Remediation REJECTED. Incident marked as escalated.');
      }
      await fetchIncidents();
    } catch (err: any) {
      setActionMessage(`Rejection failed: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleTriggerChaos = async (nodeId: string) => {
    setLoading(true);
    setActionMessage(`Triggering 512MB heap pressure on ${nodeId}...`);
    try {
      const port = nodeId === 'node-1' ? 8081 : nodeId === 'node-2' ? 8082 : 8083;
      await fetch(`http://localhost:${port}/api/v1/chaos/memory-pressure?targetMb=512`, {
        method: 'POST',
      });
      // Also notify agent
      await fetch(`${targetAgent}/api/sre/incidents/trigger`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          node_id: nodeId,
          alert_name: 'JvmHeapUsageCritical',
          severity: 'CRITICAL',
          summary: `JVM heap critical on ${nodeId} (>90% used)`,
          description: `Simulated OOM failure injected via dashboard.`,
        }),
      });
      setActionMessage(`Failure injected on ${nodeId}! Agent investigation started.`);
      await fetchIncidents();
    } catch (e: any) {
      setActionMessage(`Simulation note: ${e.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card-panel" style={{ marginTop: '2rem', padding: '1.5rem', background: 'rgba(30, 41, 59, 0.7)', borderRadius: '12px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', borderBottom: '1px solid rgba(255, 255, 255, 0.08)', paddingBottom: '1rem' }}>
        <div>
          <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', color: '#f8fafc', fontSize: '1.4rem' }}>
            <ShieldAlert color="#06b6d4" size={24} />
            Autonomous AI SRE Incident Response Console
          </h2>
          <p style={{ color: '#94a3b8', fontSize: '0.9rem', marginTop: '0.2rem' }}>
            OODA Loop Engine (Observe → Orient → Decide → Act → Verify) with Human-In-The-Loop Approval Gate
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            onClick={() => handleTriggerChaos('node-2')}
            disabled={loading}
            style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: 'linear-gradient(135deg, #ef4444, #dc2626)', color: '#fff', border: 'none', padding: '0.5rem 1rem', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem' }}
          >
            <Flame size={16} />
            Inject Chaos (node-2)
          </button>
          <button
            onClick={fetchIncidents}
            style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: 'rgba(255,255,255,0.1)', color: '#f8fafc', border: 'none', padding: '0.5rem 1rem', borderRadius: '6px', cursor: 'pointer', fontSize: '0.85rem' }}
          >
            <RefreshCw size={14} />
            Refresh
          </button>
        </div>
      </div>

      {actionMessage && (
        <div style={{ padding: '0.75rem 1rem', marginBottom: '1rem', borderRadius: '6px', background: 'rgba(6, 182, 212, 0.1)', border: '1px solid rgba(6, 182, 212, 0.3)', color: '#06b6d4', fontSize: '0.9rem' }}>
          {actionMessage}
        </div>
      )}

      {/* Active Incident Inspection */}
      {activeIncident ? (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
          {/* Left: OODA Lifecycle State */}
          <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '1.25rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.05)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <span style={{ fontSize: '1.1rem', fontWeight: 600, color: '#38bdf8' }}>
                {activeIncident.incident_id}
              </span>
              <span style={{
                padding: '0.25rem 0.6rem',
                borderRadius: '9999px',
                fontSize: '0.75rem',
                fontWeight: 700,
                background: activeIncident.status === 'RESOLVED' ? 'rgba(16, 185, 129, 0.2)' : activeIncident.status === 'AWAITING_APPROVAL' ? 'rgba(245, 158, 11, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                color: activeIncident.status === 'RESOLVED' ? '#10b981' : activeIncident.status === 'AWAITING_APPROVAL' ? '#f59e0b' : '#ef4444',
              }}>
                {activeIncident.status}
              </span>
            </div>

            <div style={{ fontSize: '0.85rem', color: '#cbd5e1', marginBottom: '0.75rem' }}>
              <strong>Affected Service:</strong> <code style={{ color: '#06b6d4' }}>{activeIncident.affected_service}</code> | <strong>Severity:</strong> {activeIncident.severity}
            </div>

            <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginBottom: '1rem' }}>
              <strong>Alert:</strong> {activeIncident.alert_name} — {activeIncident.alert_description}
            </div>

            {/* OODA Progress Timeline */}
            <h4 style={{ fontSize: '0.9rem', color: '#94a3b8', marginBottom: '0.6rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              OODA Loop Steps
            </h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: '200px', overflowY: 'auto' }}>
              {activeIncident.ooda_steps.map((step, idx) => (
                <div key={idx} style={{ fontSize: '0.8rem', padding: '0.5rem', background: 'rgba(255,255,255,0.03)', borderRadius: '4px', borderLeft: `3px solid ${step.phase === 'OBSERVE' ? '#38bdf8' : step.phase === 'ORIENT' ? '#a855f7' : step.phase === 'DECIDE' ? '#f59e0b' : step.phase === 'ACT' ? '#ec4899' : '#10b981'}` }}>
                  <span style={{ fontWeight: 700, marginRight: '0.4rem', color: '#f1f5f9' }}>[{step.phase}]</span>
                  <span style={{ color: '#cbd5e1' }}>{step.summary}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Right: AI Diagnosis & HITL Approval */}
          <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '1.25rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.05)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <div>
              <h3 style={{ fontSize: '1rem', color: '#f8fafc', marginBottom: '0.6rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Cpu size={18} color="#a855f7" />
                AI Root Cause Analysis (RCA)
              </h3>
              <div style={{ background: 'rgba(0,0,0,0.3)', padding: '0.85rem', borderRadius: '6px', fontSize: '0.85rem', color: '#e2e8f0', lineHeight: 1.5, marginBottom: '1rem', border: '1px solid rgba(255,255,255,0.05)' }}>
                <p><strong>Diagnosis:</strong> {activeIncident.diagnosis_summary || 'Analyzing telemetry...'}</p>
                <p style={{ marginTop: '0.4rem' }}><strong>Root Cause:</strong> {activeIncident.root_cause || 'Investigating logs and memory...'}</p>
                <p style={{ marginTop: '0.4rem' }}><strong>Confidence:</strong> <span style={{ color: activeIncident.confidence === 'HIGH' ? '#10b981' : '#f59e0b', fontWeight: 600 }}>{activeIncident.confidence || 'PENDING'}</span></p>
              </div>

              <h4 style={{ fontSize: '0.85rem', color: '#94a3b8', marginBottom: '0.5rem' }}>Planned Remediation:</h4>
              <ul style={{ paddingLeft: '1.2rem', fontSize: '0.8rem', color: '#cbd5e1', marginBottom: '1rem' }}>
                {activeIncident.planned_actions.map((act, idx) => (
                  <li key={idx} style={{ marginBottom: '0.2rem' }}><code>{act}</code></li>
                ))}
              </ul>
            </div>

            {/* Approval Gate */}
            {activeIncident.status === 'AWAITING_APPROVAL' && (
              <div style={{ background: 'rgba(245, 158, 11, 0.1)', padding: '1rem', borderRadius: '8px', border: '1px solid rgba(245, 158, 11, 0.3)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#f59e0b', fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.5rem' }}>
                  <AlertTriangle size={18} />
                  Human-In-The-Loop Approval Gate
                </div>
                <p style={{ fontSize: '0.8rem', color: '#cbd5e1', marginBottom: '0.75rem' }}>
                  High-risk mutating action requires explicit approval before executing node drain and restart.
                </p>
                <div style={{ display: 'flex', gap: '0.75rem' }}>
                  <button
                    onClick={() => handleApprove(activeIncident.incident_id)}
                    disabled={loading}
                    style={{ flex: 1, padding: '0.6rem', background: '#10b981', color: '#fff', border: 'none', borderRadius: '6px', fontWeight: 700, cursor: 'pointer', fontSize: '0.85rem' }}
                  >
                    [ APPROVE ]
                  </button>
                  <button
                    onClick={() => handleReject(activeIncident.incident_id)}
                    disabled={loading}
                    style={{ flex: 1, padding: '0.6rem', background: '#ef4444', color: '#fff', border: 'none', borderRadius: '6px', fontWeight: 700, cursor: 'pointer', fontSize: '0.85rem' }}
                  >
                    [ REJECT ]
                  </button>
                </div>
              </div>
            )}

            {activeIncident.status === 'RESOLVED' && (
              <div style={{ background: 'rgba(16, 185, 129, 0.1)', padding: '1rem', borderRadius: '8px', border: '1px solid rgba(16, 185, 129, 0.3)', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <CheckCircle color="#10b981" size={24} />
                <div style={{ fontSize: '0.85rem', color: '#cbd5e1' }}>
                  <strong>Incident Resolved in {Math.round(activeIncident.resolution_time_seconds || 0)}s</strong>
                  <p style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.2rem' }}>
                    Verification verified node health, HTTP ping, and consistent hash routing.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8' }}>
          No active incidents. The cluster is running normally. Click "Inject Chaos" to simulate an incident.
        </div>
      )}

      {/* Incident History Table */}
      {incidents.length > 0 && (
        <div>
          <h3 style={{ fontSize: '1rem', color: '#f8fafc', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <FileText size={18} color="#06b6d4" />
            Recent Incidents & Postmortems
          </h3>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', color: '#94a3b8' }}>
                  <th style={{ padding: '0.5rem' }}>ID</th>
                  <th style={{ padding: '0.5rem' }}>Service</th>
                  <th style={{ padding: '0.5rem' }}>Severity</th>
                  <th style={{ padding: '0.5rem' }}>Status</th>
                  <th style={{ padding: '0.5rem' }}>Detected At</th>
                  <th style={{ padding: '0.5rem' }}>Resolution</th>
                  <th style={{ padding: '0.5rem' }}>Action</th>
                </tr>
              </thead>
              <tbody>
                {incidents.slice(0, 5).map((inc) => (
                  <tr
                    key={inc.incident_id}
                    onClick={() => setActiveIncident(inc)}
                    style={{
                      borderBottom: '1px solid rgba(255,255,255,0.05)',
                      cursor: 'pointer',
                      background: activeIncident?.incident_id === inc.incident_id ? 'rgba(255,255,255,0.05)' : 'transparent',
                    }}
                  >
                    <td style={{ padding: '0.5rem', color: '#38bdf8', fontWeight: 600 }}>{inc.incident_id}</td>
                    <td style={{ padding: '0.5rem' }}>{inc.affected_service}</td>
                    <td style={{ padding: '0.5rem' }}>{inc.severity}</td>
                    <td style={{ padding: '0.5rem' }}>
                      <span style={{
                        padding: '0.2rem 0.5rem',
                        borderRadius: '4px',
                        fontSize: '0.7rem',
                        background: inc.status === 'RESOLVED' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                        color: inc.status === 'RESOLVED' ? '#10b981' : '#f59e0b',
                      }}>
                        {inc.status}
                      </span>
                    </td>
                    <td style={{ padding: '0.5rem', color: '#94a3b8' }}>{inc.detected_at.split('T')[1]?.slice(0, 8)}</td>
                    <td style={{ padding: '0.5rem', color: '#94a3b8' }}>{inc.resolution_time_seconds ? `${Math.round(inc.resolution_time_seconds)}s` : '—'}</td>
                    <td style={{ padding: '0.5rem' }}>
                      <button
                        onClick={(e) => { e.stopPropagation(); setActiveIncident(inc); }}
                        style={{ padding: '0.25rem 0.5rem', background: 'rgba(255,255,255,0.1)', color: '#f8fafc', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '0.75rem' }}
                      >
                        Inspect
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
