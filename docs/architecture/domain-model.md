# Domain Model Overview

## Domain Boundaries

| Module | Responsibility |
|--------|---------------|
| squamed-identity | Users, roles, permissions, sessions, MFA |
| squamed-patient | Patient master, EMPI, demographics, insurance |
| squamed-hospital-config | Hospital hierarchy, departments, beds, services |
| squamed-master-data | Drugs, procedures, diagnoses, lab tests, services |
| squamed-clinical | Encounters, notes, vitals, examinations, referrals |
| squamed-appointment | Schedules, appointments, slots |
| squamed-reception | Check-in, visitor management |
| squamed-queue | Queue definitions, entries, display, voice |
| squamed-orders | Lab, imaging, procedure orders |
| squamed-laboratory | Specimens, test results, validation |
| squamed-pharmacy | Prescriptions, dispensing, formulary |
| squamed-billing | Charges, invoices, payments |
| squamed-tasks | Task engine, assignments, escalation |
| squamed-communication | Messaging, channels |
| squamed-notifications | Notification templates, delivery |
| squamed-workflow | Workflow definitions, instances, steps |
| squamed-audit | Audit logging |
| squamed-ai | AI gateway, agents, scribe |
| squamed-analytics | Analytics pipeline, reporting |
| squamed-interoperability | FHIR, HL7v2, DICOM |

## Key Principles

- Patient identity belongs to SQUAMED (Section 2.3)
- Every action is governed (Section 2.4)
- Hospital activity is event-driven (Section 2.5)
- Configure, don't fork (Section 2.2)
