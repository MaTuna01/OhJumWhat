import { NavLink, Outlet, useMatch } from 'react-router'
import NotFoundPage from '../pages/NotFoundPage.tsx'
import { useMe } from '../queries/me.ts'

const tabs = [
  { to: '/admin', label: '개요', end: true },
  { to: '/admin/users', label: '회원', end: true },
  { to: '/admin/orgs', label: '조직', end: true },
  { to: '/admin/sanctions', label: '제재', end: true },
  { to: '/admin/blocks', label: '차단', end: true },
  { to: '/admin/notices', label: '공지', end: true },
  { to: '/admin/reports', label: '신고', end: true },
]

/**
 * /admin 아래 화면 공통(Figma 「관리자 콘솔」): 관리자가 아니면 없는 페이지처럼 보여준다(실제 권한은 서버가 확인).
 * 목록 화면에는 제목과 탭을, 상세 화면(회원·조직·투표)에는 각 화면의 "‹ 목록" 링크만 둔다.
 */
export default function AdminLayout() {
  const { data: me } = useMe()
  const onDetail = useMatch('/admin/:section/:id') != null

  if (!me?.admin) {
    return <NotFoundPage />
  }

  return (
    <div className="space-y-6">
      {!onDetail && (
        <div>
          <h1 className="text-2xl font-bold tracking-tight">관리자 콘솔</h1>
          <p className="mt-1 text-sm text-text-tertiary">서비스 전체의 회원과 조직을 관리해요</p>
          {/* 탭이 화면보다 넓으면(모바일) 탭 줄만 가로로 넘긴다. */}
          <nav className="mt-4 overflow-x-auto [scrollbar-width:none]" aria-label="관리자 메뉴">
            <div className="flex w-max min-w-full gap-1 border-b border-border-default">
              {tabs.map((tab) => (
                <NavLink
                  key={tab.to}
                  to={tab.to}
                  end={tab.end}
                  className={({ isActive }) =>
                    `-mb-px shrink-0 border-b-2 px-3 py-2 text-sm font-medium whitespace-nowrap ${
                      isActive ? 'border-border-brand text-text-brand' : 'border-transparent text-text-tertiary hover:text-text-primary'
                    }`
                  }
                >
                  {tab.label}
                </NavLink>
              ))}
            </div>
          </nav>
        </div>
      )}
      <Outlet />
    </div>
  )
}
