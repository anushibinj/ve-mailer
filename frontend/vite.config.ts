import { loadEnv, type ConfigEnv } from "vite";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig(({ mode }: ConfigEnv) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    base: env.VITE_BASE_PATH || "/",
    plugins: [react()],
    // Inject the ISO build timestamp at bundle time so the footer can display it.
    define: {
      "import.meta.env.VITE_BUILD_TIME": JSON.stringify(new Date().toISOString()),
    },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      globals: true,
    },
  };
});
