import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { MedOrderLine } from './MedOrderLine';

const base = {
  drug: 'Insulin glargine',
  dose: '10 units',
  route: 'SC',
  schedule: '22:00 daily',
};

describe('MedOrderLine', () => {
  it('renders drug, dose, route, schedule in fixed columns', () => {
    render(<MedOrderLine {...base} />);
    expect(screen.getByText('Insulin glargine')).toBeInTheDocument();
    expect(screen.getByText('10 units')).toBeInTheDocument();
    expect(screen.getByText('SC')).toBeInTheDocument();
    expect(screen.getByText('22:00 daily')).toBeInTheDocument();
  });

  it('high-alert drugs carry the second-verify affordance', async () => {
    const onVerify = vi.fn();
    render(<MedOrderLine {...base} highAlert onVerify={onVerify} />);
    expect(screen.getByText('High alert')).toBeInTheDocument();
    await screen.getByRole('button', { name: 'Verify' }).click();
    expect(onVerify).toHaveBeenCalledTimes(1);
  });

  it('shows the completed second verification with the verifier name', () => {
    render(<MedOrderLine {...base} highAlert verifiedBy="Dr. K. Rao" />);
    expect(screen.getByText('Verified by Dr. K. Rao')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verify' })).not.toBeInTheDocument();
  });
});
