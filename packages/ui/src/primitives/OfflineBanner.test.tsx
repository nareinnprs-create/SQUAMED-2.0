import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { OfflineBanner } from './OfflineBanner';

describe('OfflineBanner', () => {
  it('honestly states the offline condition and announces politely', () => {
    render(<OfflineBanner />);
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('You are offline')).toBeInTheDocument();
  });

  it('offers the retry action', async () => {
    const onRetry = vi.fn();
    render(<OfflineBanner onRetry={onRetry} />);
    await screen.getByRole('button', { name: 'Retry' }).click();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
