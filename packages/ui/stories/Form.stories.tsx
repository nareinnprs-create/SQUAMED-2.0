import type { Meta, StoryObj } from '@storybook/react';
import { Input } from '../src/primitives/Input';
import { Textarea } from '../src/primitives/Textarea';
import { Select } from '../src/primitives/Select';
import { Field } from '../src/primitives/Field';

const meta: Meta<typeof Field> = {
  title: 'Primitives/Form',
  component: Field,
  parameters: { layout: 'padded' },
};

export default meta;
type Story = StoryObj<typeof Field>;

export const TextualInput: Story = {
  render: () => <Input label="Patient name" hint="As printed on the ID document" />,
};

export const InputWithError: Story = {
  render: () => <Input label="Mobile number" error="Enter a 10-digit number" defaultValue="98765" />,
};

export const SelectField: Story = {
  render: () => (
    <Field label="Ward" hint="Bed assignments follow from the ward">
      <Select>
        <option value="general">General medicine</option>
        <option value="icu">Intensive care</option>
        <option value="maternity">Maternity</option>
      </Select>
    </Field>
  ),
};

export const SelectRequired: Story = {
  render: () => (
    <Field label="Blood group" required>
      <Select>
        <option value="">Choose…</option>
        <option value="a">A</option>
        <option value="b">B</option>
        <option value="o">O</option>
      </Select>
    </Field>
  ),
};

export const TextareaField: Story = {
  render: () => (
    <Field label="Clinical note" hint="Autosaves every 30 seconds">
      <Textarea rows={4} />
    </Field>
  ),
};

export const TextareaWithError: Story = {
  render: () => (
    <Field label="Discharge summary" error="Minimum 40 characters required">
      <Textarea rows={4} defaultValue="Pt stable." />
    </Field>
  ),
};
