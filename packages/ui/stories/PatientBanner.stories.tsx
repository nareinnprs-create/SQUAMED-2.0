import type { Meta, StoryObj } from '@storybook/react';
import { PatientBanner } from '../src/domain/PatientBanner';

const meta: Meta<typeof PatientBanner> = {
  title: 'Domain/PatientBanner',
  component: PatientBanner,
  parameters: { layout: 'padded' },
};

export default meta;
type Story = StoryObj<typeof PatientBanner>;

export const StandardAdmission: Story = {
  args: {
    name: 'Rohit Verma',
    mrn: 'SM-100241',
    dateOfBirth: '1979-04-12',
    gender: 'male',
    age: 47,
    bloodGroup: 'B+',
    allergySummary: 'No known allergies',
    isEmergency: false,
  },
};

export const EmergencyAdmission: Story = {
  args: {
    name: 'Anita Desai',
    mrn: 'SM-100242',
    dateOfBirth: '1993-09-30',
    gender: 'female',
    age: 33,
    bloodGroup: 'O-',
    allergySummary: 'Penicillin',
    isEmergency: true,
  },
};
