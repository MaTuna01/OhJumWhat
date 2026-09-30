import { useEffect } from 'react'
import { Navigate } from 'react-router'
import { clearReturnTo, peekReturnTo, resolveEntryPath } from '../lib/entry.ts'
import { useMe } from '../queries/me.ts'

/** `/`: 로그인 후 도착하는 곳. 초대 링크 → 최근 조직 → 마이페이지 순으로 보낸다. */
export default function RootRedirect() {
  const { data: me } = useMe()
  const returnTo = peekReturnTo()

  useEffect(() => {
    clearReturnTo()
  }, [])

  if (!me) return null
  return <Navigate to={resolveEntryPath(returnTo, me.lastVisitedOrgId)} replace />
}
