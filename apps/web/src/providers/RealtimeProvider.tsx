'use client';

import { createContext, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useAuth } from '@squamed/auth';

export const REALTIME_EVENT = 'squamed:realtime';

function resolveWsBase(): string {
  const explicit = process.env.NEXT_PUBLIC_WS_URL;
  if (explicit) return explicit;
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (apiBase) {
    return (
      apiBase.replace('https://', 'wss://').replace('http://', 'ws://').replace(/\/api\/v1\/?$/, '') +
      '/ws'
    );
  }
  return 'ws://localhost:8080/ws';
}

const WS_BASE = resolveWsBase();

export type RealtimeFrame = Record<string, unknown>;

interface RealtimeContextValue {
  connected: boolean;
  lastEvent: RealtimeFrame | null;
}

const RealtimeContext = createContext<RealtimeContextValue>({
  connected: false,
  lastEvent: null,
});

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { accessToken } = useAuth();
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState<RealtimeFrame | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (!accessToken) {
      setConnected(false);
      return;
    }

    let disposed = false;
    let socket: WebSocket | null = null;

    try {
      socket = new WebSocket(`${WS_BASE}?token=${accessToken}`);
    } catch {
      setConnected(false);
      return;
    }

    socket.onopen = () => {
      if (!disposed) setConnected(true);
    };

    socket.onmessage = (event: MessageEvent) => {
      if (disposed) return;
      let parsed: unknown = null;
      try {
        parsed = JSON.parse(event.data as string);
      } catch {
        return;
      }
      if (typeof parsed !== 'object' || parsed === null) return;
      const frame = parsed as RealtimeFrame;
      setLastEvent(frame);
      window.dispatchEvent(new CustomEvent<RealtimeFrame>(REALTIME_EVENT, { detail: frame }));
    };

    socket.onclose = () => {
      if (!disposed) setConnected(false);
    };

    socket.onerror = () => {
      if (!disposed) setConnected(false);
    };

    return () => {
      disposed = true;
      if (socket) socket.close();
    };
  }, [accessToken]);

  return (
    <RealtimeContext.Provider value={{ connected, lastEvent }}>
      {children}
    </RealtimeContext.Provider>
  );
}

export function useRealtime(): RealtimeContextValue {
  return useContext(RealtimeContext);
}

export function useNotificationsRealtime(onNotification: (notification: unknown) => void) {
  const callbackRef = useRef(onNotification);
  callbackRef.current = onNotification;

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<RealtimeFrame>).detail;
      if (detail && detail.type === 'notification.created') {
        callbackRef.current(detail.notification);
      }
    };
    window.addEventListener(REALTIME_EVENT, listener);
    return () => window.removeEventListener(REALTIME_EVENT, listener);
  }, []);
}