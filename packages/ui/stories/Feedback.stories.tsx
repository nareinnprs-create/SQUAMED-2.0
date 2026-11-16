import type { Meta, StoryObj } from '@storybook/react';
import { EmptyState } from '../src/primitives/EmptyState';
import { ErrorState } from '../src/primitives/ErrorState';
import { OfflineBanner } from '../src/primitives/OfflineBanner';
import { Button } from '../src/primitives/Button';
import { FlaskIcon, NoteIcon } from '../src/domain/icons';

const meta: Meta = {
  title: 'Primitives/Feedback States',
  parameters: { layout: 'padded' },
};

export default meta;

export const EmptyStates: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 520 }}>
      <EmptyState
        title="No orders yet"
        description="Orders placed for this encounter will appear here."
        glyph={<NoteIcon size={20} />}
        action={<Button size="sm" variant="primary">Place order</Button>}
      />
      <EmptyState title="No results" glyph={<FlaskIcon size={20} />} />
    </div>
  ),
};

export const ErrorStates: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 520 }}>
      <ErrorState
        title="Could not save the note"
        description="The server rejected the draft. Check your connection and retry."
        onRetry={() => undefined}
      />
      <ErrorState title="Prescription failed validation" />
    </div>
  ),
};

export const OfflineBanners: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 640 }}>
      <OfflineBanner onRetry={() => undefined} />
      <OfflineBanner />
    </div>
  ),
};
