import { type FormEvent, useState } from 'react'
import { formatHhmm } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import Button from './Button.tsx'

export type PollFormValues = { title: string; closesAt: string }

type Props = {
  initial: PollFormValues
  submitLabel: string
  /** 마감 시각 안내 뒤에 붙는 문장(예: "만들면 바로 열려요.") */
  hint?: string
  pending: boolean
  error: string | undefined
  onSubmit: (values: PollFormValues) => void
  onCancel: () => void
}

/** 투표 만들기(Figma 04-M)·수정(05-M1) 모달의 제목·마감 시간 입력 */
export default function PollForm({ initial, submitLabel, hint, pending, error, onSubmit, onCancel }: Props) {
  const [title, setTitle] = useState(initial.title)
  const [closesAt, setClosesAt] = useState(initial.closesAt)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit({ title: title.trim(), closesAt })
  }

  const preview = /^\d{2}:\d{2}$/.test(closesAt) ? formatHhmm(closesAt) : null

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
        {preview && (
          <p className="mt-1.5 text-xs text-text-tertiary">
            오늘 {preview}에 마감돼요.{hint && ` ${hint}`}
          </p>
        )}
      </div>
      {error && (
        <p role="alert" className="text-sm text-text-danger">
          {error}
        </p>
      )}
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancel}>
          취소
        </Button>
        <Button type="submit" disabled={!title.trim() || !closesAt || pending}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
