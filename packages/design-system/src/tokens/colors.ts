export const colors = {
  canvas: '#F7F8F6',
  surface: '#FFFFFF',
  secondary: '#EEF1EF',
  primaryText: '#15191A',
  secondaryText: '#596267',
  border: '#DDE2DF',
  identity: '#087F7A',
  success: '#2E7D32',
  warning: '#F57C00',
  critical: '#C62828',
  information: '#1565C0',
} as const;

export type SquamedColor = keyof typeof colors;
