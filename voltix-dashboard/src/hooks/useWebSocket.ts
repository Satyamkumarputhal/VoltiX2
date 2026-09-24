import { useEffect, useRef, useCallback, useState } from 'react';
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import type { SystemAlert } from '../types';
import { useAlertStore } from '../store/alertStore';

type WsStatus = 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR';

interface UseWebSocketReturn {
  status:      WsStatus;
  lastMessage: SystemAlert | null;
  reconnectAttempts: number;
}

// Use the Vite dev-server proxy for WebSocket connections (/ws is proxied
// to localhost:8080 with ws:true in vite.config.ts). This avoids hardcoding
// a host/port and works in any environment where the proxy is configured.
// In production, the same relative path resolves against the serving origin.
function getWsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/alerts/websocket`;
}

const MAX_BACKOFF_MS = 30_000;

function computeBackoff(attempt: number): number {
  // Exponential: 1s, 2s, 4s, 8s, 16s, 30s (capped)
  return Math.min(1_000 * Math.pow(2, attempt), MAX_BACKOFF_MS);
}

export function useWebSocket(): UseWebSocketReturn {
  const addAlert       = useAlertStore((s) => s.addAlert);
  const [status, setStatus]             = useState<WsStatus>('CONNECTING');
  const [lastMessage, setLastMessage]   = useState<SystemAlert | null>(null);
  const [reconnectAttempts, setAttempts] = useState(0);

  const clientRef      = useRef<Client | null>(null);
  const subscriptionRef = useRef<StompSubscription | null>(null);
  const reconnectTimer  = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const attemptRef      = useRef(0);
  const unmountedRef    = useRef(false);

  const connect = useCallback(() => {
    if (unmountedRef.current) return;
    setStatus('CONNECTING');

    const stompClient = new Client({
      // Use native browser WebSocket directly — no SockJS needed for modern browsers
      brokerURL: getWsUrl(),
      reconnectDelay: 0, // We manage reconnect ourselves for precise backoff

      onConnect: () => {
        if (unmountedRef.current) return;
        attemptRef.current = 0;
        setAttempts(0);
        setStatus('CONNECTED');

        subscriptionRef.current = stompClient.subscribe(
          '/topic/alerts',
          (frame: IMessage) => {
            try {
              const alert: SystemAlert = JSON.parse(frame.body);
              setLastMessage(alert);
              addAlert(alert);
            } catch (e) {
              console.error('[VoltiX WS] Failed to parse alert payload:', e);
            }
          },
        );
      },

      onStompError: (frame) => {
        console.error('[VoltiX WS] STOMP error:', frame.headers['message']);
        setStatus('ERROR');
        scheduleReconnect();
      },

      onDisconnect: () => {
        if (unmountedRef.current) return;
        setStatus('DISCONNECTED');
        scheduleReconnect();
      },

      onWebSocketClose: () => {
        if (unmountedRef.current) return;
        setStatus('DISCONNECTED');
        scheduleReconnect();
      },
    });

    stompClient.activate();
    clientRef.current = stompClient;
  }, [addAlert]);

  const scheduleReconnect = useCallback(() => {
    if (unmountedRef.current) return;
    clearTimeout(reconnectTimer.current);
    const delay = computeBackoff(attemptRef.current);
    attemptRef.current += 1;
    setAttempts(attemptRef.current);
    console.info(`[VoltiX WS] Reconnecting in ${delay}ms (attempt ${attemptRef.current})`);
    reconnectTimer.current = setTimeout(connect, delay);
  }, [connect]);

  useEffect(() => {
    unmountedRef.current = false;
    connect();

    return () => {
      unmountedRef.current = true;
      clearTimeout(reconnectTimer.current);
      subscriptionRef.current?.unsubscribe();
      clientRef.current?.deactivate();
    };
  }, [connect]);

  return { status, lastMessage, reconnectAttempts };
}
