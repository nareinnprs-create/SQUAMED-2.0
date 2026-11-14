export interface RegistrationForm {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  phone: string;
  qid: string;
  email: string;
  nationality: string;
  city: string;
}

/**
 * Builds the POST /patients body. Unknown patients send only the flag (server applies
 * sentinel identity + pending status); the optional ABHA identifier is attached when set.
 */
export function buildRegistrationPayload(
  form: RegistrationForm,
  unknownPatient: boolean,
  idType: string,
  idValue: string,
): Record<string, unknown> {
  const identifier = idValue.trim() ? [{ idType, idValue: idValue.trim() }] : undefined;
  return {
    ...(unknownPatient
      ? { unknownPatient: true }
      : {
          firstName: form.firstName,
          lastName: form.lastName,
          dateOfBirth: form.dateOfBirth,
          gender: form.gender,
        }),
    phone: form.phone || undefined,
    qid: form.qid || undefined,
    email: form.email || undefined,
    nationality: form.nationality || undefined,
    city: form.city || undefined,
    identifiers: identifier,
  };
}

/** Unknown patients skip the identity fields; known patients need name + DOB + gender. */
export function registrationRequired(
  form: RegistrationForm,
  unknownPatient: boolean,
): boolean {
  return unknownPatient || Boolean(form.firstName.trim() && form.lastName.trim() && form.dateOfBirth && form.gender);
}
