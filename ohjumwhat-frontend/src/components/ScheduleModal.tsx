import { type FormEvent, useState } from 'react'
import { EVERY_DAY, WEEKDAYS } from '../lib/daysOfWeek.ts'
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

/** Figma 06-M 정기 투표 추가·수정 */
export default function ScheduleModal({ orgId, open, schedule, onClose }: Props) {
  return (
    <Modal open={open} onClose={onClose} title={schedule ? '정기 투표 수정' : '정기 투표 추가'}>
      <ScheduleForm orgId={orgId} schedule={schedule} onClose={onClose} />
    </Modal>
  )
}

function ScheduleForm({ orgId, schedule, onClose }: { orgId: number; schedule: Schedule | null; onClose: () => void }) {
  const [name, setName] = useState(schedule?.name ?? '점심')
  const [days, setDays] = useState(schedule?.daysOfWeek ?? WEEKDAYS)
  const [openTime, setOpenTime] = useState(schedule?.openTime ?? '11:00')
  const [closeTime, setCloseTime] = useState(schedule?.closeTime ?? '11:50')
  const create = useCreateSchedule(orgId)
  const update = useUpdateSchedule(orgId)
  const mutation = schedule ? update : create

  const timeError = openTime && closeTime && closeTime <= openTime ? '마감 시간은 오픈 시간보다 늦어야 해요.' : null
  const valid = name.trim() && days > 0 && openTime && closeTime && !timeError

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const body = { name: name.trim(), daysOfWeek: days, openTime, closeTime }
    const options = { onSuccess: onClose }
    if (schedule) update.mutate({ id: schedule.id, ...body }, options)
    else create.mutate(body, options)
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
        <input id="schedule-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={50} placeholder="예: 점심" className={`${inputClass} mt-1.5`} />
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
