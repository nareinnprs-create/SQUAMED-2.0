import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { ErrorState } from './ErrorState';

describe('ErrorState', () => {
  it('announces itself and states what happened + what to do', () => {
    render(<ErrorState title="Could not save the note" description="The server rejected the draft." />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Could not save the note')).toBeInTheDocument();
    expect(screen.getByText('The server rejected the draft.')).toBeInTheDocument();
  });

  it('offers the i18n retry action', async () => {
    const onRetry = vi.fn();
    render(<ErrorState title="Failed" onRetry={onRetry} />);
    await screen.getByRole('button', { name: 'Try again' }).click();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
