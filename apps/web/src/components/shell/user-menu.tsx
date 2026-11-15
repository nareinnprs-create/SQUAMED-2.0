'use client';

import { useEffect, useRef, useState } from 'react';
import { Badge, Button, Divider, setLocale, t } from '@squamed/ui';
import { useAuth } from '@squamed/auth';
import { useShellStore } from '@/lib/shell-store';
import type { Locale } from '@squamed/ui';

const LANGUAGES: Array<{ value: Locale; label: string }> = [
  { value: 'en', label: 'English' },
  { value: 'hi', label: 'हिन्दी' },
];

/**
 * User/security menu: identity, roles, permissions, and the language toggle.
 * Sign out stays a visible topbar action (same spot as before the menu
 * existed) rather than hiding inside the dropdown.
 */
export function UserMenu() {
  const { user, logout } = useAuth();
  const locale = useShellStore((state) => state.locale);
  const setStoreLocale = useShellStore((state) => state.setLocale);
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  if (!user) return null;

  function switchLanguage(next: Locale) {
    // Module setLocale takes effect for the render triggered by the store
    // update below; the I18nProvider effect re-syncs it (no-op).
    setLocale(next);
    setStoreLocale(next);
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <div ref={rootRef} style={{ position: 'relative' }}>
        <Button
          variant="ghost"
          size="sm"
          aria-expanded={open}
          aria-label={t('shell.user.menu')}
          title={t('shell.user.menu')}
          onClick={() => setOpen((value) => !value)}
          style={{ gap: 8, fontWeight: 500 }}
        >
          <span
            aria-hidden="true"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 26,
              height: 26,
              borderRadius: '50%',
              backgroundColor: 'var(--sq-primary-soft)',
              color: 'var(--sq-primary)',
              fontSize: 12,
              fontWeight: 700,
            }}
          >
            {(user.displayName || '?').trim().charAt(0).toUpperCase()}
          </span>
          <span style={{ fontSize: 13 }}>{user.displayName}</span>
        </Button>

        {open ? (
          <div
            role="menu"
            aria-label={t('shell.user.menu')}
            style={{
              position: 'absolute',
              top: 'calc(100% + 8px)',
              right: 0,
              width: 280,
              zIndex: 900,
              boxSizing: 'border-box',
              backgroundColor: 'var(--sq-surface)',
              border: '1px solid var(--sq-border)',
              borderRadius: 12,
              boxShadow: '0 16px 48px color-mix(in srgb, var(--sq-fg) 20%, transparent)',
              overflow: 'hidden',
            }}
          >
            <div style={{ padding: '12px 16px' }}>
              <strong style={{ display: 'block', fontSize: 14 }}>{user.displayName}</strong>
              <span style={{ display: 'block', fontSize: 12, color: 'var(--sq-fg-muted)' }}>{user.email}</span>
            </div>
            <Divider />
            <div style={{ padding: '10px 16px', display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {user.roles.map((role) => (
                <Badge key={role} variant="information" size="sm" appearance="soft">
                  {role}
                </Badge>
              ))}
            </div>
            <div
              style={{
                padding: '4px 16px 10px',
                fontSize: 12,
                color: 'var(--sq-fg-muted)',
              }}
            >
              {`${t('shell.user.permissions')}: ${user.permissions.length}`}
            </div>
            <Divider />
            <div style={{ padding: '10px 16px', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 13 }}>{t('shell.user.language')}</span>
              {LANGUAGES.map((language) => (
                <Button
                  key={language.value}
                  variant={locale === language.value ? 'primary' : 'outline'}
                  size="sm"
                  aria-pressed={locale === language.value}
                  onClick={() => switchLanguage(language.value)}
                >
                  {language.label}
                </Button>
              ))}
            </div>
          </div>
        ) : null}
      </div>
      <Button variant="outline" size="sm" onClick={() => void logout()}>
        {t('shell.user.signOut')}
      </Button>
    </div>
  );
}
