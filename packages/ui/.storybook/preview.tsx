import React from 'react';
import type { Preview } from '@storybook/react';
import { ThemeProvider } from '../src/theme/ThemeProvider';

// Chunk-00 item 2: light clinical mode only (02-DESIGN-SYSTEM §1).
const preview: Preview = {
  parameters: {
    backgrounds: { disable: true },
  },
  decorators: [
    (Story) => (
      <ThemeProvider initialMode="clinical">
        <div style={{ margin: 16 }}>
          <Story />
        </div>
      </ThemeProvider>
    ),
  ],
};

export default preview;
