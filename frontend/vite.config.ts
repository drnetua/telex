/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Tests run in UTC whatever the host's zone, so a test can pick an Owner zone that differs from the device's.
process.env.TZ = "UTC";

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
