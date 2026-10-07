import { withJosa } from '../lib/josa.ts'
import {
  contentText,
  periodText,
  reasonText,
  restrictionLabels,
  type SanctionRow as SanctionRowData,
  type SanctionStatus,
  STATUS_LABELS,
} from '../lib/sanctions.ts'
import { useLiftSanction } from '../queries/sanctions.ts'
import Badge, { type BadgeTone } from './Badge.tsx'
import Button from './Button.tsx'
import ConfirmDialog from './ConfirmDialog.tsx'

const STATUS_TONES: Record<SanctionStatus, BadgeTone> = {
  ACTIVE: 'danger',
  EXPIRED: 'neutral',
  LIFTED: 'neutral',
  WARNING: 'warning',
  RESET_ONLY: 'neutral',
}

type Props = {
  row: SanctionRowData
  /** 진행 중인 제재의 「해제」(없으면 버튼을 숨긴다) */
  onLift?: () => void
}

/**
 * Figma SanctionRow(관리자 회원 상세 A03b·「제재」 탭 A10): 상태 배지와 내용, 기간(해제했으면 언제 누가),
 * 「사유 — 관리자 설명」, 건 관리자. 진행 중이면 「해제」를 둔다.
 */
export default function SanctionRow({ row, onLift }: Props) {
  return (
    <div className="space-y-1 rounded-xl border border-border-default bg-bg-surface p-3.5">
      <div className="flex items-start gap-2">
        <span className="pt-0.5">
          <Badge tone={STATUS_TONES[row.status]}>{STATUS_LABELS[row.status]}</Badge>
        </span>
        <p className="min-w-0 flex-1 text-sm font-bold break-words">{contentText(row.restrictions, row.resets)}</p>
        {row.status === 'ACTIVE' && onLift && (
          <Button variant="secondary" onClick={onLift} className="shrink-0 py-1.5">
            해제
          </Button>
        )}
      </div>
      <p className="text-xs text-text-secondary">{periodText(row)}</p>
      <p className="text-xs break-words whitespace-pre-wrap text-text-secondary">{reasonText(row.reason, row.note)}</p>
      <p className="text-xs text-text-tertiary">{row.createdByName ?? '관리자'}</p>
    </div>
  )
}

/** 제재 해제 확인(Figma A03-M6). 함께 한 프로필 초기화는 되돌리지 않고 본인에게 알리지 않는다. */
export function LiftSanctionDialog({ row, onClose }: { row: SanctionRowData | null; onClose: () => void }) {
  const lift = useLiftSanction()
  const close = () => {
    lift.reset()
    onClose()
  }

  return (
    <ConfirmDialog
      open={row !== null}
      onClose={close}
      onConfirm={() => row && lift.mutate(row.id, { onSuccess: close })}
      title="제재를 해제할까요?"
      confirmLabel="해제"
      pending={lift.isPending}
      error={lift.error?.message}
    >
      <p>
        {row && withJosa(restrictionLabels(row.restrictions), '을/를')} 지금 풀어요. 함께 한 프로필 초기화는 되돌리지 않아요. 본인에게 따로 알리지
        않아요.
      </p>
    </ConfirmDialog>
  )
}
