'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useAuth } from '@squamed/auth';
import { Button, Card, CardContent, Input, StatusIndicator } from '@squamed/ui';

export default function LoginPage() {
  const router = useRouter();
  const { login, verifyMfa } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [mfaCode, setMfaCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleLogin(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const result = await login(username, password);
    setSubmitting(false);
    if (result.step === 'success') {
      router.push('/dashboard');
    } else if (result.step === 'mfa_required') {
      setMfaToken(result.mfaToken);
    } else {
      setError('Invalid credentials. Please check your username and password.');
    }
  }

  async function handleMfa(event: React.FormEvent) {
    event.preventDefault();
    if (!mfaToken) return;
    setSubmitting(true);
    setError(null);
    const result = await verifyMfa(mfaToken, mfaCode);
    setSubmitting(false);
    if (result.step === 'success') {
      router.push('/dashboard');
    } else {
      setError('Invalid verification code.');
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--sq-bg)',
      }}
    >
      <Card style={{ width: 380 }}>
        <CardContent>
          <div style={{ marginBottom: 20 }}>
            <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700 }}>SQUAMED</h1>
            <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--sq-fg-muted)' }}>
              AI-native hospital operating system
            </p>
          </div>

          {mfaToken ? (
            <form onSubmit={handleMfa} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <Input
                label="6-digit verification code"
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value)}
                placeholder="000000"
                size="md"
                autoFocus
              />
              {error ? <StatusIndicator tone="critical" label={error} /> : null}
              <Button type="submit" disabled={submitting || mfaCode.length !== 6}>
                {submitting ? 'Verifying...' : 'Verify'}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setMfaToken(null);
                  setMfaCode('');
                }}
              >
                Back to sign in
              </Button>
            </form>
          ) : (
            <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <Input
                label="Email or phone"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin@squamed.local"
                size="md"
                autoComplete="username"
              />
              <Input
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                size="md"
                autoComplete="current-password"
              />
              {error ? <StatusIndicator tone="critical" label={error} /> : null}
              <Button type="submit" disabled={submitting || !username || !password}>
                {submitting ? 'Signing in...' : 'Sign in'}
              </Button>
              <div style={{ fontSize: 12, color: 'var(--sq-fg-muted)', textAlign: 'center' }}>
                Default dev account: admin@squamed.local
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}