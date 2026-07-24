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
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes("node_modules")) return undefined;
            if (id.includes("react-dom") || id.includes("\\react\\") || id.includes("/react/")) return "react-vendor";
            if (id.includes("react-router")) return "router-vendor";
            if (id.includes("recharts")) return "charts-vendor";
            if (id.includes("axios")) return "http-vendor";
            if (id.includes("react-hot-toast")) return "toast-vendor";
            if (id.includes("lucide-react")) return "icons-vendor";
            return undefined;
          },
        },
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      globals: true,
    },
  };
});
