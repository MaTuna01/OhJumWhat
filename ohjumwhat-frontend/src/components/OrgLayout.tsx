import { Link, NavLink, Outlet, useMatch } from 'react-router'
import { useOrgId } from '../hooks/useOrgId.ts'
import { ApiError } from '../lib/api.ts'
import { buttonClass } from '../lib/ui.ts'
import { useOrganization } from '../queries/orgs.ts'
import { PageLoader, PageMessage } from './PageState.tsx'

const tabs = [
  { to: '', label: '투표', end: true },
  { to: 'schedules', label: '정기 투표', end: false },
  { to: 'stats', label: '통계', end: false },
  { to: 'settings', label: '설정', end: false },
]

/** /orgs/:orgId 아래 화면 공통: 조직 조회(방문 기록), 404 처리, 탭 */
export default function OrgLayout() {
  const orgId = useOrgId()
  const org = useOrganization(orgId)
  // 투표 상세는 Figma 05처럼 조직 머리글·탭 대신 "투표 목록으로" 링크만 둔다.
  const onPollDetail = useMatch('/orgs/:orgId/polls/:pollId') != null

  if (!Number.isInteger(orgId) || (org.error instanceof ApiError && org.error.status === 404)) {
    return (
      <PageMessage title="조직을 찾을 수 없어요">
        <p>조직이 삭제됐거나 멤버가 아니에요.</p>
        <Link to="/me" className={buttonClass('secondary', 'mt-4')}>
          마이페이지로
        </Link>
      </PageMessage>
    )
  }
  if (org.isError) {
    return <PageMessage title="조직 정보를 불러오지 못했어요">{org.error.message}</PageMessage>
  }
  if (org.isPending) {
    return <PageLoader />
  }

  if (onPollDetail) {
    return <Outlet />
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold tracking-tight">{org.data.name}</h1>
        <p className="mt-1 text-sm text-text-tertiary">멤버 {org.data.memberCount}명</p>
        <nav className="mt-4 flex gap-1 border-b border-border-default" aria-label="조직 메뉴">
          {tabs.map((tab) => (
            <NavLink
              key={tab.label}
              to={tab.to === '' ? `/orgs/${orgId}` : `/orgs/${orgId}/${tab.to}`}
              end={tab.end}
              className={({ isActive }) =>
                `-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
                  isActive ? 'border-border-brand text-text-brand' : 'border-transparent text-text-tertiary hover:text-text-primary'
                }`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
      </div>
      <Outlet />
    </div>
  )
}
