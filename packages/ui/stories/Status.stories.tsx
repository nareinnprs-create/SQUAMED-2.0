import type { Meta, StoryObj } from '@storybook/react';
import { StatusIndicator } from '../src/primitives/StatusIndicator';
import { Badge } from '../src/primitives/Badge';
import { PriorityIndicator } from '../src/primitives/PriorityIndicator';
import { Alert } from '../src/primitives/Alert';

const meta: Meta = {
  title: 'Primitives/Status',
  parameters: { layout: 'padded' },
};

export default meta;

export const IndicatorTones: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <StatusIndicator tone="success" label="Stable" />
      <StatusIndicator tone="warning" label="Watch" />
      <StatusIndicator tone="critical" label="Deteriorating" />
      <StatusIndicator tone="information" label="In transit" />
      <StatusIndicator tone="neutral" label="Unknown" />
    </div>
  ),
};

export const PulsingIndicator: StoryObj = {
  render: () => <StatusIndicator tone="critical" pulse label="Code blue" />,
};

export const BadgeVariants: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      <Badge variant="neutral">Neutral</Badge>
      <Badge variant="success">Paid</Badge>
      <Badge variant="warning">Pending</Badge>
      <Badge variant="critical">Overdue</Badge>
      <Badge variant="information">Scheduled</Badge>
      <Badge variant="primary">SQUAMED</Badge>
    </div>
  ),
};

export const PriorityLevels: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, width: 220 }}>
      <PriorityIndicator level="low" />
      <PriorityIndicator level="routine" />
      <PriorityIndicator level="urgent" />
      <PriorityIndicator level="high" />
      <PriorityIndicator level="emergency" />
      <PriorityIndicator level="critical" />
    </div>
  ),
};

export const AlertVariants: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, width: 420 }}>
      <Alert variant="info" title="Rounds start at 08:00">
        Ward round schedule published for today.
      </Alert>
      <Alert variant="warning" title="Allergy conflict">
        Penicillin allergy flag on this patient.
      </Alert>
      <Alert variant="critical" title="Critical result">
        Potassium 6.8 mmol/L — call the on-call physician.
      </Alert>
      <Alert variant="success" title="Discharge ready">
        All pending signatures captured.
      </Alert>
    </div>
  ),
};
