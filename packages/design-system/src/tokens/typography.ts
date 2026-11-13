export const typography = {
  fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  scale: {
    '10': { fontSize: '10px', lineHeight: '14px', fontWeight: 400 },
    '11': { fontSize: '11px', lineHeight: '15px', fontWeight: 400 },
    '12': { fontSize: '12px', lineHeight: '18px', fontWeight: 400 },
    '13': { fontSize: '13px', lineHeight: '20px', fontWeight: 400 },
    '14': { fontSize: '14px', lineHeight: '20px', fontWeight: 400 },
    '16': { fontSize: '16px', lineHeight: '24px', fontWeight: 400 },
    '18': { fontSize: '18px', lineHeight: '26px', fontWeight: 500 },
    '20': { fontSize: '20px', lineHeight: '28px', fontWeight: 500 },
    '24': { fontSize: '24px', lineHeight: '32px', fontWeight: 600 },
    '30': { fontSize: '30px', lineHeight: '38px', fontWeight: 600 },
    '36': { fontSize: '36px', lineHeight: '44px', fontWeight: 700 },
  },
  weights: {
    regular: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },
} as const;

export type TypographyScale = keyof typeof typography.scale;
