import { isRouteErrorResponse, useRouteError } from 'react-router'
import { LogoMark } from '../components/Logo.tsx'
import { buttonClass } from '../lib/ui.ts'

/** 화면을 그리다 예상하지 못한 오류가 났을 때(라우터 errorElement). 전체 화면으로 보여준다. */
export default function RouteErrorPage() {
  const error = useRouteError()
  const notFound = isRouteErrorResponse(error) && error.status === 404
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-3 px-4 text-center">
      <LogoMark size={56} />
      <h1 className="mt-2 text-xl font-bold">{notFound ? '페이지를 찾을 수 없어요' : '문제가 생겼어요'}</h1>
      <p className="text-sm text-text-tertiary">
        {notFound ? '주소가 잘못됐거나 사라진 페이지예요.' : '잠시 후 다시 시도해 주세요. 계속되면 새로고침해 보세요.'}
      </p>
      <div className="mt-4 flex gap-2">
        <button type="button" onClick={() => window.location.reload()} className={buttonClass('secondary')}>
          새로고침
        </button>
        <a href="/" className={buttonClass('primary')}>
          처음으로
        </a>
      </div>
    </main>
  )
}
