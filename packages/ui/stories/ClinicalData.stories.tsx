import type { Meta, StoryObj } from '@storybook/react';
import { ContextSpine } from '../src/domain/ContextSpine';
import { VitalChip } from '../src/domain/VitalChip';
import { RangeBar } from '../src/domain/RangeBar';
import { LabResultRow } from '../src/domain/LabResultRow';
import { MedOrderLine } from '../src/domain/MedOrderLine';
import { DualConfirm } from '../src/domain/DualConfirm';
import { TriangleIcon, TrendDownIcon, TrendUpIcon } from '../src/domain/icons';

const meta: Meta = {
  title: 'Domain/Clinical Data',
  parameters: { layout: 'padded' },
};

export default meta;

export const ContextSpineSignature: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 880 }}>
      <ContextSpine
        name="Rohit Verma"
        mrn="MQ-2026-0042"
        abha="12-3456-7890-1234"
        facts={['42 yrs', 'Male', 'O+']}
        allergy="Penicillin, Sulfa drugs"
        statuses={[
          { tone: 'critical', label: 'Deteriorating', glyph: <TriangleIcon size={13} /> },
          { tone: 'information', label: 'NPO since 22:00' },
        ]}
        encounterStage="Admitted · Day 2"
      />
      <ContextSpine
        name="Anita Desai"
        mrn="MQ-2026-0107"
        facts={['29 yrs', 'Female']}
        statuses={[{ tone: 'success', label: 'Stable', glyph: <TrendUpIcon size={13} /> }]}
        encounterStage="Pre-op"
      />
    </div>
  ),
};

export const VitalChips: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
      <VitalChip label="HR" value="88" unit="bpm" trend="up" />
      <VitalChip label="BP" value="128/82" unit="mmHg" />
      <VitalChip label="SpO2" value="89" unit="%" trend="down" outOfRange />
      <VitalChip label="Temp" value="36.8" unit="°C" trend="flat" />
    </div>
  ),
};

export const RangeBars: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, width: 420 }}>
      <RangeBar min={110} max={160} low={135} high={145} value={142} ariaLabel="Sodium 142 mmol/L, in band" />
      <RangeBar min={110} max={160} low={135} high={145} value={155} ariaLabel="Sodium 155 mmol/L, high" />
      <RangeBar min={0} max={100} low={20} high={80} value={12} ariaLabel="Score 12, below band" />
    </div>
  ),
};

export const LabResultRows: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', maxWidth: 760 }}>
      <LabResultRow test="Sodium" value={142} unit="mmol/L" scale={{ min: 110, max: 160 }} band={{ low: 135, high: 145 }} />
      <LabResultRow
        test="Potassium"
        value={5.9}
        unit="mmol/L"
        scale={{ min: 2, max: 7 }}
        band={{ low: 3.5, high: 5.1 }}
        status="high"
      />
      <LabResultRow
        test="Hemoglobin"
        value={9.1}
        unit="g/dL"
        scale={{ min: 4, max: 18 }}
        band={{ low: 12, high: 16 }}
        status="low"
      />
    </div>
  ),
};

export const MedOrderLines: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', maxWidth: 820 }}>
      <MedOrderLine drug="Insulin glargine" dose="10 units" route="SC" schedule="22:00 daily" highAlert />
      <MedOrderLine drug="Amoxicillin" dose="500 mg" route="PO" schedule="8h × 5 days" verifiedBy="Dr. K. Rao" />
      <MedOrderLine drug="Pantoprazole" dose="40 mg" route="PO" schedule="OD before food" />
    </div>
  ),
};

export const DualConfirmSignature: StoryObj = {
  render: () => (
    <div style={{ maxWidth: 440 }}>
      <DualConfirm
        name="Rohit Verma"
        mrn="MQ-2026-0042"
        abha="12-3456-7890-1234"
        action="Blood transfusion — 2 units packed RBC"
        onConfirm={() => undefined}
      />
    </div>
  ),
};

export const TrendGlyphs: StoryObj = {
  render: () => (
    <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
      <TrendUpIcon />
      <TrendDownIcon />
      <TriangleIcon />
    </div>
  ),
};
