import type { Metadata } from 'next';
import { ThemeProvider } from '@squamed/ui';
import { RootProviders } from '@/providers/RootProviders';
import { ServiceWorkerRegistrar } from '@/components/service-worker-registrar';
import './fonts.css';
import './globals.css';
import './themes.css';

// 02-DESIGN-SYSTEM §3: IBM Plex Sans (Regular/Medium/Semibold — the Devanagari-capable
// spec choice, no Inter) + IBM Plex Mono for instrument numerals, self-hosted via
// fonts.css (vendored woff2 + @font-face). No preloads — unicode-range fetches only
// what renders; Devanagari is a separate family picked up through the font stack.

export const metadata: Metadata = {
  title: {
    default: 'SQUAMED — AI-Native Hospital Operating System',
    template: '%s | SQUAMED',
  },
  description:
    'The operating system of a modern hospital. Connecting patients, clinicians, operations, finance and intelligence in one governed real-time environment.',
  viewport: {
    width: 'device-width',
    initialScale: 1,
    maximumScale: 1,
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <ThemeProvider>
          <RootProviders>
            {children}
            <ServiceWorkerRegistrar />
          </RootProviders>
        </ThemeProvider>
      </body>
    </html>
  );
}
