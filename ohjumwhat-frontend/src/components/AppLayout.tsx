import { Link, Outlet } from 'react-router'
import OrgSwitcher from './OrgSwitcher.tsx'
import ProfileMenu from './ProfileMenu.tsx'

export default function AppLayout() {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-3xl items-center gap-2 px-4">
          <Link to="/" className="shrink-0 text-lg font-bold tracking-tight text-orange-600">
            오점왓
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
