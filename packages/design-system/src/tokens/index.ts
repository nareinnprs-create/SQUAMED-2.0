import { colors } from './colors';
import { typography } from './typography';
import { spacing, radius, shadows, breakpoints, zIndex, transitions } from './layout';

export const tokens = {
  colors,
  typography,
  spacing,
  radius,
  shadows,
  breakpoints,
  zIndex,
  transitions,
} as const;

export * from './colors';
export * from './typography';
export * from './layout';
