import { create } from 'zustand';
import type { SystemAlert, AlertStatus } from '../types';

// Keep a rolling window of the latest 200 alerts to avoid unbounded growth
const MAX_ALERTS = 200;

interface AlertStore {
  alerts:     SystemAlert[];
  addAlert:   (alert: SystemAlert) => void;
  clearAlerts: () => void;
  markAcknowledged: (alertId: number) => void;
  unreadCount: number;
}

export const useAlertStore = create<AlertStore>((set) => ({
  alerts:      [],
  unreadCount: 0,

  addAlert: (alert) =>
    set((state) => {
      const deduplicated = state.alerts.filter((a) => a.alertId !== alert.alertId);
      const next = [alert, ...deduplicated].slice(0, MAX_ALERTS);
      return { alerts: next, unreadCount: state.unreadCount + 1 };
    }),

  clearAlerts: () => set({ alerts: [], unreadCount: 0 }),

  markAcknowledged: (alertId) =>
    set((state) => {
      const target = state.alerts.find((a) => a.alertId === alertId);
      const alreadyAcked = !target || target.status === 'ACKNOWLEDGED';
      return {
        alerts: state.alerts.map((a) =>
          a.alertId === alertId ? { ...a, status: 'ACKNOWLEDGED' as AlertStatus } : a,
        ),
        unreadCount: alreadyAcked ? state.unreadCount : Math.max(0, state.unreadCount - 1),
      };
    }),
}));
