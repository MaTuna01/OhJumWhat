import { useState } from 'react'
import { AdminSearch, EmptyRow, ListCard, ListRow } from '../../components/AdminParts.tsx'
import Badge from '../../components/Badge.tsx'
import { PageLoader } from '../../components/PageState.tsx'
import { useDebouncedValue } from '../../hooks/useDebouncedValue.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { formatDay } from '../../lib/time.ts'
import { ADMIN_LIST_LIMIT, useAdminUsers } from '../../queries/admin.ts'

/** Figma A02·DA02 회원 목록: 이름·이메일 검색, 최근 가입 순. 이용 제한이 걸린 사람은 「제한 중」 */
export default function AdminUsersPage() {
  const [query, setQuery] = useState('')
  const q = useDebouncedValue(query.trim())
  const users = useAdminUsers(q)
  const now = useNow(60_000)
  useDocumentTitle('회원', '관리자 콘솔')

  const count = users.data?.length ?? 0
  const summary = !users.data ? '' : `${q ? '검색 결과' : '회원'} ${count}명${count >= ADMIN_LIST_LIMIT ? '(최대 100명까지 보여요)' : ''} · 최근 가입 순`

  return (
    <div className="space-y-4">
      <AdminSearch value={query} onChange={setQuery} placeholder="이름이나 이메일로 검색" summary={summary} />
      {users.isPending ? (
        <PageLoader />
      ) : users.isError ? (
        <p className="text-sm text-text-danger">{users.error.message}</p>
      ) : (
        <ListCard>
          {users.data.length === 0 && <EmptyRow>{q ? '검색 결과가 없어요.' : '아직 회원이 없어요.'}</EmptyRow>}
          {users.data.map((u) => (
            <ListRow
              key={u.id}
              to={`/admin/users/${u.id}`}
              person={{ name: u.name, imageUrl: u.profileImageUrl }}
              title={u.name}
              badge={
                <>
                  {u.role === 'ADMIN' && <Badge tone="brand">관리자</Badge>}
                  {u.restricted && <Badge tone="danger">제한 중</Badge>}
                </>
              }
              subtitle={u.googleName !== u.name ? `${u.email} · 구글 이름 ${u.googleName}` : u.email}
              meta={
                <>
                  조직 {u.organizationCount}개
                  <span className="hidden lg:inline">
                    {' '}
                    · 가입 {formatDay(u.createdAt, now)} · 최근 로그인 {u.lastLoginAt ? formatDay(u.lastLoginAt, now) : '기록 없음'}
                  </span>
                </>
              }
            />
          ))}
        </ListCard>
      )}
    </div>
  )
}
