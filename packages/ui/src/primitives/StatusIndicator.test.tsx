import { render } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { StatusIndicator } from './StatusIndicator';

describe('StatusIndicator motion (02-DESIGN-SYSTEM §5)', () => {
  it('pulses exactly once for 300ms — the single orchestrated moment', () => {
    const { container } = render(<StatusIndicator tone="critical" pulse label="Code blue" />);
    const dot = container.querySelector('.sq-status-dot') as HTMLElement;
    expect(dot.style.animation).toBe('sq-status-pulse 300ms ease-out 1');
  });

  it('does not animate without the pulse prop', () => {
    const { container } = render(<StatusIndicator tone="success" label="Stable" />);
    const dot = container.querySelector('.sq-status-dot') as HTMLElement;
    expect(dot.style.animation).toBe('');
  });
});
