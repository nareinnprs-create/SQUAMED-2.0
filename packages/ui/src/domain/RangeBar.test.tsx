import { render } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { RangeBar } from './RangeBar';

describe('RangeBar', () => {
  it('positions the marker and normal band on the scale', () => {
    const { container } = render(
      <RangeBar min={120} max={180} low={135} high={145} value={150} ariaLabel="MAP 150" />,
    );
    const bar = container.firstElementChild as HTMLElement;
    const layers = Array.from(bar.children) as HTMLElement[];
    const band = layers[0];
    const marker = layers[1];
    // value 150 of 120..180 → 50%
    expect(marker.style.insetInlineStart).toBe('calc(50% - 6px)');
    // band 135..145 → left 25%, width 16.666%
    expect(band.style.insetInlineStart).toBe('25%');
  });

  it('turns the marker critical when the value is out of band', () => {
    const { container } = render(<RangeBar min={0} max={100} low={20} high={80} value={95} />);
    const marker = (container.firstElementChild as HTMLElement).children[1] as HTMLElement;
    expect(marker.style.backgroundColor).toBe('var(--sq-critical)');
  });
});
