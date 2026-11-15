import type { Meta, StoryObj } from '@storybook/react';
import { TaskCard, type Task } from '../src/domain/TaskCard';

const tasks: Task[] = [
  { id: 't-1', title: 'Administer morning insulin', status: 'pending', priority: 'urgent', dueAt: new Date(2026, 10, 15, 9, 30), assigneeName: 'R. Sharma' },
  { id: 't-2', title: 'Recheck vitals after transfusion', status: 'in_progress', priority: 'high', dueAt: new Date(2026, 10, 15, 11, 0), assigneeName: 'P. Iyer' },
  { id: 't-3', title: 'Call family about discharge plan', status: 'escalated', priority: 'emergency', dueAt: new Date(2026, 10, 15, 12, 15), assigneeName: 'S. Gupta', escalationCount: 3 },
  { id: 't-4', title: 'Sign discharge summary', status: 'completed', priority: 'routine', dueAt: new Date(2026, 10, 14, 17, 45), assigneeName: 'Dr. K. Rao' },
];

const meta: Meta<typeof TaskCard> = {
  title: 'Domain/TaskCard',
  component: TaskCard,
  parameters: { layout: 'padded' },
};

export default meta;
type Story = StoryObj<typeof TaskCard>;

export const Pending: Story = {
  args: { task: tasks[0], onOpen: () => {} },
};

export const InProgress: Story = {
  args: { task: tasks[1], onOpen: () => {} },
};

export const Escalated: Story = {
  args: { task: tasks[2], onOpen: () => {} },
};

export const Completed: Story = {
  args: { task: tasks[3], onOpen: () => {} },
};

export const TaskInbox: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, width: 460 }}>
      {tasks.map((task) => (
        <TaskCard key={task.id} task={task} onOpen={() => {}} />
      ))}
    </div>
  ),
};
