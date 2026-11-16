import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { DualConfirm } from './DualConfirm';

describe('DualConfirm (02-DESIGN-SYSTEM signature)', () => {
  it('hard-renders the two identifiers', () => {
    render(<DualConfirm name="Rohit Verma" mrn="MQ-2026-0042" abha="12-3456-7890-1234" onConfirm={() => undefined} />);
    expect(screen.getByText('Rohit Verma')).toBeInTheDocument();
    expect(screen.getByText('MRN MQ-2026-0042')).toBeInTheDocument();
    expect(screen.getByText('ABHA 12-3456-7890-1234')).toBeInTheDocument();
  });

  it('keeps confirm disabled until the verification check is affirmed', async () => {
    const onConfirm = vi.fn();
    render(<DualConfirm name="Rohit Verma" mrn="MQ-2026-0042" onConfirm={onConfirm} />);
    const confirm = screen.getByRole('button', { name: 'Confirm' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    await screen.getByRole('checkbox').click();
    expect(confirm.disabled).toBe(false);
    await confirm.click();
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});
