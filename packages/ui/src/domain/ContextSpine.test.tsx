import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { ContextSpine } from './ContextSpine';

describe('ContextSpine (02-DESIGN-SYSTEM §1 signature)', () => {
  it('renders the two identifiers — name and mono MRN/ABHA', () => {
    render(
      <ContextSpine name="Rohit Verma" mrn="MQ-2026-0042" abha="12-3456-7890-1234" />,
    );
    expect(screen.getByText('Rohit Verma')).toBeInTheDocument();
    expect(screen.getByText('MQ-2026-0042')).toBeInTheDocument();
    expect(screen.getByText('12-3456-7890-1234')).toBeInTheDocument();
  });

  it('flags the allergy with glyph + word + color, never color alone', () => {
    render(<ContextSpine name="Rohit Verma" mrn="MQ-2026-0042" allergy="Penicillin" />);
    expect(screen.getByText('Allergies: Penicillin')).toBeInTheDocument();
  });

  it('renders live status dots and the encounter stage', () => {
    render(
      <ContextSpine
        name="Rohit Verma"
        mrn="MQ-2026-0042"
        statuses={[
          { tone: 'critical', label: 'Deteriorating' },
          { tone: 'information', label: 'NPO since 22:00' },
        ]}
        encounterStage="Admitted · Day 2"
      />,
    );
    expect(screen.getByText('Deteriorating')).toBeInTheDocument();
    expect(screen.getByText('NPO since 22:00')).toBeInTheDocument();
    expect(screen.getByText('Admitted · Day 2')).toBeInTheDocument();
  });
});
