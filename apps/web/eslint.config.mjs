import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { FlatCompat } from '@eslint/eslintrc';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  ...compat.extends('next/core-web-vitals', 'next/typescript'),
  {
    ignores: ['.next/**', 'node_modules/**', 'dist/**', 'out/**', 'next-env.d.ts'],
  },
  {
    // Section 00.15: zero hardcoded strings in the shell layer — all copy goes
    // through t() (en/hi catalogs). Nav labels and page bodies are data
    // literals, not JSX text; rollout to pages stays progressive (deviation).
    files: ['src/components/shell/**/*.tsx', 'src/app/(app)/layout.tsx'],
    ignores: ['**/*.test.tsx', '**/*.spec.tsx'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: 'JSXText[value=/\\S/]',
          message: 'Hardcoded JSX text: use t() from @squamed/ui (i18n en/hi).',
        },
        {
          selector:
            "JSXAttribute[name.name=/^(aria-label|title|placeholder|alt)$/] > Literal[value=/\\S/]",
          message: 'Hardcoded attribute copy: use t() from @squamed/ui (i18n en/hi).',
        },
      ],
    },
  },
];

export default eslintConfig;