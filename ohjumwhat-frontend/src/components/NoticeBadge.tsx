import type { NoticeKind } from '../queries/notices.ts'
import Badge from './Badge.tsx'

/** 새 소식 종류 배지: 「업데이트 v1.5.0」(brand) / 「개발자 노트」(neutral). short면 모바일에서 버전을 뺀다. */
export default function NoticeBadge({ kind, version, short = false }: { kind: NoticeKind; version: string | null; short?: boolean }) {
  if (kind === 'NOTE') return <Badge tone="neutral">개발자 노트</Badge>
  return (
    <Badge tone="brand">
      업데이트
      {version && <span className={short ? 'hidden lg:inline' : undefined}>&nbsp;v{version}</span>}
    </Badge>
  )
}
