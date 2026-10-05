import { useCallback, useRef, useState } from 'react'
import { Link, useMatch } from 'react-router'
import { useDismiss } from '../hooks/useDismiss.ts'
import { useMyOrganizations } from '../queries/orgs.ts'

/** 상단 바의 조직 전환 드롭다운 */
export default function OrgSwitcher() {
  const match = useMatch('/orgs/:orgId/*')
  const currentId = match ? Number(match.params.orgId) : null
  const { data: orgs } = useMyOrganizations()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const close = useCallback(() => setOpen(false), [])
  useDismiss(ref, open, close)

  if (!orgs || orgs.length === 0) return null
  const current = orgs.find((o) => o.id === currentId)

  return (
    <div ref={ref} className="relative flex min-w-0 items-center gap-2">
      <span className="text-border-strong" aria-hidden>
        /
      </span>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`조직 전환: ${current?.name ?? '선택 안 됨'}`}
        className="flex max-w-48 min-w-0 items-center gap-1 rounded-lg px-2 py-1.5 text-sm font-medium text-text-secondary hover:bg-bg-muted focus-visible:outline-2 focus-visible:outline-border-brand"
      >
        <span className="truncate">{current?.name ?? '조직 선택'}</span>
        <svg viewBox="0 0 20 20" className="size-4 shrink-0 text-text-placeholder" aria-hidden>
          <path fill="currentColor" d="M5.3 7.3a1 1 0 0 1 1.4 0L10 10.6l3.3-3.3a1 1 0 1 1 1.4 1.4l-4 4a1 1 0 0 1-1.4 0l-4-4a1 1 0 0 1 0-1.4z" />
        </svg>
      </button>
      {open && (
        <div role="menu" className="absolute top-full left-0 z-20 mt-2 w-64 overflow-hidden rounded-xl border border-border-default bg-bg-surface shadow-lg">
          <ul className="max-h-72 overflow-y-auto py-1">
            {orgs.map((org) => (
              <li key={org.id}>
                <Link
                  role="menuitem"
                  to={`/orgs/${org.id}`}
                  onClick={close}
                  aria-current={org.id === currentId ? 'page' : undefined}
                  className="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-bg-subtle aria-[current=page]:font-bold aria-[current=page]:text-text-brand"
                >
                  <span className="truncate">{org.name}</span>
                  {org.hasOpenPollToday && (
                    <span className="size-2 shrink-0 rounded-full bg-bg-brand" aria-label="진행 중인 투표 있음" />
                  )}
                </Link>
              </li>
            ))}
          </ul>
          <Link role="menuitem" to="/me" onClick={close} className="block border-t border-border-default px-4 py-2.5 text-sm text-text-tertiary hover:bg-bg-subtle">
            조직 관리
          </Link>
        </div>
      )}
    </div>
  )
}
