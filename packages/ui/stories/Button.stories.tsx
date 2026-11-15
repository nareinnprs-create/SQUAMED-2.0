import type { Meta, StoryObj } from '@storybook/react';
import { Button } from '../src/primitives/Button';

const meta: Meta<typeof Button> = {
  title: 'Primitives/Button',
  component: Button,
  parameters: { layout: 'centered' },
};

export default meta;
type Story = StoryObj<typeof Button>;

export const Primary: Story = {
  args: { variant: 'primary', children: 'Admit patient' },
};

export const Secondary: Story = {
  args: { variant: 'secondary', children: 'Save draft' },
};

export const Outline: Story = {
  args: { variant: 'outline', children: 'Print chart' },
};

export const Ghost: Story = {
  args: { variant: 'ghost', children: 'Dismiss' },
};

export const Danger: Story = {
  args: { variant: 'danger', children: 'Cancel order' },
};

export const Success: Story = {
  args: { variant: 'success', children: 'Verify vitals' },
};

export const Loading: Story = {
  args: { variant: 'primary', loading: true, children: 'Submitting' },
};

export const Small: Story = {
  args: { variant: 'primary', size: 'sm', children: 'Compact action' },
};

export const Large: Story = {
  args: { variant: 'primary', size: 'lg', children: 'Primary action' },
};
