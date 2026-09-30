import { useState } from 'react'
import Button from '../components/Button.tsx'
import ConfirmDialog from '../components/ConfirmDialog.tsx'
import DayPills from '../components/DayPills.tsx'
import { PageLoader } from '../components/PageState.tsx'
import ScheduleModal from '../components/ScheduleModal.tsx'
import { useOrgId } from '../hooks/useOrgId.ts'
import { daysLabel } from '../lib/daysOfWeek.ts'
import { formatTimeRange } from '../lib/time.ts'
import { type Schedule, useDeleteSchedule, useSchedules } from '../queries/schedules.ts'

/** Figma 06 정기 투표 관리. 모든 멤버가 규칙을 추가·수정·삭제할 수 있다. */
export default function SchedulesPage() {
  const orgId = useOrgId()
  const schedules = useSchedules(orgId)
  const remove = useDeleteSchedule(orgId)
  // undefined: 닫힘, null: 새 규칙, Schedule: 수정
  const [editing, setEditing] = useState<Schedule | null | undefined>(undefined)
  const [deleting, setDeleting] = useState<Schedule | null>(null)

  const closeDelete = () => {
    remove.reset()
    setDeleting(null)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-bold">정기 투표 규칙</h2>
        <Button onClick={() => setEditing(null)}>+ 규칙 추가</Button>
      </div>

      {schedules.isPending ? (
        <PageLoader />
      ) : schedules.isError ? (
        <p className="text-sm text-text-danger">{schedules.error.message}</p>
      ) : schedules.data.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border-strong bg-bg-surface px-4 py-10 text-center">
          <p className="font-medium">정기 투표가 없어요</p>
          <p className="mt-1 text-sm text-text-tertiary">예: 점심 · 평일 오전 11:00 ~ 11:50 규칙을 만들면 매일 자동으로 투표가 열려요.</p>
        </div>
      ) : (
        <ul className="space-y-3">
          {schedules.data.map((schedule) => (
            <li key={schedule.id} className="space-y-3 rounded-2xl border border-border-default bg-bg-surface p-4">
              <div className="flex items-center gap-1">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold">{schedule.name}</p>
                  <p className="text-sm text-text-secondary">
                    {daysLabel(schedule.daysOfWeek)} · {formatTimeRange(schedule.openTime, schedule.closeTime)}
                  </p>
                </div>
                <Button variant="ghost" className="py-1.5" onClick={() => setEditing(schedule)}>
                  수정
                </Button>
                <Button variant="ghost" className="py-1.5" onClick={() => setDeleting(schedule)}>
                  삭제
                </Button>
              </div>
              <DayPills mask={schedule.daysOfWeek} />
            </li>
          ))}
        </ul>
      )}

      <ul className="space-y-1 rounded-xl bg-bg-muted px-3.5 py-3 text-xs text-text-secondary">
        <li>· 규칙에 맞춰 오픈 시간에 투표가 자동으로 열려요.</li>
        <li>· 아침·점심·저녁은 규칙을 여러 개 만들면 돼요.</li>
        <li>· 규칙을 고치거나 지워도 이미 열린 투표는 그대로 남아요.</li>
      </ul>

      <ScheduleModal orgId={orgId} open={editing !== undefined} schedule={editing ?? null} onClose={() => setEditing(undefined)} />
      <ConfirmDialog
        open={deleting != null}
        onClose={closeDelete}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSuccess: closeDelete })}
        title={`${deleting?.name ?? ''} 규칙을 삭제할까요?`}
        confirmLabel="삭제"
        danger
        pending={remove.isPending}
        error={remove.error?.message}
      >
        <p>앞으로 이 규칙으로는 투표가 열리지 않아요. 이미 열린 투표는 그대로 남아요.</p>
      </ConfirmDialog>
    </div>
  )
}
