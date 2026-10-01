import { type FormEvent, useState } from 'react'
import { inputClass } from '../lib/ui.ts'
import { type NoticeInput, useCreateNotice, useUpdateNotice } from '../queries/admin.ts'
import type { Notice } from '../queries/notices.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'
import NoticeBody from './NoticeBody.tsx'

// 서버(NoticeRequest)와 같은 한도. 서버처럼 UTF-16 길이로 센다.
const TITLE_MAX = 100
const BODY_MAX = 5000

/** 관리자 콘솔 개발자 노트 쓰기·고치기(Figma A08-M). notice가 있으면 고치기다. 고쳐도 다시 알리지 않는다. */
export default function NoticeFormModal({ notice, open, onClose }: { notice: Notice | null; open: boolean; onClose: () => void }) {
  const create = useCreateNotice()
  const update = useUpdateNotice()
  const mutation = notice ? update : create
  const close = () => {
    create.reset()
    update.reset()
    onClose()
  }

  return (
    <Modal open={open} onClose={close} title={notice ? '새 소식 고치기' : '새 소식 쓰기'}>
      <NoticeForm
        initial={notice}
        submitLabel={notice ? '저장' : '올리기'}
        pending={mutation.isPending}
        error={mutation.error?.message}
        onCancel={close}
        onSubmit={(input) =>
          notice ? update.mutate({ id: notice.id, ...input }, { onSuccess: close }) : create.mutate(input, { onSuccess: close })
        }
      />
    </Modal>
  )
}

type FormProps = {
  initial: Notice | null
  submitLabel: string
  pending: boolean
  error: string | undefined
  onCancel: () => void
  onSubmit: (input: NoticeInput) => void
}

function NoticeForm({ initial, submitLabel, pending, error, onCancel, onSubmit }: FormProps) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [body, setBody] = useState(initial?.body ?? '')
  const [preview, setPreview] = useState(false)
  const input = { title: title.trim(), body: body.trim() }
  const invalid = input.title === '' || input.body === '' || input.title.length > TITLE_MAX || input.body.length > BODY_MAX
  const unchanged = initial != null && input.title === initial.title && input.body === initial.body

  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit(input)
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label htmlFor="notice-title" className="text-sm font-medium">
          제목
        </label>
        <input
          id="notice-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="예: 점검 안내"
          className={`${inputClass} mt-1.5`}
        />
        <Counter length={input.title.length} max={TITLE_MAX} />
      </div>
      <div>
        <div className="flex items-center justify-between gap-2">
          <label htmlFor="notice-body" className="text-sm font-medium">
            내용
          </label>
          <button
            type="button"
            onClick={() => setPreview((v) => !v)}
            aria-pressed={preview}
            className="text-xs font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
          >
            {preview ? '다시 쓰기' : '미리보기'}
          </button>
        </div>
        {preview ? (
          <div className="mt-1.5 max-h-72 min-h-40 overflow-y-auto rounded-lg border border-border-default bg-bg-subtle px-3 py-2">
            {input.body ? <NoticeBody body={input.body} /> : <p className="text-sm text-text-placeholder">내용을 쓰면 여기에 보여요.</p>}
          </div>
        ) : (
          <textarea
            id="notice-body"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={7}
            placeholder={'빈 줄로 문단을 나누고, "- "로 시작하면 목록이 돼요. 주소는 링크가 돼요.'}
            className={`${inputClass} mt-1.5 resize-y leading-relaxed`}
          />
        )}
        <Counter length={input.body.length} max={BODY_MAX} />
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
        <Button type="submit" disabled={pending || invalid || unchanged}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}

function Counter({ length, max }: { length: number; max: number }) {
  return (
    <p className={`mt-1.5 text-right text-xs ${length > max ? 'text-text-danger' : 'text-text-tertiary'}`}>
      {length.toLocaleString('ko-KR')}/{max.toLocaleString('ko-KR')}
    </p>
  )
}
