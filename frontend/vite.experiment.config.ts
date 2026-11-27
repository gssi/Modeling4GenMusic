import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The static site of the experiment: a page of its own that needs no backend. The cases, with the
// exercises generated for them, are served as they are in experiment/cases/. Relative paths
// everywhere, so that the site works under whatever path it is published at.
export default defineConfig({
  plugins: [react()],
  base: './',
  publicDir: '../experiment/cases',
  build: {
    outDir: 'dist-experiment',
    emptyOutDir: true,
    rollupOptions: { input: 'experiment.html' },
  },
})
