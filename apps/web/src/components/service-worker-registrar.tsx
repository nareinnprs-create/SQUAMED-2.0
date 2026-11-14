'use client';

import { useEffect, useRef } from 'react';
import { useAuth } from '@squamed/auth';
import { flushOutbox } from '@/lib/offline/outbox';

/**
 * Chunk-00 item 13 client runtime: registers the offline shell service worker
 * and drains the store-and-forward outbox when connectivity returns (plus once
 * per mount/token change, covering writes queued while the tab was closed).
 */
export function ServiceWorkerRegistrar() {
  const { accessToken } = useAuth();
  const tokenRef = useRef(accessToken);

  useEffect(() => {
    tokenRef.current = accessToken;
  }, [accessToken]);

  useEffect(() => {
    if ('serviceWorker' in navigator) {
      void navigator.serviceWorker.register('/sw.js');
    }

    const drain = () => {
      const token = tokenRef.current;
      void flushOutbox(token ? { headers: { Authorization: `Bearer ${token}` } } : {});
    };
    window.addEventListener('online', drain);
    drain();
    return () => window.removeEventListener('online', drain);
  }, [accessToken]);

  return null;
}
