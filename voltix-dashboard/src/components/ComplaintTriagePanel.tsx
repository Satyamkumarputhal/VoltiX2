import React, { useState, useEffect, useCallback } from 'react';
import { ClipboardCheck, ChevronUp, X, Loader2, AlertCircle, RefreshCw } from 'lucide-react';
import client from '../api/client';
import type { PublicComplaint, ComplaintStatus } from '../types';

const STATUS_LABELS: Record<ComplaintStatus, string> = {
  PENDING_VERIFICATION: 'Pending',
  VERIFIED:             'Verified',
  ESCALATED:            'Escalated',
  REJECTED:             'Rejected',
};

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

interface TriageAction {
  complaintId: number;
  status:      'loading' | 'error';
}

export const ComplaintTriagePanel = React.memo(function ComplaintTriagePanel() {
  const [complaints, setComplaints]     = useState<PublicComplaint[]>([]);
  const [loading, setLoading]           = useState(true);
  const [initialLoad, setInitialLoad]   = useState(true);
  const [error, setError]               = useState<string | null>(null);
  const [triaging, setTriaging]         = useState<Record<number, TriageAction['status']>>({});

  const fetchComplaints = useCallback(async () => {
    if (initialLoad) setLoading(true);
    setError(null);
    try {
      const res = await client.get<PublicComplaint[]>('/complaints', {
        params: { status: 'PENDING_VERIFICATION' },
      });
      setComplaints(res.data);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Failed to load complaints. Check authentication.');
    } finally {
      setLoading(false);
      setInitialLoad(false);
    }
  }, [initialLoad]);

  useEffect(() => {
    fetchComplaints();
    // Poll every 15s for new complaints — no WebSocket channel exists for
    // complaints (only alerts use STOMP), so polling is the pragmatic choice.
    const interval = setInterval(fetchComplaints, 15_000);
    return () => clearInterval(interval);
  }, [fetchComplaints]);

  const handleTriage = useCallback(
    async (complaintId: number, newStatus: ComplaintStatus) => {
      setTriaging((prev) => ({ ...prev, [complaintId]: 'loading' }));
      try {
        await client.patch(`/complaints/${complaintId}/triage`, null, {
          params: { status: newStatus },
        });
        setComplaints((prev) => prev.filter((c) => c.complaintId !== complaintId));
      } catch {
        setTriaging((prev) => ({ ...prev, [complaintId]: 'error' }));
        setTimeout(() => setTriaging((prev) => {
          const next = { ...prev };
          delete next[complaintId];
          return next;
        }), 3000);
      }
    },
    [],
  );

  return (
    <div className="flex flex-col h-full rounded-lg border border-grid-border bg-grid-surface overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-grid-border">
        <div className="flex items-center gap-2">
          <ClipboardCheck className="w-4 h-4 text-accent-amber" />
          <h2 className="text-sm font-semibold text-white">Complaint Triage</h2>
          {complaints.length > 0 && (
            <span className="px-1.5 py-0.5 text-[10px] rounded-full bg-amber-500/20 text-amber-400 font-mono">
              {complaints.length}
            </span>
          )}
        </div>
        <button
          onClick={fetchComplaints}
          disabled={loading}
          className="text-grid-muted hover:text-white transition-colors disabled:opacity-40"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto">
        {loading && (
          <div className="flex items-center justify-center h-32 gap-2 text-grid-muted">
            <Loader2 className="w-4 h-4 animate-spin" />
            <span className="text-xs">Loading complaints…</span>
          </div>
        )}

        {error && !loading && (
          <div className="flex flex-col items-center gap-2 p-6 text-center">
            <AlertCircle className="w-6 h-6 text-accent-orange" />
            <p className="text-xs text-accent-orange">{error}</p>
            <button onClick={fetchComplaints} className="text-xs text-accent-blue hover:underline">Retry</button>
          </div>
        )}

        {!loading && !error && complaints.length === 0 && (
          <div className="flex flex-col items-center justify-center h-32 gap-2 text-grid-muted">
            <ClipboardCheck className="w-6 h-6 opacity-30" />
            <p className="text-xs">No complaints pending verification</p>
          </div>
        )}

        {!loading && complaints.map((complaint) => {
          const isTriaging = triaging[complaint.complaintId] === 'loading';
          const hasError   = triaging[complaint.complaintId] === 'error';

          return (
            <div
              key={complaint.complaintId}
              className="px-4 py-3 border-b border-grid-border/40 hover:bg-white/5 transition-colors animate-fade-in"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-[10px] font-mono text-grid-muted">
                      #{complaint.complaintId}
                    </span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-grid-raised text-grid-muted font-mono">
                      Zone {complaint.zoneId}
                    </span>
                    <span className="text-[10px] text-grid-muted">{timeAgo(complaint.submittedAt)}</span>
                  </div>
                  <p className="text-xs font-medium text-white truncate">{complaint.incidentAddress}</p>
                  <p className="text-xs text-grid-muted mt-0.5 line-clamp-2">{complaint.description}</p>
                </div>
              </div>

              {hasError && (
                <p className="text-[10px] text-accent-red mt-1">Triage action failed — please retry</p>
              )}

              {/* Explicit triage actions — unambiguous operator controls */}
              <div className="flex items-center gap-2 mt-2">
                <button
                  onClick={() => handleTriage(complaint.complaintId, 'ESCALATED')}
                  disabled={isTriaging}
                  className="flex items-center gap-1 px-2.5 py-1 text-[10px] rounded
                             bg-accent-amber/10 border border-accent-amber/30 text-amber-400
                             hover:bg-accent-amber/20 transition-colors disabled:opacity-40 font-mono"
                >
                  {isTriaging ? <Loader2 className="w-3 h-3 animate-spin" /> : <ChevronUp className="w-3 h-3" />}
                  Escalate
                </button>
                <button
                  onClick={() => handleTriage(complaint.complaintId, 'REJECTED')}
                  disabled={isTriaging}
                  className="flex items-center gap-1 px-2.5 py-1 text-[10px] rounded
                             bg-red-600/10 border border-red-600/30 text-red-400
                             hover:bg-red-600/20 transition-colors disabled:opacity-40 font-mono"
                >
                  <X className="w-3 h-3" />
                  Reject
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
});
