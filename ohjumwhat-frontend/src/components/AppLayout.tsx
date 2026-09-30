import { Link, Outlet } from 'react-router'
import ProfileMenu from './ProfileMenu.tsx'

export default function AppLayout() {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-3xl items-center gap-3 px-4">
          <Link to="/" className="text-lg font-bold tracking-tight text-orange-600">
            오점왓
          </Link>
          {/* 조직 전환 드롭다운은 3단계에서 추가한다. */}
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
