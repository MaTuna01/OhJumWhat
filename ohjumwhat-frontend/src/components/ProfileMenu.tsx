import { useCallback, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useDismiss } from '../hooks/useDismiss.ts'
import { newGuestbookLabel } from '../lib/guestbook.ts'
import { useGuestbookAlerts } from '../queries/guestbook.ts'
import { useLogout, useMe } from '../queries/me.ts'
import Avatar from './Avatar.tsx'
import Badge from './Badge.tsx'

/**
 * 상단 바 프로필 메뉴(Figma TopBar 「새 방명록 점」, ProfileMenu). 내 방명록에 아직 보지 않은 글이 있으면 사진에 빨간 점을,
 * 「마이페이지」에 「새 방명록 N」을 달고 마이페이지의 방명록(/me#guestbook)으로 바로 보낸다.
 */
export default function ProfileMenu() {
  const { data: me } = useMe()
  const logout = useLogout()
  const alerts = useGuestbookAlerts()
  const newCount = alerts.data?.newEntryCount ?? 0
  const newLabel = newGuestbookLabel(newCount)
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
        aria-label={newCount > 0 ? `프로필 메뉴, 새 방명록 ${newLabel}개` : '프로필 메뉴'}
        className="relative rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
      >
        <Avatar name={me.name} imageUrl={me.profileImageUrl} />
        {newCount > 0 && <span className="absolute -top-0.5 -right-0.5 size-2.5 rounded-full bg-bg-danger ring-2 ring-bg-surface" />}
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-20 mt-2 w-56 overflow-hidden rounded-xl border border-border-default bg-bg-surface shadow-lg">
          <div className="border-b border-border-default px-4 py-3">
            <p className="truncate text-sm font-bold">{me.name}</p>
            <p className="truncate text-xs text-text-tertiary">{me.email}</p>
          </div>
          <Link
            role="menuitem"
            to={newCount > 0 ? '/me#guestbook' : '/me'}
            onClick={close}
            className="flex items-center justify-between gap-2 px-4 py-2.5 text-sm hover:bg-bg-subtle"
          >
            마이페이지
            {newCount > 0 && <Badge tone="danger">새 방명록 {newLabel}</Badge>}
          </Link>
          {me.admin && (
            <Link role="menuitem" to="/admin" onClick={close} className="block px-4 py-2.5 text-sm hover:bg-bg-subtle">
              관리자 콘솔
            </Link>
          )}
          <button
            role="menuitem"
            type="button"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            className="block w-full px-4 py-2.5 text-left text-sm text-text-secondary hover:bg-bg-subtle disabled:opacity-50"
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  )
}
