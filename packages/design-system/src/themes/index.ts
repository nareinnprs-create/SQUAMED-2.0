import { colors } from '../tokens/colors';

export type SquamedThemeMode = 'clinical' | 'enterprise' | 'command';

export interface SquamedTheme {
  mode: SquamedThemeMode;
  background: string;
  surface: string;
  secondary: string;
  foreground: string;
  mutedForeground: string;
  border: string;
  primary: string;
  success: string;
  warning: string;
  critical: string;
  information: string;
  isDark: boolean;
}

export const clinicalTheme: SquamedTheme = {
  mode: 'clinical',
  background: colors.canvas,
  surface: colors.surface,
  secondary: colors.secondary,
  foreground: colors.primaryText,
  mutedForeground: colors.secondaryText,
  border: colors.border,
  primary: colors.identity,
  success: colors.success,
  warning: colors.warning,
  critical: colors.critical,
  information: colors.information,
  isDark: false,
};

export const enterpriseTheme: SquamedTheme = {
  mode: 'enterprise',
  background: colors.canvas,
  surface: colors.surface,
  secondary: colors.secondary,
  foreground: colors.primaryText,
  mutedForeground: colors.secondaryText,
  border: colors.border,
  primary: colors.identity,
  success: colors.success,
  warning: colors.warning,
  critical: colors.critical,
  information: colors.information,
  isDark: false,
};

export const commandTheme: SquamedTheme = {
  mode: 'command',
  background: '#101414',
  surface: '#171C1D',
  secondary: '#22292A',
  foreground: '#E8ECEB',
  mutedForeground: '#9AA5A4',
  border: '#2E3839',
  primary: '#2BB0A8',
  success: '#66BB6A',
  warning: '#FFA726',
  critical: '#EF5350',
  information: '#42A5F5',
  isDark: true,
};

export const themes: Record<SquamedThemeMode, SquamedTheme> = {
  clinical: clinicalTheme,
  enterprise: enterpriseTheme,
  command: commandTheme,
};
