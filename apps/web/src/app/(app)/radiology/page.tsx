'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  Input,
  PageHeader,
  Stack,
  StatusIndicator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@squamed/ui';
import type { BadgeVariant, StatusTone } from '@squamed/ui';
import { useApi } from '../../../lib/use-api';

// 02.05 RIS-lite workspace: worklist, structured report (ICD/SNOMED), critical findings,
// and OHIF embedding via a study-scoped token (PACS/rendering stay with Orthanc/OHIF).

interface ReportView {
  id: string;
  reportVersion: number;
  templateCode: string | null;
  findings: string;
  impression: string;
  icdCodes: string[];
  snomedCodes: string[];
  status: string;
  signedAt: string | null;
  amendsReportId: string | null;
}

interface StudyView {
  id: string;
  studyInstanceUid: string;
  orthancStudyId: string | null;
  seriesCount: number | null;
  completedAt: string | null;
}

interface RadOrder {
  id: string;
  accession: string;
  modality: string;
  bodyPart: string;
  priority: string;
  status: string;
  orderedAt: string | null;
  study: StudyView | null;
  reports: ReportView[];
}

interface CriticalFinding {
  id: string;
  severity: string;
  finding: string;
  slaMinutes: number;
  status: string;
  escalationLevel: number;
  escalationRole: string;
  raisedAt: string | null;
}

interface Notice {
  tone: StatusTone;
  label: string;
}

const statusVariants: Record<string, BadgeVariant> = {
  ordered: 'neutral',
  scheduled: 'information',
  in_progress: 'warning',
  completed: 'information',
  reported: 'warning',
  signed: 'success',
};

export default function RadiologyPage() {
  const { apiFetch, isReady } = useApi();

  const [orders, setOrders] = useState<RadOrder[]>([]);
  const [critical, setCritical] = useState<CriticalFinding[]>([]);
  const [selected, setSelected] = useState<RadOrder | null>(null);
  const [findings, setFindings] = useState('');
  const [impression, setImpression] = useState('');
  const [icd, setIcd] = useState('');
  const [snomed, setSnomed] = useState('');
  const [template, setTemplate] = useState('CT-CHEST');
  const [studyUid, setStudyUid] = useState('');
  const [viewer, setViewer] = useState<{ token: string; viewerUrl: string | null } | null>(null);
  const [working, setWorking] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const load = useCallback(async () => {
    const [worklist, findingsRows] = await Promise.all([
      apiFetch<RadOrder[]>('/radiology/worklist'),
      apiFetch<CriticalFinding[]>('/radiology/critical-findings?status=OPEN'),
    ]);
    setOrders(worklist);
    setCritical(findingsRows);
  }, [apiFetch]);

  useEffect(() => {
    if (!isReady) return;
    void load().catch((e: Error) => setError(e.message));
  }, [isReady, load]);

  const select = (order: RadOrder) => {
    setSelected(order);
    setViewer(null);
    const draft = order.reports.find((r) => r.status === 'draft');
    const latest = draft ?? order.reports[0];
    setFindings(latest?.findings ?? '');
    setImpression(latest?.impression ?? '');
    setIcd(latest?.icdCodes.join(', ') ?? '');
    setSnomed(latest?.snomedCodes.join(', ') ?? '');
    setTemplate(latest?.templateCode ?? 'CT-CHEST');
    setStudyUid(order.study?.studyInstanceUid ?? '');
  };

  const run = async (key: string, fn: () => Promise<void>) => {
    setWorking(key);
    setError(null);
    try {
      await fn();
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setWorking(null);
    }
  };

  const completeStudy = () =>
    selected &&
    run('complete', async () => {
      await apiFetch(`/radiology/orders/${selected.id}/study:complete`, {
        method: 'POST',
        body: JSON.stringify({ studyInstanceUid: studyUid, seriesCount: 1 }),
      });
      setNotice({ tone: 'success', label: `Study completed for ${selected.accession}` });
    });

  const saveDraft = () =>
    selected &&
    run('draft', async () => {
      const body = JSON.stringify({
        templateCode: template,
        findings,
        impression,
        icdCodes: icd.split(',').map((c) => c.trim()).filter(Boolean),
        snomedCodes: snomed.split(',').map((c) => c.trim()).filter(Boolean),
      });
      const draft = selected.reports.find((r) => r.status === 'draft');
      if (draft) {
        await apiFetch(`/radiology/reports/${draft.id}`, { method: 'PUT', body });
      } else {
        await apiFetch(`/radiology/orders/${selected.id}/reports`, { method: 'POST', body });
      }
      setNotice({ tone: 'success', label: 'Draft saved' });
    });

  const sign = () =>
    selected &&
    run('sign', async () => {
      const draft = selected.reports.find((r) => r.status === 'draft');
      if (!draft) throw new Error('No draft to sign');
      await apiFetch(`/radiology/reports/${draft.id}/sign`, { method: 'POST' });
      setNotice({ tone: 'success', label: 'Report signed' });
    });

  const raiseCritical = () =>
    selected &&
    run('critical', async () => {
      const signed = selected.reports.find((r) => r.status === 'signed');
      if (!signed) throw new Error('Sign the report before raising a critical finding');
      await apiFetch(`/radiology/reports/${signed.id}/critical-findings`, {
        method: 'POST',
        body: JSON.stringify({ severity: 'critical', finding: impression, slaMinutes: 30 }),
      });
      setNotice({ tone: 'warning', label: 'Critical finding raised (SLA 30 min)' });
    });

  const acknowledge = (id: string) =>
    run(`ack-${id}`, async () => {
      await apiFetch(`/radiology/critical-findings/${id}/ack`, {
        method: 'POST',
        body: JSON.stringify({ note: 'acknowledged from worklist' }),
      });
      setNotice({ tone: 'success', label: 'Critical finding acknowledged' });
    });

  const openViewer = () =>
    selected?.study &&
    run('viewer', async () => {
      const res = await apiFetch<{ token: string; viewerUrl: string | null }>(
        `/radiology/studies/${selected.study?.id}/viewer-token`,
        { method: 'POST' },
      );
      setViewer(res);
      setNotice({ tone: 'information', label: 'Study-scoped viewer token issued (5 min)' });
    });

  return (
    <Stack gap="lg">
      <PageHeader
        title="Radiology"
        description="Modality worklist, structured reporting and critical findings. PACS = Orthanc, viewer = OHIF."
      />
      {error && <StatusIndicator tone="critical" label={error} />}
      {notice && <StatusIndicator tone={notice.tone} label={notice.label} />}

      <ContentPanel>
        <h3 style={{ marginTop: 0 }}>Radiologist worklist</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Accession</TableHead>
              <TableHead>Modality</TableHead>
              <TableHead>Body part</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Reports</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.map((order) => (
              <TableRow key={order.id}>
                <TableCell>{order.accession}</TableCell>
                <TableCell>{order.modality}</TableCell>
                <TableCell>{order.bodyPart}</TableCell>
                <TableCell>
                  <Badge variant={order.priority === 'routine' ? 'neutral' : 'warning'}>{order.priority}</Badge>
                </TableCell>
                <TableCell>
                  <Badge variant={statusVariants[order.status] ?? 'neutral'}>{order.status}</Badge>
                </TableCell>
                <TableCell>{order.reports.length}</TableCell>
                <TableCell>
                  <Button variant="secondary" onClick={() => select(order)}>
                    Open
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {orders.length === 0 && (
              <TableRow>
                <TableCell colSpan={7}>No imaging orders on the worklist.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </ContentPanel>

      {selected && (
        <ContentPanel>
          <h3 style={{ marginTop: 0 }}>{`Study ${selected.accession} — ${selected.modality} ${selected.bodyPart}`}</h3>
          <Stack gap="md">
            {!selected.study && (
              <Stack direction="row" gap="sm" align="end">
                <Input label="DICOM study instance UID" value={studyUid} onChange={(e) => setStudyUid(e.target.value)} />
                <Button onClick={completeStudy} disabled={working === 'complete' || !studyUid}>
                  Complete study
                </Button>
              </Stack>
            )}
            {selected.study && (
              <StatusIndicator
                tone="information"
                label={`Study ${selected.study.studyInstanceUid} — ${selected.study.seriesCount ?? 0} series`}
              />
            )}
            <Input label="Template" value={template} onChange={(e) => setTemplate(e.target.value)} />
            <Input label="Findings" value={findings} onChange={(e) => setFindings(e.target.value)} />
            <Input label="Impression" value={impression} onChange={(e) => setImpression(e.target.value)} />
            <Input label="ICD codes (comma separated)" value={icd} onChange={(e) => setIcd(e.target.value)} />
            <Input label="SNOMED codes (comma separated)" value={snomed} onChange={(e) => setSnomed(e.target.value)} />
            <Stack direction="row" gap="sm">
              <Button onClick={saveDraft} disabled={working === 'draft'}>
                Save draft
              </Button>
              <Button onClick={sign} disabled={working === 'sign'}>
                Sign report
              </Button>
              <Button variant="secondary" onClick={raiseCritical} disabled={working === 'critical'}>
                Raise critical finding
              </Button>
              <Button variant="secondary" onClick={openViewer} disabled={!selected.study || working === 'viewer'}>
                Open in OHIF
              </Button>
            </Stack>
            {viewer &&
              (viewer.viewerUrl ? (
                <iframe
                  title="OHIF viewer"
                  src={`${viewer.viewerUrl}?StudyInstanceUIDs=${selected.study?.studyInstanceUid ?? ''}&token=${viewer.token}`}
                  style={{ width: '100%', height: 520, border: 0 }}
                />
              ) : (
                <StatusIndicator
                  tone="warning"
                  label={`OHIF URL not configured (squamed.radiology.ohif-url). Study-scoped token: ${viewer.token.slice(0, 24)}…`}
                />
              ))}
            {selected.reports.length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Version</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Impression</TableHead>
                    <TableHead>Amends</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {selected.reports.map((report) => (
                    <TableRow key={report.id}>
                      <TableCell>v{report.reportVersion}</TableCell>
                      <TableCell>
                        <Badge variant={report.status === 'signed' ? 'success' : 'neutral'}>{report.status}</Badge>
                      </TableCell>
                      <TableCell>{report.impression}</TableCell>
                      <TableCell>{report.amendsReportId ? 'yes' : '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </ContentPanel>
      )}

      <ContentPanel>
        <h3 style={{ marginTop: 0 }}>Open critical findings</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Severity</TableHead>
              <TableHead>Finding</TableHead>
              <TableHead>SLA</TableHead>
              <TableHead>Escalation</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {critical.map((row) => (
              <TableRow key={row.id}>
                <TableCell>
                  <Badge variant="critical">{row.severity}</Badge>
                </TableCell>
                <TableCell>{row.finding}</TableCell>
                <TableCell>{row.slaMinutes} min</TableCell>
                <TableCell>
                  {row.escalationLevel === 0 ? 'none' : `L${row.escalationLevel} ${row.escalationRole}`}
                </TableCell>
                <TableCell>
                  <Button variant="secondary" onClick={() => acknowledge(row.id)} disabled={working === `ack-${row.id}`}>
                    Acknowledge
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {critical.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>No open critical findings.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </ContentPanel>
    </Stack>
  );
}
