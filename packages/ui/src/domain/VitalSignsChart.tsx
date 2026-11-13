import type { CSSProperties } from 'react';
import { cn } from '../lib/cn';

export interface VitalPoint {
  time: number;
  hr?: number;
  bpSystolic?: number;
  bpDiastolic?: number;
  rr?: number;
  spo2?: number;
  temp?: number;
}

export interface VitalSignsChartProps {
  series: VitalPoint[];
  width?: number;
  height?: number;
  className?: string;
  style?: CSSProperties;
}

interface BuiltLine {
  points: Array<{ x: number; y: number }>;
  min: number;
  max: number;
}

const LEFT = 30;
const RIGHT = 30;
const TOP = 12;
const BOTTOM = 22;

function buildLine(values: Array<number | undefined>, plotWidth: number, plotHeight: number): BuiltLine | null {
  const defined = values.filter((value): value is number => value !== undefined);
  if (defined.length === 0) return null;
  const rawMin = Math.min(...defined);
  const rawMax = Math.max(...defined);
  const span = rawMax - rawMin || 1;
  const pad = span * 0.12;
  const min = rawMin - pad;
  const max = rawMax + pad;
  const total = Math.max(1, values.length - 1);
  const points = values
    .map((value, index) =>
      value === undefined
        ? null
        : {
            x: LEFT + (index / total) * plotWidth,
            y: TOP + (1 - (value - min) / (max - min)) * plotHeight,
          },
    )
    .filter((point): point is { x: number; y: number } => point !== null);
  return { points, min, max };
}

function toPath(points: Array<{ x: number; y: number }>): string {
  return points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)},${point.y.toFixed(1)}`)
    .join(' ');
}

function tickY(value: number, line: BuiltLine, plotHeight: number): number {
  return TOP + (1 - (value - line.min) / (line.max - line.min)) * plotHeight;
}

function tickValues(line: BuiltLine): number[] {
  return [line.max, (line.max + line.min) / 2, line.min];
}

export function VitalSignsChart({ series, width = 360, height = 160, className, style }: VitalSignsChartProps) {
  const plotWidth = Math.max(0, width - LEFT - RIGHT);
  const plotHeight = Math.max(0, height - TOP - BOTTOM);
  const hr = buildLine(
    series.map((point) => point.hr),
    plotWidth,
    plotHeight,
  );
  const spo2 = buildLine(
    series.map((point) => point.spo2),
    plotWidth,
    plotHeight,
  );

  if (!hr && !spo2) {
    return (
      <div
        className={cn(className)}
        style={{
          width,
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 13,
          color: 'var(--sq-fg-muted)',
          border: '1px solid var(--sq-border)',
          borderRadius: 8,
          boxSizing: 'border-box',
          ...style,
        }}
      >
        No vital data
      </div>
    );
  }

  const lastIndex = Math.max(0, series.length - 1);

  return (
    <div className={cn(className)} style={{ display: 'inline-flex', flexDirection: 'column', gap: 8, ...style }}>
      <div aria-hidden="true" style={{ display: 'flex', gap: 16, fontSize: 12, color: 'var(--sq-fg-muted)' }}>
        {hr ? (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: 2, backgroundColor: 'var(--sq-critical)' }} />
            Heart rate (bpm)
          </span>
        ) : null}
        {spo2 ? (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: 2, backgroundColor: 'var(--sq-information)' }} />
            SpO2 (%)
          </span>
        ) : null}
      </div>
      <svg
        role="img"
        aria-label="Vitals chart: heart rate and oxygen saturation over time"
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        fontFamily="inherit"
        style={{
          display: 'block',
          boxSizing: 'border-box',
          border: '1px solid var(--sq-border)',
          borderRadius: 8,
          backgroundColor: 'var(--sq-surface)',
        }}
      >
        {[0.2, 0.4, 0.6, 0.8].map((fraction, index) => (
          <line
            key={index}
            x1={LEFT}
            x2={width - RIGHT}
            y1={TOP + fraction * plotHeight}
            y2={TOP + fraction * plotHeight}
            stroke="var(--sq-border)"
            strokeWidth="1"
            strokeDasharray="4 4"
          />
        ))}
        <text x={LEFT} y={TOP - 4} fontSize={8} fill="var(--sq-fg-muted)">
          {hr ? 'bpm' : ''}
        </text>
        <text x={width - RIGHT} y={TOP - 4} fontSize={8} fill="var(--sq-fg-muted)" textAnchor="end">
          {spo2 ? '%' : ''}
        </text>
        <text x={LEFT} y={height - 6} fontSize={9} fill="var(--sq-fg-muted)">
          0
        </text>
        <text x={width - RIGHT} y={height - 6} fontSize={9} fill="var(--sq-fg-muted)" textAnchor="end">
          {lastIndex}
        </text>
        {hr ? (
          <>
            <polyline
              points={toPath(hr.points)}
              fill="none"
              stroke="var(--sq-critical)"
              strokeWidth="2"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
            {hr.points.map((point, index) => (
              <circle key={index} cx={point.x} cy={point.y} r="2.5" fill="var(--sq-critical)" />
            ))}
            {tickValues(hr).map((value, index) => (
              <text
                key={index}
                x={LEFT - 6}
                y={tickY(value, hr, plotHeight) + 3}
                fontSize={8}
                fill="var(--sq-fg-muted)"
                textAnchor="end"
              >
                {Math.round(value)}
              </text>
            ))}
          </>
        ) : null}
        {spo2 ? (
          <>
            <polyline
              points={toPath(spo2.points)}
              fill="none"
              stroke="var(--sq-information)"
              strokeWidth="2"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
            {spo2.points.map((point, index) => (
              <circle key={index} cx={point.x} cy={point.y} r="2.5" fill="var(--sq-information)" />
            ))}
            {tickValues(spo2).map((value, index) => (
              <text
                key={index}
                x={width - RIGHT + 6}
                y={tickY(value, spo2, plotHeight) + 3}
                fontSize={8}
                fill="var(--sq-fg-muted)"
              >
                {Math.round(value)}
              </text>
            ))}
          </>
        ) : null}
      </svg>
    </div>
  );
}