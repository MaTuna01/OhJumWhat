import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router'
import { defaultCloseTime, formatClock } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { useCreatePoll } from '../queries/polls.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

export default function CreatePollModal({ orgId, open, onClose }: { orgId: number; open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} onClose={onClose} title="투표 만들기">
      <CreatePollForm orgId={orgId} onClose={onClose} />
    </Modal>
  )
}

function CreatePollForm({ orgId, onClose }: { orgId: number; onClose: () => void }) {
  const [title, setTitle] = useState('점심')
  const [closesAt, setClosesAt] = useState(() => defaultCloseTime(Date.now()))
  const create = useCreatePoll(orgId)
  const navigate = useNavigate()

  const submit = (e: FormEvent) => {
    e.preventDefault()
    create.mutate(
      { title: title.trim(), closesAt },
      {
        onSuccess: (poll) => {
          onClose()
          navigate(`/orgs/${orgId}/polls/${poll.id}`)
        },
      },
    )
  }

  const preview = /^\d{2}:\d{2}$/.test(closesAt) ? formatClock(`2026-01-01T${closesAt}:00+09:00`) : null

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label htmlFor="poll-title" className="text-sm font-medium">
          제목
        </label>
        <input id="poll-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} className={`${inputClass} mt-1.5`} />
      </div>
      <div>
        <label htmlFor="poll-closes-at" className="text-sm font-medium">
          마감 시간
        </label>
        <input
          id="poll-closes-at"
          type="time"
          value={closesAt}
          onChange={(e) => setClosesAt(e.target.value)}
          className={`${inputClass} mt-1.5`}
        />
        {preview && <p className="mt-1.5 text-xs text-text-tertiary">오늘 {preview}에 마감돼요. 만들면 바로 열려요.</p>}
      </div>
      {create.error && (
        <p role="alert" className="text-sm text-text-danger">
          {create.error.message}
        </p>
      )}
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button type="submit" disabled={!title.trim() || !closesAt || create.isPending}>
          만들기
        </Button>
      </div>
    </form>
  )
}
