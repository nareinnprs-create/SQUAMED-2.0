import type { ComponentType, CSSProperties } from 'react';
import { format } from 'date-fns';
import { Timeline, TimelineItem, type TimelineTone } from '../primitives/Timeline';
import {
  ClockIcon,
  CreditCardIcon,
  FlaskIcon,
  HospitalIcon,
  NoteIcon,
  PillIcon,
  ProcedureIcon,
  type IconProps,
} from './icons';

export type EncounterTone = 'note' | 'lab' | 'pharmacy' | 'procedure' | 'billing' | 'admission' | 'general';

export interface EncounterTimelineEntry {
  id: string;
  title: string;
  time: Date | string;
  description?: string;
  tone?: EncounterTone;
  user?: string;
}

export interface EncounterTimelineProps {
  entries: EncounterTimelineEntry[];
  className?: string;
  style?: CSSProperties;
}

const toneMeta: Record<EncounterTone, { tone: TimelineTone; Icon: ComponentType<IconProps> }> = {
  note: { tone: 'information', Icon: NoteIcon },
  lab: { tone: 'primary', Icon: FlaskIcon },
  pharmacy: { tone: 'success', Icon: PillIcon },
  procedure: { tone: 'warning', Icon: ProcedureIcon },
  billing: { tone: 'critical', Icon: CreditCardIcon },
  admission: { tone: 'default', Icon: HospitalIcon },
  general: { tone: 'default', Icon: ClockIcon },
};

function formatTime(value: Date | string): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return format(date, 'dd MMM, h:mm a');
}

export function EncounterTimeline({ entries, className, style }: EncounterTimelineProps) {
  if (entries.length === 0) {
    return (
      <div className={className} style={{ fontSize: 13, color: 'var(--sq-fg-muted)', ...style }}>
        No encounters recorded
      </div>
    );
  }

  return (
    <Timeline className={className} style={style}>
      {entries.map((entry) => {
        const { tone, Icon } = toneMeta[entry.tone ?? 'general'];
        return (
          <TimelineItem
            key={entry.id}
            title={entry.title}
            time={formatTime(entry.time)}
            description={entry.description}
            tone={tone}
            icon={<Icon size={11} />}
          >
            {entry.user ? (
              <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>by {entry.user}</span>
            ) : null}
          </TimelineItem>
        );
      })}
    </Timeline>
  );
}