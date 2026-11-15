import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { TaskCard, type Task } from './TaskCard';

const base: Task = {
  id: 't-1',
  title: 'Administer morning insulin',
  status: 'pending',
  priority: 'urgent',
  dueAt: new Date(2026, 10, 15, 9, 30),
  assigneeName: 'R. Sharma',
};

describe('TaskCard', () => {
  it('renders title, status, priority, assignee and mono due time', () => {
    render(<TaskCard task={base} />);
    expect(screen.getByText('Administer morning insulin')).toBeInTheDocument();
    expect(screen.getByText('Pending')).toBeInTheDocument();
    expect(screen.getByText('R. Sharma')).toBeInTheDocument();
    expect(screen.getByText('15 Nov, 9:30')).toBeInTheDocument();
    expect(screen.getByText('Administer morning insulin').style.fontVariantNumeric).not.toBe('tabular-nums');
    expect(screen.getByText('15 Nov, 9:30').style.fontVariantNumeric).toBe('tabular-nums');
  });

  it('maps statuses to tones and labels', () => {
    const { unmount } = render(<TaskCard task={{ ...base, status: 'completed' }} />);
    expect(screen.getByText('Completed')).toBeInTheDocument();
    unmount();
    render(<TaskCard task={{ ...base, status: 'in_progress' }} />);
    expect(screen.getByText('In progress')).toBeInTheDocument();
  });

  it('calls onOpen with the task', async () => {
    const opened: Task[] = [];
    render(<TaskCard task={base} onOpen={(task) => opened.push(task)} />);
    await screen.getByRole('button', { name: 'Open task' }).click();
    expect(opened).toHaveLength(1);
    expect(opened[0].id).toBe('t-1');
  });

  it('surfaces the escalation count for escalated tasks', () => {
    render(<TaskCard task={{ ...base, status: 'escalated', escalationCount: 3 }} />);
    expect(screen.getByText('Escalated')).toBeInTheDocument();
    expect(screen.getByText('Escalated x3')).toBeInTheDocument();
  });
});
