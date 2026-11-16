import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { VitalChip } from './VitalChip';

describe('VitalChip', () => {
  it('renders value + unit in tabular mono', () => {
    render(<VitalChip label="HR" value="88" unit="bpm" />);
    const value = screen.getByText('88');
    expect(value.style.fontFamily).toBe('var(--sq-font-mono)');
    expect(value.style.fontVariantNumeric).toBe('tabular-nums');
  });

  it('announces trend and out-of-range in the accessible name', () => {
    render(<VitalChip label="SpO2" value="89" unit="%" trend="down" outOfRange />);
    const chip = screen.getByLabelText('SpO2 89 %, trending down, out of range');
    expect(chip).toBeInTheDocument();
  });
});
