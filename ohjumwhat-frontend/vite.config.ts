import { readFileSync } from 'node:fs'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, runnerImport, type Plugin } from 'vite'

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

// 소개 페이지(landing.html): 로그인하지 않은 방문자가 /에서 보는 페이지라 검색 로봇이 JS 없이 읽도록
// src/landing/LandingPage.tsx를 HTML로 그려 넣는다(브라우저에서 React를 붙이지 않는다). 개발 서버에서는 /landing.html로 본다.
function landingPage(): Plugin {
  const marker = '<!--landing-->'
  return {
    name: 'ohjumwhat-landing-page',
    transformIndexHtml: {
      order: 'pre',
      async handler(html, ctx) {
        if (!ctx.filename.endsWith('landing.html')) return html
        if (!html.includes(marker)) throw new Error(`landing.html에 ${marker}가 없다`)
        const { module } = await runnerImport<{ renderLanding: () => string }>('/src/landing/render.tsx', {
          configFile: false,
          root: import.meta.dirname,
          plugins: [react()],
          logLevel: 'error',
        })
        return html.replace(marker, module.renderLanding())
      },
    },
    // landing.html이 빠지면 서버가 /에 noindex인 앱을 줘서 검색에서 조용히 사라진다. 그래서 빌드를 실패시킨다.
    // HTML은 Vite의 generateBundle에서 나오므로 그 뒤(post)에 본다.
    generateBundle: {
      order: 'post',
      handler(_, bundle) {
        const page = bundle['landing.html']
        if (page?.type !== 'asset' || !String(page.source).includes('<h1')) {
          this.error('dist/landing.html에 소개 페이지 본문이 없다(build.rolldownOptions.input의 landing 확인)')
        }
        if (String(page.source).includes('<script')) {
          this.error('dist/landing.html에 스크립트가 있다(소개 페이지는 JS 없이 읽혀야 한다)')
        }
      },
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), versionFile(), landingPage()],
  define: {
    __BUILD_ID__: JSON.stringify(buildId),
    __APP_VERSION__: JSON.stringify(version),
  },
  build: {
    // 앱(index.html)과 소개 페이지(landing.html). 서버가 /에서 로그인 여부로 둘 중 하나를 준다.
    rolldownOptions: {
      input: {
        index: 'index.html',
        landing: 'landing.html',
      },
    },
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
