'use client';

import { useCallback, useEffect, useState } from 'react';
import QRCode from 'qrcode';
import {
  Button,
  ContentPanel,
  Input,
  PageHeader,
  Select,
  Stack,
  StatusIndicator,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@squamed/ui';

export type KioskToken = {
  tokenNumber: string;
  queueType: string;
  priority: string;
  status: string;
  estimatedWaitMinutes?: number | null;
  qrPayload?: string | null;
};

export type KioskSlip = {
  patientId: string;
  patientMrn?: string | null;
  patientName?: string | null;
  appointmentId?: string | null;
  appointmentStart?: string | null;
  departmentName?: string | null;
  token: KioskToken;
  printedAt: string;
};

export type KioskDepartment = {
  id: string;
  name?: string | null;
  description?: string | null;
};

type IdentifierType = 'abha' | 'abha_address' | 'mobile';

const IDENTIFIER_LABELS: Record<IdentifierType, string> = {
  abha: 'ABHA number',
  abha_address: 'ABHA address',
  mobile: 'Mobile number',
};

/** Kiosk errors must be readable by patients; unknown codes fall through to the raw code. */
const ERROR_TEXT: Record<string, string> = {
  KIOSK_PATIENT_NOT_FOUND: 'No patient found for that value. Please ask at the reception desk.',
  KIOSK_IDENTIFIER_AMBIGUOUS: 'More than one patient shares this value. Please ask at the reception desk.',
  KIOSK_NO_APPOINTMENT: 'No appointment found for today. Please ask at the reception desk.',
  KIOSK_QR_INVALID: 'This QR code is not valid at this kiosk.',
  KIOSK_IDENTIFIER_TYPE_INVALID: 'Please choose a valid identification method.',
  ABHA_NUMBER_INVALID: 'ABHA numbers have 14 digits. Please check and try again.',
  ABHA_ADDRESS_INVALID: 'ABHA addresses look like name@abdm. Please check and try again.',
  HOSPITAL_CONTEXT_REQUIRED: 'This kiosk is not linked to a hospital. Please ask staff for help.',
};

/** 58mm thermal slip: on print, only the slip is visible at paper width. */
const PRINT_CSS = `
@media print {
  body * { visibility: hidden; }
  #kiosk-slip, #kiosk-slip * { visibility: visible; }
  #kiosk-slip {
    position: absolute;
    inset-inline-start: 0;
    top: 0;
    width: 58mm;
    padding: 4mm;
    color: #000;
    background: #fff;
  }
  #kiosk-slip img { width: 40mm; height: 40mm; }
}
`;

export function errorText(error: unknown): string {
  const code = error instanceof Error ? error.message : String(error);
  return ERROR_TEXT[code] ?? `Check-in failed: ${code}`;
}

export function checkInBody(identifierType: IdentifierType, identifier: string): string {
  return JSON.stringify({ identifierType, identifier: identifier.trim() });
}

export function KioskWorkspace({
  apiFetch,
  hospitalId,
}: {
  apiFetch: <T>(path: string, options?: RequestInit) => Promise<T>;
  hospitalId: string | null;
}) {
  const [tab, setTab] = useState<string>('check-in');
  const [identifierType, setIdentifierType] = useState<IdentifierType>('abha');
  const [identifier, setIdentifier] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [slip, setSlip] = useState<KioskSlip | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [departments, setDepartments] = useState<KioskDepartment[] | null>(null);

  const submit = useCallback(async () => {
    if (!identifier.trim() || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const data = await apiFetch<KioskSlip>('/kiosk/check-in', {
        method: 'POST',
        body: checkInBody(identifierType, identifier),
      });
      setSlip(data);
      setIdentifier('');
    } catch (caught) {
      setError(errorText(caught));
    } finally {
      setSubmitting(false);
    }
  }, [apiFetch, identifier, identifierType, submitting]);

  useEffect(() => {
    if (!slip) {
      setQrDataUrl(null);
      return;
    }
    let stale = false;
    QRCode.toDataURL(slip.token.qrPayload ?? slip.token.tokenNumber, { width: 240 })
      .then((url) => {
        if (!stale) setQrDataUrl(url);
      })
      .catch(() => {
        if (!stale) setQrDataUrl(null);
      });
    return () => {
      stale = true;
    };
  }, [slip]);

  useEffect(() => {
    if (tab !== 'departments' || !hospitalId || departments) return;
    let stale = false;
    apiFetch<KioskDepartment[]>(`/hospital-config/hospitals/${hospitalId}/departments`)
      .then((data) => {
        if (!stale) setDepartments(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (!stale) setDepartments([]);
      });
    return () => {
      stale = true;
    };
  }, [apiFetch, departments, hospitalId, tab]);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Self-service kiosk"
        description="Check in with ABHA, ABHA address or mobile number and print your queue token."
      />
      <style>{PRINT_CSS}</style>
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList style={{ marginBottom: 16 }}>
          <TabsTrigger value="check-in">Check-in</TabsTrigger>
          <TabsTrigger value="departments">Departments</TabsTrigger>
        </TabsList>
        <TabsContent value="check-in">
          <Stack gap={16}>
            {!slip ? (
              <ContentPanel style={{ maxWidth: 420 }}>
                <Stack gap={12}>
                  <Select
                    label="Identification method"
                    value={identifierType}
                    onChange={(e) => setIdentifierType(e.target.value as IdentifierType)}
                  >
                    <option value="abha">ABHA number</option>
                    <option value="abha_address">ABHA address</option>
                    <option value="mobile">Mobile number</option>
                  </Select>
                  <Input
                    label={IDENTIFIER_LABELS[identifierType]}
                    value={identifier}
                    onChange={(e) => setIdentifier(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') void submit();
                    }}
                    required
                  />
                  {error ? <StatusIndicator tone="critical" label={error} /> : null}
                  <Button onClick={() => void submit()} disabled={submitting || !identifier.trim()}>
                    {submitting ? 'Checking in...' : 'Get token'}
                  </Button>
                </Stack>
              </ContentPanel>
            ) : (
              <ContentPanel>
                <div id="kiosk-slip">
                  <div style={{ fontSize: 16, fontWeight: 700 }}>Your queue token</div>
                  <div style={{ fontSize: 64, fontWeight: 800, lineHeight: 1.1, margin: '12px 0' }}>
                    {slip.token.tokenNumber}
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <strong>{slip.patientName ?? ''}</strong>
                    {slip.patientMrn ? <span style={{ color: 'var(--sq-fg-muted)' }}> · {slip.patientMrn}</span> : null}
                  </div>
                  {slip.departmentName ? (
                    <div style={{ marginBottom: 4 }}>Go to: {slip.departmentName}</div>
                  ) : null}
                  {typeof slip.token.estimatedWaitMinutes === 'number' ? (
                    <div style={{ marginBottom: 4 }}>Approximate wait: {slip.token.estimatedWaitMinutes} min</div>
                  ) : null}
                  {qrDataUrl ? (
                    // Data-URL QR is regenerated per slip; next/image optimization does not apply.
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={qrDataUrl} alt={`QR code for token ${slip.token.tokenNumber}`} width={180} height={180} />
                  ) : null}
                  <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
                    <Button onClick={() => window.print()}>Print slip</Button>
                    <Button variant="outline" onClick={() => setSlip(null)}>
                      Done
                    </Button>
                  </div>
                </div>
              </ContentPanel>
            )}
          </Stack>
        </TabsContent>
        <TabsContent value="departments">
          <ContentPanel>
            <div style={{ fontWeight: 700, marginBottom: 8 }}>Department guide</div>
            {!hospitalId ? (
              <StatusIndicator tone="information" label="Hospital not linked yet - the department guide appears once the kiosk is registered." />
            ) : departments === null ? (
              <StatusIndicator tone="information" label="Loading departments..." />
            ) : departments.length === 0 ? (
              <StatusIndicator tone="information" label="No departments published yet." />
            ) : (
              <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                {departments.map((department) => (
                  <li
                    key={department.id}
                    style={{ padding: '10px 0', borderBottom: '1px solid var(--sq-border, #1e293b)' }}
                  >
                    <div style={{ fontWeight: 600 }}>{department.name ?? department.id}</div>
                    {department.description ? (
                      <div style={{ fontSize: 13, color: 'var(--sq-fg-muted)' }}>{department.description}</div>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </ContentPanel>
        </TabsContent>
      </Tabs>
    </div>
  );
}
