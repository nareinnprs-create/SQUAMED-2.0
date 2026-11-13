import type { CSSProperties } from 'react';
import { Badge, type BadgeVariant } from '../primitives/Badge';

export type AllergySeverity = 'mild' | 'moderate' | 'severe' | 'life_threatening';

export interface AllergyBadgeProps {
  label: string;
  severity: AllergySeverity;
  className?: string;
  style?: CSSProperties;
}

const severityVariants: Record<AllergySeverity, BadgeVariant> = {
  mild: 'information',
  moderate: 'warning',
  severe: 'critical',
  life_threatening: 'critical',
};

const severityLabels: Record<AllergySeverity, string> = {
  mild: 'mild',
  moderate: 'moderate',
  severe: 'severe',
  life_threatening: 'life-threatening',
};

export function AllergyBadge({ label, severity, className, style }: AllergyBadgeProps) {
  return (
    <Badge
      variant={severityVariants[severity]}
      appearance="soft"
      size="sm"
      aria-label={`${label}, ${severityLabels[severity]} allergy`}
      className={className}
      style={style}
    >
      {label}
    </Badge>
  );
}