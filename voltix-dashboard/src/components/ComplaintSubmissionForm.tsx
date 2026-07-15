import React, { useState, useCallback, useRef } from 'react';
import { Send, CheckCircle, AlertCircle, Loader2, MapPin } from 'lucide-react';
import client from '../api/client';
import type { ComplaintSubmitResponse } from '../types';

interface FormState {
  incidentAddress: string;
  description:     string;
  zoneId:          string;
}

type SubmitStatus = 'idle' | 'submitting' | 'success' | 'rate_limited' | 'error';

const ZONES = [1, 2, 3, 4, 5]; // Extend with real zone fetch in production

function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const update = useCallback((v: T) => {
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setDebounced(v), delay);
  }, [delay]);
  // Trigger debounce on value change
  React.useEffect(() => { update(value); }, [value, update]);
  return debounced;
}

// ── Validation ───────────────────────────────────────────────
function validate(form: FormState): Record<string, string> {
  const errors: Record<string, string> = {};
  if (form.incidentAddress.trim().length < 10) {
    errors.incidentAddress = 'Address must be at least 10 characters';
  }
  if (form.description.trim().length < 20) {
    errors.description = 'Description must be at least 20 characters';
  }
  if (!form.zoneId) {
    errors.zoneId = 'Please select a zone';
  }
  return errors;
}

// ── ComplaintSubmissionForm ──────────────────────────────────
export const ComplaintSubmissionForm = React.memo(function ComplaintSubmissionForm() {
  const [form, setForm]         = useState<FormState>({ incidentAddress: '', description: '', zoneId: '' });
  const [touched, setTouched]   = useState<Partial<Record<keyof FormState, boolean>>>({});
  const [submitStatus, setStatus] = useState<SubmitStatus>('idle');
  const [responseMsg, setMsg]   = useState('');

  // Debounce validation — 500ms after user stops typing
  const debouncedForm = useDebounce(form, 500);
  const errors        = React.useMemo(() => validate(debouncedForm), [debouncedForm]);
  const isValid       = Object.keys(errors).length === 0;

  const handleChange = useCallback((
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }, []);

  const handleBlur = useCallback((e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setTouched((prev) => ({ ...prev, [e.target.name]: true }));
  }, []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setTouched({ incidentAddress: true, description: true, zoneId: true });
    if (!isValid) return;

    setStatus('submitting');
    try {
      const res = await client.post<ComplaintSubmitResponse>('/complaints/anonymous', {
        incidentAddress: form.incidentAddress,
        description:     form.description,
        zoneId:          Number(form.zoneId),
      });

      if (res.data.status === 'RATE_LIMITED') {
        setStatus('rate_limited');
        setMsg(res.data.message);
      } else {
        setStatus('success');
        setMsg(`Complaint #${res.data.complaintId ?? 'submitted'} — pending verification`);
        setForm({ incidentAddress: '', description: '', zoneId: '' });
        setTouched({});
      }
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 429) {
        setStatus('rate_limited');
        setMsg('Rate limit reached. Please wait before submitting again.');
      } else {
        setStatus('error');
        setMsg('Submission failed. Please try again.');
      }
    }
  }, [form, isValid]);

  const fieldClass = (name: keyof FormState) => `
    w-full bg-grid-raised border rounded px-3 py-2 text-sm text-white placeholder:text-grid-muted
    focus:outline-none focus:ring-1 transition-colors font-sans
    ${touched[name] && errors[name]
      ? 'border-accent-red focus:ring-accent-red/50'
      : 'border-grid-border focus:ring-accent-blue/50 focus:border-accent-blue'}
  `;

  return (
    <div className="rounded-lg border border-grid-border bg-grid-surface p-6 max-w-lg mx-auto">
      <div className="flex items-center gap-2 mb-5">
        <MapPin className="w-4 h-4 text-accent-blue" />
        <h2 className="text-sm font-semibold text-white">Report a Grid Incident</h2>
      </div>

      {/* Success / error banners */}
      {submitStatus === 'success' && (
        <div className="flex items-start gap-2 p-3 mb-4 rounded bg-accent-green/10 border border-accent-green/30 text-accent-green text-xs animate-fade-in">
          <CheckCircle className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{responseMsg}</span>
        </div>
      )}
      {(submitStatus === 'error' || submitStatus === 'rate_limited') && (
        <div className="flex items-start gap-2 p-3 mb-4 rounded bg-accent-orange/10 border border-accent-orange/30 text-orange-400 text-xs animate-fade-in">
          <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{responseMsg}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        {/* Zone selector */}
        <div>
          <label className="block text-xs text-grid-muted mb-1.5">Grid Zone *</label>
          <select
            name="zoneId"
            value={form.zoneId}
            onChange={handleChange}
            onBlur={handleBlur}
            className={fieldClass('zoneId')}
          >
            <option value="">Select zone…</option>
            {ZONES.map((z) => <option key={z} value={z}>Zone {z}</option>)}
          </select>
          {touched.zoneId && errors.zoneId && (
            <p className="text-[10px] text-accent-red mt-1">{errors.zoneId}</p>
          )}
        </div>

        {/* Address */}
        <div>
          <label className="block text-xs text-grid-muted mb-1.5">Incident Address *</label>
          <input
            type="text"
            name="incidentAddress"
            value={form.incidentAddress}
            onChange={handleChange}
            onBlur={handleBlur}
            placeholder="e.g. 45 Grid Street, Sector 7"
            className={fieldClass('incidentAddress')}
          />
          {touched.incidentAddress && errors.incidentAddress && (
            <p className="text-[10px] text-accent-red mt-1">{errors.incidentAddress}</p>
          )}
          <p className="text-[10px] text-grid-muted mt-1">{form.incidentAddress.length}/10 min chars</p>
        </div>

        {/* Description */}
        <div>
          <label className="block text-xs text-grid-muted mb-1.5">Incident Description *</label>
          <textarea
            name="description"
            value={form.description}
            onChange={handleChange}
            onBlur={handleBlur}
            rows={4}
            placeholder="Describe the incident clearly — power outage, flickering, equipment damage…"
            className={`${fieldClass('description')} resize-none`}
          />
          {touched.description && errors.description && (
            <p className="text-[10px] text-accent-red mt-1">{errors.description}</p>
          )}
          <p className="text-[10px] text-grid-muted mt-1">{form.description.length}/20 min chars</p>
        </div>

        <button
          type="submit"
          disabled={submitStatus === 'submitting'}
          className="flex items-center justify-center gap-2 w-full py-2.5 rounded
                     bg-accent-blue hover:bg-blue-500 text-white text-sm font-medium
                     transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitStatus === 'submitting'
            ? <><Loader2 className="w-4 h-4 animate-spin" /> Submitting…</>
            : <><Send className="w-4 h-4" /> Submit Report</>
          }
        </button>
      </form>
    </div>
  );
});
