'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { SquamedThemeMode } from '@squamed/design-system';

const STORAGE_KEY = 'squamed.theme-mode';
const THEME_MODE_PREFERRED = typeof window !== 'undefined' ? window.matchMedia('(prefers-contrast: more)') : null;

interface ThemeContextValue {
  mode: SquamedThemeMode;
  setMode: (mode: SquamedThemeMode) => void;
  toggleMode: () => void;
  isCommand: boolean;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

function getInitialMode(): SquamedThemeMode {
  if (typeof window === 'undefined') return 'clinical';
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === 'clinical' || stored === 'enterprise' || stored === 'command') {
    return stored;
  }
  if (THEME_MODE_PREFERRED?.matches) return 'command';
  return 'clinical';
}

interface ThemeProviderProps {
  children: ReactNode;
  initialMode?: SquamedThemeMode;
}

export function ThemeProvider({ children, initialMode }: ThemeProviderProps) {
  const [mode, setModeState] = useState<SquamedThemeMode>(initialMode ?? getInitialMode);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', mode);
    document.documentElement.style.colorScheme = mode === 'command' ? 'dark' : 'light';
  }, [mode]);

  const setMode = useCallback((next: SquamedThemeMode) => {
    setModeState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* storage unavailable */
    }
  }, []);

  const toggleMode = useCallback(() => {
    setModeState((prev) => {
      const next: SquamedThemeMode = prev === 'clinical' ? 'enterprise' : prev === 'enterprise' ? 'command' : 'clinical';
      try {
        window.localStorage.setItem(STORAGE_KEY, next);
      } catch {
        /* storage unavailable */
      }
      return next;
    });
  }, []);

  const value = useMemo(
    () => ({ mode, setMode, toggleMode, isCommand: mode === 'command' }),
    [mode, setMode, toggleMode],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return ctx;
}