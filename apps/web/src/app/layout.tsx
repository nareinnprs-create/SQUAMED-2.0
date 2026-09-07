import type { Metadata } from 'next';
import { ThemeProvider } from '@squamed/ui';
import { RootProviders } from '@/providers/RootProviders';
import './globals.css';
import './themes.css';

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
          <RootProviders>{children}</RootProviders>
        </ThemeProvider>
      </body>
    </html>
  );
}