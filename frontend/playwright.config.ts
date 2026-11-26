import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { defineConfig, devices } from '@playwright/test'

// Every run starts from an empty workspace, so the test never touches the models of the real one.
const workspace = mkdtempSync(join(tmpdir(), 'studio-e2e-'))

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
  webServer: [
    {
      command: 'mvn -q -f ../backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=8090',
      url: 'http://localhost:8090/api/metamodels',
      env: { STUDIO_WORKSPACE_DIR: workspace },
      timeout: 240_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --port 5174 --strictPort',
      url: 'http://localhost:5174',
      env: { BACKEND_URL: 'http://localhost:8090' },
      reuseExistingServer: false,
    },
  ],
})
