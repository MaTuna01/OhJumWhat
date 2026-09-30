import { Navigate, Outlet, useLocation } from 'react-router'
import { isUnauthorized } from '../lib/api.ts'
import { saveReturnTo } from '../lib/entry.ts'
import { useMe } from '../queries/me.ts'
import { FullPageLoader, FullPageMessage } from './FullPageMessage.tsx'

/** 로그인한 사용자만 하위 화면을 본다. 아니면 지금 경로를 기억해 두고 로그인 화면으로 보낸다. */
export default function RequireAuth() {
  const location = useLocation()
  const me = useMe()

  if (me.isPending) {
    return <FullPageLoader />
  }
  if (me.isError) {
    if (isUnauthorized(me.error)) {
      if (location.pathname !== '/') {
        saveReturnTo(location.pathname + location.search)
      }
      return <Navigate to="/login" replace />
    }
    return (
      <FullPageMessage title="서버에 연결하지 못했어요">
        <button
          type="button"
          onClick={() => me.refetch()}
          className="rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-700"
        >
          다시 시도
        </button>
      </FullPageMessage>
    )
  }
  return <Outlet />
}
