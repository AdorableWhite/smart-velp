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
  }
});
