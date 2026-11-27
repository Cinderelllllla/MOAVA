import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
    plugins: [react(), tailwindcss()],
    server: {
        port: 3000,
        proxy: {
            // ML 서버(:8000)는 브라우저가 직접 부르지 않는다. 백엔드 /api/v1/finance/ai/... 가 대신 부른다.
            '/api': { target: 'http://localhost:8080', changeOrigin: true },
            '/ws': { target: 'http://localhost:8080', ws: true },
        },
    },
});
