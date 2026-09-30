import { Link, Outlet } from 'react-router'
import Logo from './Logo.tsx'
import OrgSwitcher from './OrgSwitcher.tsx'
import ProfileMenu from './ProfileMenu.tsx'

export default function AppLayout() {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-border-default bg-bg-surface/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-3xl items-center gap-2 px-4">
          <Link to="/" aria-label="오점왓 홈" className="shrink-0 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand">
            <Logo size={28} />
          </Link>
          <OrgSwitcher />
          <div className="flex-1" />
          <ProfileMenu />
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
