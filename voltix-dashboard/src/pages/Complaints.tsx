import React from 'react';
import { Loader2, AlertCircle, RefreshCw } from 'lucide-react';
import client from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { PublicComplaint, ComplaintStatus } from '../types';

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
  status: 'loading' | 'error';
}

function Complaints() {
  const { isAuthenticated } = useAuth();
  const [complaints, setComplaints] = React.useState<PublicComplaint[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [initialLoad, setInitialLoad] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [triaging, setTriaging] = React.useState<Record<number, TriageAction['status']>>({});

  const fetchComplaints = React.useCallback(async () => {
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

  React.useEffect(() => {
    if (!isAuthenticated) {
      setLoading(false);
      setInitialLoad(false);
      return;
    }
    fetchComplaints();
    const interval = setInterval(fetchComplaints, 15_000);
    return () => clearInterval(interval);
  }, [fetchComplaints, isAuthenticated]);

  const handleTriage = React.useCallback(
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
    <div className="h-screen bg-grid-base flex flex-col overflow-hidden">
      <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-5 shrink-0">
        <div className="flex items-center gap-3">
          <svg className="w-4 h-4 text-accent-amber" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 000 4h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
          </svg>
          <span className="font-semibold text-[15px] text-grid-text">Complaint Triage</span>
        </div>
      </header>

      <div className="flex-1 p-4 overflow-hidden">
        <div className="card flex flex-col h-full min-h-0">
          <div className="flex items-center justify-between px-4 py-3 border-b border-grid-border">
            <div className="flex items-center gap-2">
              <svg className="w-4 h-4 text-accent-amber" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 000 4h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
              </svg>
              <span className="font-semibold text-[14px] font-semibold text-white">Complaint Triage</span>
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

          <div className="flex-1 overflow-y-auto min-h-0">
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
                <svg className="w-6 h-6 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 000 4h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
                </svg>
                <p className="text-xs">No complaints pending verification</p>
              </div>
            )}

            {!loading && complaints.map((complaint) => {
              const isTriaging = triaging[complaint.complaintId] === 'loading';
              const hasError = triaging[complaint.complaintId] === 'error';

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

                  <div className="flex items-center gap-2 mt-2">
                    <button
                      onClick={() => handleTriage(complaint.complaintId, 'ESCALATED')}
                      disabled={isTriaging}
                      className="flex items-center gap-1 px-2.5 py-1 text-[10px] rounded
                                bg-accent-amber/10 border border-accent-amber/30 text-amber-400
                                hover:bg-accent-amber/20 transition-colors disabled:opacity-40 font-mono"
                    >
                      {isTriaging ? <Loader2 className="w-3 h-3 animate-spin" /> : <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 11l5-5m0 0l5 5m-5-5h12"/></svg>}
                      Escalate
                    </button>
                    <button
                      onClick={() => handleTriage(complaint.complaintId, 'REJECTED')}
                      disabled={isTriaging}
                      className="flex items-center gap-1 px-2.5 py-1 text-[10px] rounded
                                bg-red-600/10 border border-red-600/30 text-red-400
                                hover:bg-red-600/20 transition-colors disabled:opacity-40 font-mono"
                    >
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12"/></svg>
                      Reject
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

export default Complaints;