import { create } from 'zustand';
import type { SystemAlert, AlertStatus } from '../types';
import client from '../api/client';

// Keep a rolling window of the latest 200 alerts to avoid unbounded growth
const MAX_ALERTS = 200;

interface AlertStore {
  alerts:     SystemAlert[];
  loading:    boolean;
  error:      string | null;
  addAlert:   (alert: SystemAlert) => void;
  clearAlerts: () => Promise<void>;
  markAcknowledged: (alertId: number) => Promise<void>;
  unreadCount: number;
  fetchAlerts: () => Promise<void>;
}

export const useAlertStore = create<AlertStore>((set, get) => ({
  alerts:      [],
  loading:     false,
  error:       null,
  unreadCount: 0,

  addAlert: (alert) =>
    set((state) => {
      const deduplicated = state.alerts.filter((a) => a.alertId !== alert.alertId);
      const next = [alert, ...deduplicated].slice(0, MAX_ALERTS);
      return { alerts: next, unreadCount: state.unreadCount + 1 };
    }),

  clearAlerts: async () => {
    set({ loading: true, error: null });
    try {
      await client.post('/alerts/clear');
      await get().fetchAlerts();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      set({ error: msg ?? 'Failed to clear alerts.', loading: false });
      // Re-fetch to restore server state
      await get().fetchAlerts();
    }
  },

  markAcknowledged: async (alertId) => {
    // Optimistic update
    set((state) => {
      const target = state.alerts.find((a) => a.alertId === alertId);
      const alreadyAcked = !target || target.status === 'ACKNOWLEDGED';
      return {
        alerts: state.alerts.map((a) =>
          a.alertId === alertId ? { ...a, status: 'ACKNOWLEDGED' as AlertStatus } : a,
        ),
        unreadCount: alreadyAcked ? state.unreadCount : Math.max(0, state.unreadCount - 1),
      };
    });

    try {
      await client.patch(`/alerts/${alertId}/acknowledge`);
      // Re-fetch to sync with server state
      await get().fetchAlerts();
    } catch (err: unknown) {
      // On failure, restore server state
      await get().fetchAlerts();
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      set({ error: msg ?? 'Failed to acknowledge alert.' });
    }
  },

  fetchAlerts: async () => {
    set({ loading: true, error: null });
    try {
      const res = await client.get<SystemAlert[]>('/alerts');
      set({ alerts: res.data, loading: false });
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      set({ error: msg ?? 'Failed to load alerts.', loading: false });
    }
  },
}));