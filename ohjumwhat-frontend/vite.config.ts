import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 백엔드(8080)로 가는 경로. Host 헤더를 유지(changeOrigin: false)해서
// 구글 로그인 리디렉션 URI가 운영과 같이 프론트 주소(5173) 기준으로 만들어지게 한다.
const backend = { target: 'http://localhost:8080', changeOrigin: false }

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': backend,
      '/oauth2': backend,
      '/login/oauth2': backend,
      '/logout': backend,
    },
  },
})
