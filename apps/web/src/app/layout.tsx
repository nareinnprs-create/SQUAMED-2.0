import type { Metadata } from 'next';
import { IBM_Plex_Mono, IBM_Plex_Sans, IBM_Plex_Sans_Devanagari } from 'next/font/google';
import { ThemeProvider } from '@squamed/ui';
import { RootProviders } from '@/providers/RootProviders';
import { ServiceWorkerRegistrar } from '@/components/service-worker-registrar';
import './globals.css';
import './themes.css';

// 02-DESIGN-SYSTEM §3: IBM Plex Sans (Regular/Medium/Semibold — the Devanagari-capable
// spec choice, no Inter) + IBM Plex Mono for instrument numerals. Self-hosted by
// next/font; browser fetches only subsets a page uses.
const plexSans = IBM_Plex_Sans({
  subsets: ['latin', 'latin-ext'],
  weight: ['400', '500', '600'],
  variable: '--font-plex-sans',
  display: 'swap',
});
// Devanagari is a separate family on Google Fonts — loaded as a second face;
// unicode-range means Hindi text picks it up automatically.
const plexSansDeva = IBM_Plex_Sans_Devanagari({
  subsets: ['devanagari', 'latin'],
  weight: ['400', '500', '600'],
  variable: '--font-plex-sans-deva',
  display: 'swap',
});
const plexMono = IBM_Plex_Mono({
  subsets: ['latin', 'latin-ext'],
  weight: ['400', '500'],
  variable: '--font-plex-mono',
  display: 'swap',
});

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
    <html lang="en" className={`${plexSans.variable} ${plexSansDeva.variable} ${plexMono.variable}`}>
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