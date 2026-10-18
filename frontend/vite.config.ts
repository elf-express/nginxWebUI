import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 產物直接寫進 Maven 的 target/classes，jar 以 /js/spa/* 提供。
// 入口檔名固定（Freemarker 以 ?v=版本 做快取失效），共用 chunk 帶 hash。
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: {
    outDir: fileURLToPath(new URL('../target/classes/static/js/spa', import.meta.url)),
    emptyOutDir: true,
    rollupOptions: {
      input: {
        basic: fileURLToPath(new URL('./src/pages/basic/main.ts', import.meta.url)),
      },
      output: {
        entryFileNames: '[name].js',
        chunkFileNames: 'chunks/[name]-[hash].js',
        assetFileNames: '[name][extname]',
      },
    },
  },
})
