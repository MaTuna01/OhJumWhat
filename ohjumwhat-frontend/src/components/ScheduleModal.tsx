import { type FormEvent, useRef, useState } from 'react'
import { useNow } from '../hooks/useNow.ts'
import { EVERY_DAY, WEEKDAYS } from '../lib/daysOfWeek.ts'
import { hasDateToken, insertDateToken, renderScheduleName, SCHEDULE_NAME_MAX, scheduleNameLength } from '../lib/scheduleName.ts'
import { kstDayKey } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { type Schedule, useCreateSchedule, useUpdateSchedule } from '../queries/schedules.ts'
import Button from './Button.tsx'
import DayPills from './DayPills.tsx'
import Modal from './Modal.tsx'

type Props = {
  orgId: number
  open: boolean
  /** 있으면 수정, 없으면 추가 */
  schedule: Schedule | null
  onClose: () => void
}

/** Figma 06-M 정기 투표 추가·수정, 06-M2·06-M2E 이름의 오늘 날짜 */
export default function ScheduleModal({ orgId, open, schedule, onClose }: Props) {
  return (
    <Modal open={open} onClose={onClose} title={schedule ? '정기 투표 수정' : '정기 투표 추가'}>
      <ScheduleForm orgId={orgId} schedule={schedule} onClose={onClose} />
    </Modal>
  )
}

function ScheduleForm({ orgId, schedule, onClose }: { orgId: number; schedule: Schedule | null; onClose: () => void }) {
  const [name, setName] = useState(schedule?.name ?? '점심')
  const nameRef = useRef<HTMLInputElement>(null)
  const now = useNow(60_000)
  const [days, setDays] = useState(schedule?.daysOfWeek ?? WEEKDAYS)
  const [openTime, setOpenTime] = useState(schedule?.openTime ?? '11:00')
  const [closeTime, setCloseTime] = useState(schedule?.closeTime ?? '11:50')
  const create = useCreateSchedule(orgId)
  const update = useUpdateSchedule(orgId)
  const mutation = schedule ? update : create

  const timeError = openTime && closeTime && closeTime <= openTime ? '마감 시간은 오픈 시간보다 늦어야 해요.' : null
  // 길이는 투표 제목이 됐을 때(오늘 날짜 = 10자)로 센다. 서버 ScheduleService와 같은 문구
  const nameError = scheduleNameLength(name.trim()) > SCHEDULE_NAME_MAX ? `규칙 이름은 ${SCHEDULE_NAME_MAX}자 이하로 입력해 주세요. 오늘 날짜는 10자로 세요.` : null
  const nameHelp = nameError ?? (hasDateToken(name) ? `예시: ${renderScheduleName(name.trim(), kstDayKey(now))}` : null)
  const valid = name.trim() && !nameError && days > 0 && openTime && closeTime && !timeError

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const body = { name: name.trim(), daysOfWeek: days, openTime, closeTime }
    const options = { onSuccess: onClose }
    if (schedule) update.mutate({ id: schedule.id, ...body }, options)
    else create.mutate(body, options)
  }

  /** 커서 자리(고른 글자가 있으면 그 자리)에 오늘 날짜 토큰을 넣고 커서를 토큰 뒤로 옮긴다 */
  const insertToday = () => {
    const input = nameRef.current
    const next = insertDateToken(name, input?.selectionStart ?? name.length, input?.selectionEnd ?? name.length)
    setName(next.value)
    // 포커스는 누른 순간에 바로 옮긴다(iOS는 그래야 키보드를 띄운다). 커서는 새 값이 그려진 뒤에 맞춘다.
    input?.focus()
    requestAnimationFrame(() => input?.setSelectionRange(next.cursor, next.cursor))
  }

  const quick = (label: string, mask: number) => (
    <button
      type="button"
      onClick={() => setDays(mask)}
      className={`rounded px-1 text-xs font-medium ${days === mask ? 'text-text-brand' : 'text-text-tertiary hover:text-text-secondary'}`}
    >
      {label}
    </button>
  )

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label htmlFor="schedule-name" className="text-sm font-medium">
          이름
        </label>
        <input
          id="schedule-name"
          ref={nameRef}
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={SCHEDULE_NAME_MAX}
          placeholder="예: 점심"
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameHelp ? 'schedule-name-help' : undefined}
          className={`${inputClass} mt-1.5`}
        />
        <button
          type="button"
          onClick={insertToday}
          aria-label="이름에 오늘 날짜 넣기"
          className="mt-2 rounded-full border border-border-default bg-bg-surface px-2.5 py-1 text-xs font-medium text-text-secondary hover:border-border-strong focus-visible:outline-2 focus-visible:outline-border-brand"
        >
          + 오늘 날짜
        </button>
        {nameHelp && (
          <p id="schedule-name-help" role={nameError ? 'alert' : undefined} className={`mt-1.5 text-xs ${nameError ? 'text-text-danger' : 'text-text-tertiary'}`}>
            {nameHelp}
          </p>
        )}
      </div>
      <div>
        <div className="flex items-center gap-2">
          <span className="flex-1 text-sm font-medium">요일</span>
          {quick('평일', WEEKDAYS)}
          {quick('매일', EVERY_DAY)}
        </div>
        <div className="mt-2">
          <DayPills mask={days} onChange={setDays} size="lg" />
        </div>
        {days === 0 && <p className="mt-1.5 text-xs text-text-danger">요일을 하나 이상 골라 주세요.</p>}
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div>
          <label htmlFor="schedule-open" className="text-sm font-medium">
            오픈 시간
          </label>
          <input id="schedule-open" type="time" value={openTime} onChange={(e) => setOpenTime(e.target.value)} className={`${inputClass} mt-1.5`} />
        </div>
        <div>
          <label htmlFor="schedule-close" className="text-sm font-medium">
            마감 시간
          </label>
          <input id="schedule-close" type="time" value={closeTime} onChange={(e) => setCloseTime(e.target.value)} className={`${inputClass} mt-1.5`} />
        </div>
      </div>
      <p className={`text-xs ${timeError ? 'text-text-danger' : 'text-text-tertiary'}`}>
        {timeError ?? '한국 시간 기준이에요. 오픈 시간이 되면 투표가 자동으로 열려요.'}
      </p>
      {mutation.error && (
        <p role="alert" className="text-sm text-text-danger">
          {mutation.error.message}
        </p>
      )}
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button type="submit" disabled={!valid || mutation.isPending}>
          저장
        </Button>
      </div>
    </form>
  )
}
