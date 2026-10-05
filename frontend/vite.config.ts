/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Tests run in one fixed device zone whatever the host's, so a test can pick an Owner zone that differs from it.
// Asia/Kathmandu (+05:45, no DST) is neither UTC nor the Owner zone the tests save (Asia/Tokyo), so a fallback to
// UTC instead of the device's zone shows a different time and fails.
process.env.TZ = "Asia/Kathmandu";

export default defineConfig({
  plugins: [react()],
  server: {
    // The Spring Boot app (`./gradlew :backend:app:bootRun`) serves the API during `pnpm dev`.
    proxy: {
      "/api": "http://localhost:8080",
      "/webauthn": "http://localhost:8080",
      "/login/webauthn": "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
  },
});
