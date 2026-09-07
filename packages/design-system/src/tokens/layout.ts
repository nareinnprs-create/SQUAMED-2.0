export const spacing = {
  '0': '0px',
  '1': '2px',
  '2': '4px',
  '3': '6px',
  '4': '8px',
  '5': '12px',
  '6': '16px',
  '7': '20px',
  '8': '24px',
  '9': '32px',
  '10': '40px',
  '11': '48px',
  '12': '64px',
  '13': '80px',
  '14': '96px',
  '15': '128px',
} as const;

export const radius = {
  none: '0px',
  sm: '2px',
  md: '4px',
  lg: '6px',
  xl: '8px',
} as const;

export const shadows = {
  sm: '0 1px 2px rgba(21, 25, 26, 0.05)',
  md: '0 2px 4px rgba(21, 25, 26, 0.08)',
  lg: '0 4px 8px rgba(21, 25, 26, 0.12)',
  xl: '0 8px 16px rgba(21, 25, 26, 0.16)',
} as const;

export const breakpoints = {
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  '2xl': '1536px',
} as const;

export const zIndex = {
  base: 0,
  dropdown: 1000,
  sticky: 1100,
  modal: 1300,
  popover: 1400,
  toast: 1500,
  commandBar: 1600,
} as const;

export const transitions = {
  fast: '100ms',
  normal: '200ms',
  slow: '300ms',
  easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
} as const;
