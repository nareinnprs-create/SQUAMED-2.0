'use client';

import type { ReactNode } from 'react';
import { useAuth } from './AuthProvider';

export function ProtectedRoute({ children, fallback }: { children: ReactNode; fallback?: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) {
    return <div style={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center' }}>Loading...</div>;
  }
  if (!isAuthenticated) {
    return <>{fallback ?? null}</>;
  }
  return <>{children}</>;
}

export function AuthenticatedFallback(): ReactNode {
  return null;
}