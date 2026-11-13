'use client';

import { createContext, useMemo } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { useControllableState } from './internal';

export interface RadioGroupContextValue {
  value: string | undefined;
  onValueChange: (value: string) => void;
  name?: string;
  disabled?: boolean;
}

export const RadioGroupContext = createContext<RadioGroupContextValue | undefined>(undefined);

export interface RadioGroupProps {
  children: ReactNode;
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  name?: string;
  disabled?: boolean;
  orientation?: 'horizontal' | 'vertical';
  className?: string;
  style?: CSSProperties;
  'aria-label'?: string;
}

export function RadioGroup({
  children,
  value,
  defaultValue = '',
  onValueChange,
  name,
  disabled,
  orientation = 'vertical',
  className,
  style,
  'aria-label': ariaLabel,
}: RadioGroupProps) {
  const [currentValue, setCurrentValue] = useControllableState(value, defaultValue, onValueChange);
  const contextValue = useMemo<RadioGroupContextValue>(
    () => ({ value: currentValue, onValueChange: setCurrentValue, name, disabled }),
    [currentValue, setCurrentValue, name, disabled],
  );

  return (
    <RadioGroupContext.Provider value={contextValue}>
      <div
        role="radiogroup"
        aria-orientation={orientation}
        aria-label={ariaLabel}
        className={className}
        style={{
          display: 'inline-flex',
          flexDirection: orientation === 'horizontal' ? 'row' : 'column',
          gap: 10,
          ...style,
        }}
      >
        {children}
      </div>
    </RadioGroupContext.Provider>
  );
}