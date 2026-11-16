import type { Meta, StoryObj } from '@storybook/react';
import { StatusDot } from '../src/primitives/StatusDot';
import { StatusChip } from '../src/primitives/StatusChip';
import { TriangleIcon, TrendDownIcon } from '../src/domain/icons';

const meta: Meta = {
  title: 'Primitives/Status Parts',
  parameters: { layout: 'padded' },
};

export default meta;

export const DotTones: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <StatusDot tone="success" label="Validated" />
      <StatusDot tone="warning" label="Pending" />
      <StatusDot tone="critical" label="Deteriorating" glyph={<TriangleIcon size={13} />} />
      <StatusDot tone="information" label="In transit" />
      <StatusDot tone="neutral" label="Unknown" />
    </div>
  ),
};

export const ChipTones: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      <StatusChip tone="success" label="Validated" />
      <StatusChip tone="warning" label="SLA at risk" />
      <StatusChip tone="critical" label="Blocked" glyph={<TriangleIcon size={13} />} />
      <StatusChip tone="information" label="Scheduled" />
      <StatusChip tone="neutral" label="Draft" />
      <StatusChip tone="warning" label="Falling" glyph={<TrendDownIcon size={13} />} />
    </div>
  ),
};
