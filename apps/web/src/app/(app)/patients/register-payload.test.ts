import { describe, expect, it } from 'vitest';
import { buildRegistrationPayload, registrationRequired, type RegistrationForm } from './register-payload';

const full: RegistrationForm = {
  firstName: 'Asha',
  lastName: 'Rao',
  dateOfBirth: '1990-05-05',
  gender: 'female',
  phone: '55123456',
  qid: '',
  email: 'asha@x.local',
  nationality: '',
  city: 'Doha',
};

describe('buildRegistrationPayload', () => {
  it('sends identity fields for known patients and drops empty optionals', () => {
    expect(buildRegistrationPayload(full, false, 'ABHA', '')).toEqual({
      firstName: 'Asha',
      lastName: 'Rao',
      dateOfBirth: '1990-05-05',
      gender: 'female',
      phone: '55123456',
      email: 'asha@x.local',
      city: 'Doha',
      identifiers: undefined,
    });
  });

  it('unknown patients send only the flag, never identity fields', () => {
    const payload = buildRegistrationPayload(full, true, 'ABHA', '');
    expect(payload['unknownPatient']).toBe(true);
    expect(payload['firstName']).toBeUndefined();
    expect(payload['dateOfBirth']).toBeUndefined();
  });

  it('attaches a trimmed ABHA identifier only when a value is set', () => {
    expect(buildRegistrationPayload(full, false, 'ABHA', ' 12345678901234 ')['identifiers']).toEqual([
      { idType: 'ABHA', idValue: '12345678901234' },
    ]);
    expect(buildRegistrationPayload(full, false, 'ABHA', '   ')['identifiers']).toBeUndefined();
  });
});

describe('registrationRequired', () => {
  it('requires name, DOB and gender for known patients', () => {
    expect(registrationRequired(full, false)).toBe(true);
    expect(registrationRequired({ ...full, gender: '' }, false)).toBe(false);
  });

  it('never blocks unknown patients', () => {
    expect(registrationRequired({ ...full, firstName: '', lastName: '', dateOfBirth: '', gender: '' }, true)).toBe(true);
  });
});
