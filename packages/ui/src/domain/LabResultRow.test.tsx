import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { LabResultRow } from './LabResultRow';

describe('LabResultRow', () => {
  it('renders the test, mono value and normal status', () => {
    render(
      <LabResultRow test="Sodium" value={142} unit="mmol/L" scale={{ min: 110, max: 160 }} band={{ low: 135, high: 145 }} />,
    );
    expect(screen.getByText('Sodium')).toBeInTheDocument();
    expect(screen.getByText('142')).toBeInTheDocument();
    expect(screen.getByText('Normal')).toBeInTheDocument();
  });

  it('flags abnormal ends by glyph + status word, not color alone', () => {
    render(
      <LabResultRow
        test="Potassium"
        value={5.9}
        unit="mmol/L"
        scale={{ min: 2, max: 7 }}
        band={{ low: 3.5, high: 5.1 }}
        status="high"
      />,
    );
    expect(screen.getByText('High')).toBeInTheDocument();
    expect(document.querySelectorAll('svg')).not.toHaveLength(0);
  });
});
