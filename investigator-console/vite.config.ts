import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The console runs on :3000 — the origin registered as a redirect URI + web origin on the Keycloak
// `dolos-console` public client, and the origin the gateway allows via CORS. `global` is aliased
// because sockjs-client references it (it expects a Node-ish global in the browser).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
  },
  define: {
    global: "globalThis",
  },
});
