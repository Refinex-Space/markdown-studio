/// <reference types="vitest/config" />
import fs from "node:fs";
import path from "node:path";
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

const root = import.meta.dirname;
const shims = path.join(root, "src/shims");
const core = path.join(root, "core");
const EXTENSIONS = ["", ".ts", ".tsx", "/index.ts", "/index.tsx"];
const mermaidHost = path.join(root, "src/host/mermaid.ts");

function existing(base: string): string | null {
  for (const ext of EXTENSIONS) {
    const file = base + ext;
    if (fs.existsSync(file) && fs.statSync(file).isFile()) return file;
  }
  return null;
}

/**
 * The editor core's `@/…` tree, overlaid: a module in `src/shims` (the IDE host's version of a
 * host module such as IPC, keymap or menus) wins over the core module at the same path.
 * Relative imports between the two halves resolve the same way.
 */
function coreOverlay(): Plugin {
  const lookup = (rel: string) => existing(path.join(shims, rel)) ?? existing(path.join(core, rel));
  return {
    name: "core-overlay",
    enforce: "pre",
    resolveId(source, importer) {
      if (source.startsWith("@/")) return lookup(source.slice(2));
      if (source === "@tauri-apps/plugin-opener") return path.join(shims, "tauri/opener.ts");
      // Mermaid gets the host's chart colours (src/host/mermaid.ts imports the real package).
      if (source === "mermaid" && importer !== mermaidHost) return mermaidHost;
      if (!importer || !source.startsWith(".")) return null;
      const from = [shims, core].find((dir) => importer.startsWith(dir + path.sep));
      if (!from) return null;
      const [spec, query] = source.split("?");
      const rel = path.relative(from, path.resolve(path.dirname(importer), spec!));
      if (rel.startsWith("..")) return null;
      const file = lookup(rel);
      return file && query ? `${file}?${query}` : file;
    },
  };
}

export default defineConfig({
  base: "./",
  plugins: [coreOverlay(), react()],
  css: {
    modules: {
      // Stable names: the host layout addresses a few of the core's classes (see src/app/host.css).
      generateScopedName: (name, filename) => `${path.basename(filename).replace(/\.module\.css$/, "")}_${name}`,
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
    target: "chrome120",
    sourcemap: false,
    chunkSizeWarningLimit: 4096,
    assetsInlineLimit: 0,
  },
  test: {
    environment: "jsdom",
    globals: false,
    setupFiles: ["./core/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}", "core/**/*.test.{ts,tsx}"],
  },
});
