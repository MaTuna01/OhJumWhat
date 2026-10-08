import { renderToStaticMarkup } from 'react-dom/server'
import LandingPage from './LandingPage.tsx'

/** 소개 페이지 HTML. 빌드(와 개발 서버)에서 vite.config.ts의 landingPage가 불러 landing.html에 넣는다. */
export function renderLanding() {
  return renderToStaticMarkup(<LandingPage />)
}
