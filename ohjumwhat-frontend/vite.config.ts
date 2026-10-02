import { readFileSync } from 'node:fs'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

// 백엔드(8080)로 가는 경로. Host 헤더를 유지(changeOrigin: false)해서
// 구글 로그인 리디렉션 URI가 운영과 같이 프론트 주소(5173) 기준으로 만들어지게 한다.
const backend = { target: 'http://localhost:8080', changeOrigin: false }

// 새 버전 안내(UpdateToast): 빌드마다 ID를 코드에 넣고, 같은 값을 dist/version.json으로 내보낸다.
// 배포 전부터 열려 있던 탭은 /version.json을 읽어 자기 ID와 다르면 새로고침을 안내한다.
const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as { version: string }
const buildId = Date.now().toString(36)

function versionFile(): Plugin {
  return {
    name: 'ohjumwhat-version-file',
    apply: 'build',
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'version.json', source: JSON.stringify({ buildId, version }) })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), versionFile()],
  define: {
    __BUILD_ID__: JSON.stringify(buildId),
    __APP_VERSION__: JSON.stringify(version),
  },
  server: {
    proxy: {
      // 투표 채팅 받기(WebSocket, /api/polls/{id}/ws)도 같은 경로로 넘긴다.
      '/api': { ...backend, ws: true },
      '/oauth2': backend,
      '/login/oauth2': backend,
      '/logout': backend,
    },
  },
})
