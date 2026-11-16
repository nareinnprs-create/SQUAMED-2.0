import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { EmptyState } from './EmptyState';
import { Button } from './Button';

describe('EmptyState', () => {
  it('renders title, description and the invited next action', () => {
    render(
      <EmptyState
        title="No orders yet"
        description="Orders placed for this encounter will appear here."
        action={<Button size="sm">Place order</Button>}
      />,
    );
    expect(screen.getByText('No orders yet')).toBeInTheDocument();
    expect(screen.getByText('Orders placed for this encounter will appear here.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Place order' })).toBeInTheDocument();
  });

  it('renders without description or action', () => {
    render(<EmptyState title="Nothing here" />);
    expect(screen.getByText('Nothing here')).toBeInTheDocument();
  });
});
