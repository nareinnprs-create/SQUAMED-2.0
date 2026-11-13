import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  esbuild: { jsx: 'automatic' },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    setupFiles: ['./vitest.setup.ts'],
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@squamed/ui': path.resolve(__dirname, '../../packages/ui/src'),
      '@squamed/design-system': path.resolve(__dirname, '../../packages/design-system/src'),
    },
  },
});
