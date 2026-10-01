import { Link, Outlet } from 'react-router'
import Logo from './Logo.tsx'
import NoticeBell from './NoticeBell.tsx'
import OrgSwitcher from './OrgSwitcher.tsx'
import ProfileMenu from './ProfileMenu.tsx'
import UpdateToast from './UpdateToast.tsx'

// 콘텐츠 폭: 모바일·태블릿은 768px, 데스크톱(lg)은 Figma 데스크톱 와이어프레임과 같은 1024px(+ 좌우 여백)
const container = 'mx-auto max-w-3xl px-4 lg:max-w-[66rem]'

export default function AppLayout() {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-border-default bg-bg-surface/90 backdrop-blur">
        <div className={`${container} flex h-14 items-center gap-2`}>
          <Link to="/" aria-label="오점왓 홈" className="shrink-0 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand">
            <Logo size={28} />
          </Link>
          <OrgSwitcher />
          <div className="flex-1" />
          <NoticeBell />
          <ProfileMenu />
        </div>
      </header>
      <main className={`${container} py-6 lg:pt-8 lg:pb-10`}>
        <Outlet />
      </main>
      <UpdateToast />
    </div>
  )
}
