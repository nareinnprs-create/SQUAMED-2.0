'use client';

import { forwardRef, useContext, useId } from 'react';
import type { ChangeEventHandler, InputHTMLAttributes, ReactNode } from 'react';
import { useFocusVisible } from './internal';
import { RadioGroupContext } from './RadioGroup';

export interface RadioProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'onChange' | 'checked'> {
  label?: ReactNode;
  value: string;
  checked?: boolean;
  onChange?: ChangeEventHandler<HTMLInputElement>;
}

export const Radio = forwardRef<HTMLInputElement, RadioProps>(function Radio(
  { label, value, checked = false, onChange, disabled = false, name, className, style, ...rest },
  ref,
) {
  const group = useContext(RadioGroupContext);
  const inputId = useId();
  const { focusVisible, onPointerDown, onFocus, onBlur } = useFocusVisible();
  const isChecked = group ? group.value === value : checked;
  const isDisabled = group ? (group.disabled ?? disabled) : disabled;
  const inputName = group?.name ?? name;

  const handleChange: ChangeEventHandler<HTMLInputElement> = (event) => {
    if (group) group.onValueChange(value);
    onChange?.(event);
  };

  return (
    <label
      className={className}
      onPointerDown={onPointerDown}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        position: 'relative',
        cursor: isDisabled ? 'not-allowed' : 'pointer',
        color: 'var(--sq-fg)',
        fontSize: 14,
        ...style,
      }}
    >
      <input
        {...rest}
        id={inputId}
        ref={ref}
        type="radio"
        name={inputName}
        value={value}
        checked={isChecked}
        disabled={isDisabled}
        onChange={handleChange}
        onFocus={onFocus}
        onBlur={onBlur}
        style={{
          position: 'absolute',
          width: 1,
          height: 1,
          margin: -1,
          padding: 0,
          overflow: 'hidden',
          clip: 'rect(0 0 0 0)',
          clipPath: 'inset(50%)',
          whiteSpace: 'nowrap',
          border: 0,
        }}
      />
      <span
        aria-hidden="true"
        style={{
          width: 18,
          height: 18,
          flexShrink: 0,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: '50%',
          border: '1.5px solid',
          borderColor: isChecked ? 'var(--sq-primary)' : 'var(--sq-border)',
          backgroundColor: 'var(--sq-surface)',
          transition: 'border-color 120ms ease',
          ...(isDisabled ? { opacity: 0.6 } : null),
          ...(focusVisible ? { outline: '2px solid var(--sq-focus-ring)', outlineOffset: 2 } : null),
        }}
      >
        <span
          style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            backgroundColor: isChecked ? 'var(--sq-primary)' : 'transparent',
            transition: 'background-color 120ms ease',
          }}
        />
      </span>
      {label ? <span>{label}</span> : null}
    </label>
  );
});