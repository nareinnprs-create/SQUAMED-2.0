import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { Input } from './Input';
import { Field } from './Field';

describe('Field', () => {
  it('binds a real label to the control and renders the hint', () => {
    render(
      <Field label="Allergy note" hint="Visible to clinicians only">
        <Input />
      </Field>,
    );
    const input = screen.getByRole('textbox');
    const label = screen.getByText('Allergy note');
    expect(label).toHaveAttribute('for', input.id);
    expect(screen.getByText('Visible to clinicians only')).toHaveAttribute('id');
    expect(input).toHaveAttribute('aria-describedby');
    expect(input.getAttribute('aria-describedby')).toContain('-hint');
    expect(input).not.toBeInvalid();
  });

  it('wires the inline error through aria-describedby and aria-invalid', () => {
    render(
      <Field label="Dose" error="Dose exceeds the adult maximum">
        <Input />
      </Field>,
    );
    const input = screen.getByRole('textbox');
    expect(screen.getByRole('alert')).toHaveTextContent('Dose exceeds the adult maximum');
    expect(input).toBeInvalid();
    expect(input.getAttribute('aria-describedby')).toContain('-error');
  });

  it('marks required controls for assistive tech', () => {
    render(
      <Field label="MRN" required>
        <Input />
      </Field>,
    );
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-required', 'true');
    expect(screen.getByText('(Required)')).toBeInTheDocument();
  });

  it('preserves an explicit id given by the caller', () => {
    render(
      <Field label="Ward">
        <Input id="ward-code" />
      </Field>,
    );
    expect(screen.getByRole('textbox').id).toBe('ward-code');
  });
});
