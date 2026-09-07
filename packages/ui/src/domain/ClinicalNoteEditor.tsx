'use client';

import type { CSSProperties } from 'react';
import { Button } from '../primitives/Button';
import { Textarea } from '../primitives/Textarea';
import { cn } from '../lib/cn';

export interface ClinicalNoteSection {
  id: string;
  title: string;
  placeholder?: string;
}

export interface ClinicalNoteEditorProps {
  value: string;
  onChange: (value: string) => void;
  sections?: ClinicalNoteSection[];
  onSignature?: () => void;
  userId?: string;
  className?: string;
  style?: CSSProperties;
}

const EMPTY_DRAFTS: Record<string, string> = {};

function serializeNote(drafts: Record<string, string>, sections: ClinicalNoteSection[]): string {
  return sections
    .map((section) => {
      const text = drafts[section.id] ?? '';
      return text.trim() ? `${section.title}\n${text}` : '';
    })
    .filter(Boolean)
    .join('\n\n');
}

function parseNote(value: string, sections: ClinicalNoteSection[]): Record<string, string> {
  const result: Record<string, string> = {};
  let from = 0;
  for (let i = 0; i < sections.length; i++) {
    const title = sections[i].title;
    const start = value.indexOf(title, from);
    if (start === -1) {
      result[sections[i].id] = '';
      continue;
    }
    const bodyStart = start + title.length;
    const next = i + 1 < sections.length ? value.indexOf(sections[i + 1].title, bodyStart) : -1;
    const body = value.slice(bodyStart, next === -1 ? undefined : next);
    result[sections[i].id] = body.replace(/^\s*\r?\n/, '').trim();
    from = bodyStart;
  }
  return result;
}

export function ClinicalNoteEditor({
  value,
  onChange,
  sections,
  onSignature,
  userId,
  className,
  style,
}: ClinicalNoteEditorProps) {
  const sectionList = sections ?? [];
  const hasSections = sectionList.length > 0;
  const drafts = hasSections ? parseNote(value, sectionList) : EMPTY_DRAFTS;
  const currentText = hasSections ? serializeNote(drafts, sectionList) : value;
  const chars = currentText.length;
  const words = currentText.trim() ? currentText.trim().split(/\s+/).length : 0;

  const handleSectionChange = (id: string, text: string) => {
    const nextDrafts = { ...drafts, [id]: text };
    onChange(serializeNote(nextDrafts, sectionList));
  };

  return (
    <div className={cn(className)} style={{ display: 'flex', flexDirection: 'column', gap: 12, ...style }}>
      {hasSections ? (
        sectionList.map((section) => (
          <Textarea
            key={section.id}
            label={section.title}
            placeholder={section.placeholder}
            value={drafts[section.id] ?? ''}
            onChange={(event) => handleSectionChange(section.id, event.target.value)}
            rows={4}
          />
        ))
      ) : (
        <Textarea
          label="Note"
          placeholder="Write clinical note..."
          value={value}
          onChange={(event) => onChange(event.target.value)}
          rows={6}
        />
      )}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
        <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
          {words} words · {chars} characters
          {userId ? ` · signing as ${userId}` : ''}
        </span>
        {onSignature ? (
          <Button variant="primary" size="sm" onClick={onSignature}>
            Sign note
          </Button>
        ) : null}
      </div>
    </div>
  );
}