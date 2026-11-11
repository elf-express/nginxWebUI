import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    // 覆蓋率門檻（E-150）：依目前實測值取整數下限，低於門檻 vitest run 會失敗（Maven 的 npm-test 也會跟著失敗）
    coverage: {
      enabled: true,
      provider: 'v8',
      include: ['src/**'],
      reporter: ['text-summary'],
      thresholds: {
        statements: 91,
        branches: 92,
        functions: 86,
        lines: 92,
      },
    },
  },
})
