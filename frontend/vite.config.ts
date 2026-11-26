import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The dev server forwards API calls to the backend, as nginx does in the container.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': process.env.BACKEND_URL ?? 'http://localhost:8080' },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
