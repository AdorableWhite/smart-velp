import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 9091,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true
      },
      '/downloads': {
        target: 'http://localhost:9090',
        changeOrigin: true
      }
    }
  },
  // 定义环境变量前缀，用于在代码中通过 import.meta.env.VITE_* 访问
  envPrefix: 'VITE_',
  // 构建配置
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    // 确保环境变量在构建时可用
    rollupOptions: {
      output: {
        manualChunks: undefined
      }
    }
  }
});
