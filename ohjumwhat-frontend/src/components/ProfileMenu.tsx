import { useCallback, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useDismiss } from '../hooks/useDismiss.ts'
import { useLogout, useMe } from '../queries/me.ts'
import Avatar from './Avatar.tsx'

export default function ProfileMenu() {
  const { data: me } = useMe()
  const logout = useLogout()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const close = useCallback(() => setOpen(false), [])
  useDismiss(ref, open, close)

  if (!me) return null

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="프로필 메뉴"
        className="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-orange-500"
      >
        <Avatar name={me.name} imageUrl={me.profileImageUrl} />
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-20 mt-2 w-56 overflow-hidden rounded-xl border border-stone-200 bg-white shadow-lg">
          <div className="border-b border-stone-100 px-4 py-3">
            <p className="truncate text-sm font-semibold">{me.name}</p>
            <p className="truncate text-xs text-stone-500">{me.email}</p>
          </div>
          <Link role="menuitem" to="/me" onClick={close} className="block px-4 py-2.5 text-sm hover:bg-stone-50">
            마이페이지
          </Link>
          <button
            role="menuitem"
            type="button"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            className="block w-full px-4 py-2.5 text-left text-sm text-stone-600 hover:bg-stone-50 disabled:opacity-50"
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  )
}
