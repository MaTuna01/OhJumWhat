import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { inputClass } from '../lib/ui.ts'
import Avatar from './Avatar.tsx'

// 관리자 콘솔 공통 부품. Figma 「관리자 콘솔」 페이지의 StatCard·ListRow 컴포넌트와 같다.

/** Figma StatCard: 개요의 숫자 카드 */
export function StatCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="flex flex-col gap-1 rounded-2xl border border-border-default bg-bg-surface p-4">
      <p className="text-xs text-text-tertiary">{label}</p>
      <p className="text-2xl font-bold tracking-tight">{value}</p>
    </div>
  )
}

/** 목록 카드: 안의 행 사이에 구분선을 긋는다. */
export function ListCard({ children }: { children: ReactNode }) {
  return <ul className="divide-y divide-border-default rounded-2xl border border-border-default bg-bg-surface px-5 py-1">{children}</ul>
}

/** 목록이 비었을 때 카드 안 안내 */
export function EmptyRow({ children }: { children: string }) {
  return <li className="py-8 text-center text-sm text-text-tertiary">{children}</li>
}

type Person = { name: string; imageUrl?: string | null }

/** Figma ListRow: 누르면 상세로 가는 한 줄(회원·조직·투표) */
export function ListRow({ to, person, title, badge, subtitle, meta }: { to: string; person?: Person; title: string; badge?: ReactNode; subtitle: ReactNode; meta?: ReactNode }) {
  return (
    <li>
      <Link to={to} className="group flex items-center gap-3 py-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand">
        {person && <Avatar name={person.name} imageUrl={person.imageUrl} />}
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-1.5">
            <span className="truncate font-medium group-hover:text-text-brand">{title}</span>
            {badge}
          </p>
          <p className="truncate text-sm text-text-tertiary">{subtitle}</p>
        </div>
        {meta && <span className="shrink-0 text-right text-xs text-text-tertiary">{meta}</span>}
        <span aria-hidden className="text-icon-muted">
          ›
        </span>
      </Link>
    </li>
  )
}

/** 오른쪽에 버튼(삭제·내보내기·차단 해제)이 있는 한 줄 */
export function ActionRow({ person, title, subtitle, action }: { person?: Person; title: ReactNode; subtitle: ReactNode; action: ReactNode }) {
  return (
    <li className="flex items-center gap-3 py-3">
      {person && <Avatar name={person.name} imageUrl={person.imageUrl} />}
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium">{title}</p>
        <p className="truncate text-sm text-text-tertiary">{subtitle}</p>
      </div>
      {action}
    </li>
  )
}

/** 「위험 구역」 카드: 되돌릴 수 없는 삭제·강제 탈퇴 */
export function DangerZone({ title, description, children }: { title: string; description: ReactNode; children: ReactNode }) {
  return (
    <section className="space-y-3 rounded-2xl border border-border-default bg-bg-surface p-5">
      <h2 className="font-bold text-text-danger">{title}</h2>
      <div className="text-sm text-text-tertiary">{description}</div>
      {children}
    </section>
  )
}

/** 목록 위의 검색창과 건수 */
export function AdminSearch({ value, onChange, placeholder, summary }: { value: string; onChange: (value: string) => void; placeholder: string; summary: ReactNode }) {
  return (
    <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:gap-4">
      <input type="search" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} aria-label={placeholder} className={`${inputClass} lg:max-w-[25rem]`} />
      <p className="text-xs font-medium text-text-tertiary">{summary}</p>
    </div>
  )
}
