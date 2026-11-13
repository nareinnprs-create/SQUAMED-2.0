export { AuthProvider, useAuth } from './AuthProvider';
export { usePermission, usePermissions, useHasRole, useHasAnyRole } from './hooks';
export { createHttpAuthClient } from './http-client';
export { ProtectedRoute } from './ProtectedRoute';
export * from './types';
export const SQUAMED_AUTH_VERSION = '0.2.0';