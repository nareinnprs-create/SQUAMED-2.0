import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { StatusChip } from './StatusChip';

describe('StatusChip', () => {
  it('renders the status word on the soft tint pill', () => {
    render(<StatusChip tone="success" label="Validated" />);
    expect(screen.getByText('Validated')).toBeInTheDocument();
  });

  it('uses on-soft text color for the tone, never ink on tint', () => {
    const { container } = render(<StatusChip tone="critical" label="Blocked" />);
    const pill = container.firstElementChild as HTMLElement;
    expect(pill.style.backgroundColor).toBe('var(--sq-critical-soft)');
    expect(screen.getByText('Blocked').style.color).toBe('var(--sq-critical-on-soft)');
  });
});
