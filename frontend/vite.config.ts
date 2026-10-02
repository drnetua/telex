/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

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
