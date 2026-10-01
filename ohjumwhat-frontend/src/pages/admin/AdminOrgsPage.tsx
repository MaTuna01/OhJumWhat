import { useState } from 'react'
import { AdminSearch, EmptyRow, ListCard, ListRow } from '../../components/AdminParts.tsx'
import { PageLoader } from '../../components/PageState.tsx'
import { useDebouncedValue } from '../../hooks/useDebouncedValue.ts'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { formatDay } from '../../lib/time.ts'
import { ADMIN_LIST_LIMIT, useAdminOrgs } from '../../queries/admin.ts'

/** Figma A04·DA04 조직 목록: 이름 검색, 최근 만든 순 */
export default function AdminOrgsPage() {
  const [query, setQuery] = useState('')
  const q = useDebouncedValue(query.trim())
  const orgs = useAdminOrgs(q)
  const now = useNow(60_000)
  useDocumentTitle('조직', '관리자 콘솔')

  const count = orgs.data?.length ?? 0
  const summary = !orgs.data ? '' : `${q ? '검색 결과' : '조직'} ${count}개${count >= ADMIN_LIST_LIMIT ? '(최대 100개까지 보여요)' : ''} · 최근 만든 순`

  return (
    <div className="space-y-4">
      <AdminSearch value={query} onChange={setQuery} placeholder="조직 이름으로 검색" summary={summary} />
      {orgs.isPending ? (
        <PageLoader />
      ) : orgs.isError ? (
        <p className="text-sm text-text-danger">{orgs.error.message}</p>
      ) : (
        <ListCard>
          {orgs.data.length === 0 && <EmptyRow>{q ? '검색 결과가 없어요.' : '아직 만든 조직이 없어요.'}</EmptyRow>}
          {orgs.data.map((o) => (
            <ListRow
              key={o.id}
              to={`/admin/orgs/${o.id}`}
              title={o.name}
              subtitle={`멤버 ${o.memberCount}명 · 투표 ${o.pollCount}개`}
              meta={
                <>
                  <span className="lg:hidden">{formatDay(o.createdAt, now)}</span>
                  <span className="hidden lg:inline">
                    만든 날 {formatDay(o.createdAt, now)} · {o.lastPollDate ? `최근 투표 ${formatDay(o.lastPollDate, now)}` : '투표 없음'}
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
