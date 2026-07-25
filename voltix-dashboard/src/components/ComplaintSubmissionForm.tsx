import React, { useState, useCallback, useRef } from 'react';
import { Send, CheckCircle, AlertCircle, Loader2 } from 'lucide-react';
import client from '../api/client';
import type { ComplaintSubmitResponse } from '../types';

interface FormState {
  incidentAddress: string;
  description:     string;
  zoneId:          string;
}

type SubmitStatus = 'idle' | 'submitting' | 'success' | 'rate_limited' | 'error';

// NOTE: No backend endpoint currently exposes a zone list (only GET /complaints
// exists). Adding one was explicitly out of scope for Phase 3. These values
// match the seeded grid_zones from V2_1__Seed_Tenants_And_Zones.sql.
// TODO: Replace with a real fetch when a GET /api/v1/zones endpoint is added.
const ZONES = [1, 2];

function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const update = useCallback((v: T) => {
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setDebounced(v), delay);
  }, [delay]);
  React.useEffect(() => { update(value); }, [value, update]);
  return debounced;
}

// ── Validation ───────────────────────────────────────────────
function validate(form: FormState): Record<string, string> {
  const errors: Record<string, string> = {};
  if (form.incidentAddress.trim().length < 10) {
    errors.incidentAddress = 'Please provide a full address (at least 10 characters)';
  }
  if (form.description.trim().length < 20) {
    errors.description = 'Please describe the issue in more detail (at least 20 characters)';
  }
  if (!form.zoneId) {
    errors.zoneId = 'Please select which grid zone the incident is in';
  }
  return errors;
}

// ── ComplaintSubmissionForm ──────────────────────────────────
export const ComplaintSubmissionForm = React.memo(function ComplaintSubmissionForm() {
  const [form, setForm]         = useState<FormState>({ incidentAddress: '', description: '', zoneId: '' });
  const [touched, setTouched]   = useState<Partial<Record<keyof FormState, boolean>>>({});
  const [submitStatus, setStatus] = useState<SubmitStatus>('idle');
  const [responseMsg, setMsg]   = useState('');

  const debouncedForm = useDebounce(form, 175);
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
        setMsg(`Your report has been submitted (reference #${res.data.complaintId ?? '—'}). A grid operator will review it within 24 hours.`);
        setForm({ incidentAddress: '', description: '', zoneId: '' });
        setTouched({});
      }
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 429) {
        setStatus('rate_limited');
        setMsg('You\'ve submitted too many reports recently. Please wait a few minutes before trying again.');
      } else {
        setStatus('error');
        setMsg('Something went wrong submitting your report. Please try again.');
      }
    }
  }, [form, isValid]);

  const inputBase = `
    w-full bg-grid-raised/50 border border-grid-border rounded-[10px]
    px-4 py-3 text-[15px] text-grid-text placeholder:text-grid-dim
    focus:outline-none focus:ring-2 focus:border-accent-amber/50
    focus:ring-accent-amber/20 transition-all
  `;

  const fieldClass = (name: keyof FormState) =>
    `${inputBase} ${touched[name] && errors[name] ? 'border-accent-red focus:ring-accent-red/20 focus:border-accent-red/50' : ''}`;

  return (
    <div className="bg-grid-surface border border-grid-border rounded-[16px] p-6 sm:p-8">

      {/* Success banner */}
      {submitStatus === 'success' && (
        <div className="flex items-start gap-3 p-4 mb-6 rounded-[10px] bg-accent-green/8 border border-accent-green/20 animate-fade-in">
          <CheckCircle className="w-5 h-5 text-accent-green shrink-0 mt-0.5" />
          <p className="text-[14px] text-accent-green leading-relaxed">{responseMsg}</p>
        </div>
      )}

      {/* Error/rate-limit banner */}
      {(submitStatus === 'error' || submitStatus === 'rate_limited') && (
        <div className="flex items-start gap-3 p-4 mb-6 rounded-[10px] bg-accent-red/8 border border-accent-red/20 animate-fade-in">
          <AlertCircle className="w-5 h-5 text-accent-red shrink-0 mt-0.5" />
          <p className="text-[14px] text-accent-red leading-relaxed">{responseMsg}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
        {/* Zone selector */}
        <div>
          <label className="block text-[13px] text-grid-muted mb-2 font-medium">
            Grid Zone
          </label>
          <select
            name="zoneId"
            value={form.zoneId}
            onChange={handleChange}
            onBlur={handleBlur}
            className={fieldClass('zoneId')}
          >
            <option value="">Select the zone where the issue occurred…</option>
            {ZONES.map((z) => <option key={z} value={z}>Zone {z}</option>)}
          </select>
          {touched.zoneId && errors.zoneId && (
            <p className="text-[12px] text-accent-red mt-1.5 ml-1">{errors.zoneId}</p>
          )}
        </div>

        {/* Address */}
        <div>
          <label className="block text-[13px] text-grid-muted mb-2 font-medium">
            Incident Location
          </label>
          <input
            type="text"
            name="incidentAddress"
            value={form.incidentAddress}
            onChange={handleChange}
            onBlur={handleBlur}
            placeholder="Street address or nearest landmark"
            className={fieldClass('incidentAddress')}
          />
          {touched.incidentAddress && errors.incidentAddress && (
            <p className="text-[12px] text-accent-red mt-1.5 ml-1">{errors.incidentAddress}</p>
          )}
        </div>

        {/* Description */}
        <div>
          <label className="block text-[13px] text-grid-muted mb-2 font-medium">
            What happened?
          </label>
          <textarea
            name="description"
            value={form.description}
            onChange={handleChange}
            onBlur={handleBlur}
            rows={5}
            placeholder="Describe what you observed — power outage, flickering lights, visible damage to equipment, unusual sounds…"
            className={`${fieldClass('description')} resize-none`}
          />
          {touched.description && errors.description && (
            <p className="text-[12px] text-accent-red mt-1.5 ml-1">{errors.description}</p>
          )}
        </div>

        {/* Submit button — amber accent, generous size */}
        <button
          type="submit"
          disabled={submitStatus === 'submitting'}
          className="flex items-center justify-center gap-2.5 w-full py-3.5 mt-2
                     rounded-[10px] bg-accent-amber hover:bg-amber-500
                     text-grid-base text-[15px] font-semibold
                     transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitStatus === 'submitting'
            ? <><Loader2 className="w-5 h-5 animate-spin" /> Submitting…</>
            : <><Send className="w-5 h-5" /> Submit Report</>
          }
        </button>
      </form>
    </div>
  );
});
