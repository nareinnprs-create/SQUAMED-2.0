import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { StatusDot } from './StatusDot';
import { TriangleIcon } from '../domain/icons';

describe('StatusDot', () => {
  it('pairs the dot with the required status word', () => {
    render(<StatusDot tone="critical" label="Deteriorating" />);
    expect(screen.getByText('Deteriorating')).toBeInTheDocument();
  });

  it('renders the glyph in the tone color when given', () => {
    render(
      <StatusDot
        tone="critical"
        label="Deteriorating"
        glyph={<TriangleIcon size={12} />}
      />,
    );
    expect(screen.getByText('Deteriorating')).toBeInTheDocument();
  });
});
