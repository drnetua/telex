import { defineConfig, devices } from "@playwright/test";

const baseURL = process.env.TELEX_BASE_URL ?? "http://localhost:8080";

// The suite runs against `docker compose -f compose.yaml -f compose.e2e.yaml --profile unconfigured up` (app :8080 with
// the `e2e` Spring profile, the installation without Telegram credentials :8081 for AC-119, Mailpit :8025); it does
// not start the stack.
export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: { baseURL, actionTimeout: 15_000, trace: "retain-on-failure" },
  projects: [
    {
      name: "phone",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 360, height: 800 },
      },
    },
    {
      name: "desktop",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1280, height: 800 },
      },
    },
  ],
});
