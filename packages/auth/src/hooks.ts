'use client';

import { useAuth } from './AuthProvider';
import type { AuthUser } from './types';

function matchesPermission(granted: string, required: string): boolean {
  if (granted === required) return true;
  const grantedParts = granted.split(':');
  const requiredParts = required.split(':');
  if (grantedParts.length < 2 || requiredParts.length < 2) return false;
  if (grantedParts[0] !== '*' && grantedParts[0] !== requiredParts[0]) return false;
  if (grantedParts[1] !== '*' && grantedParts[1] !== requiredParts[1]) return false;
  if (
    grantedParts.length > 2 &&
    requiredParts.length > 2 &&
    grantedParts[2] !== '*' &&
    grantedParts[2] !== requiredParts[2]
  ) {
    return false;
  }
  return true;
}

export function usePermission(required: string, user?: AuthUser | null): boolean {
  const { user: authUser } = useAuth();
  const resolvedUser = user !== undefined ? user : authUser;
  if (!resolvedUser) return false;
  return resolvedUser.permissions.some((permission) => matchesPermission(permission, required));
}

export function usePermissions(requiredAll?: string[], requiredAny?: string[]): boolean {
  const { user: authUser } = useAuth();
  if (requiredAll && requiredAll.length > 0) {
    return requiredAll.every((p) => authUser?.permissions.some((g) => matchesPermission(g, p)) ?? false);
  }
  if (requiredAny && requiredAny.length > 0) {
    return requiredAny.some((p) => authUser?.permissions.some((g) => matchesPermission(g, p)) ?? false);
  }
  return true;
}

export function useHasRole(role: string): boolean {
  const { user } = useAuth();
  return user?.roles.includes(role) ?? false;
}

export function useHasAnyRole(roles: string[]): boolean {
  const { user } = useAuth();
  if (!user) return false;
  return roles.some((role) => user.roles.includes(role));
}