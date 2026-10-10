/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { validateProductionApiBase } from './src/api/productionBase.ts'

export default defineConfig(({ command, mode }) => {
  if (command === 'build') {
    validateProductionApiBase(loadEnv(mode, process.cwd(), 'VITE_').VITE_API_BASE_URL)
  }
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': process.env.SIGNIFY_API_PROXY ?? 'http://localhost:8080',
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  }
})
