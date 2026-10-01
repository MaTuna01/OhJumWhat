import { type FormEvent, useState } from 'react'
import { inputClass } from '../lib/ui.ts'
import { type Me, useChangeNickname } from '../queries/me.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

const MAX_LENGTH = 20

/** 이름(별명) 바꾸기(Figma 03-M2). 비워서 저장하거나 「구글 이름으로 되돌리기」를 누르면 구글 이름을 쓴다. */
export default function NicknameModal({ me, open, onClose }: { me: Me; open: boolean; onClose: () => void }) {
  const change = useChangeNickname()
  const close = () => {
    change.reset()
    onClose()
  }

  return (
    <Modal open={open} onClose={close} title="이름 바꾸기">
      <NicknameForm
        initial={me.name}
        hasNickname={me.nickname != null}
        googleName={me.googleName}
        pending={change.isPending}
        error={change.error?.message}
        onCancel={close}
        onSave={(nickname) => change.mutate(nickname, { onSuccess: close })}
      />
    </Modal>
  )
}

type FormProps = {
  initial: string
  hasNickname: boolean
  googleName: string
  pending: boolean
  error: string | undefined
  onCancel: () => void
  onSave: (nickname: string | null) => void
}

function NicknameForm({ initial, hasNickname, googleName, pending, error, onCancel, onSave }: FormProps) {
  const [value, setValue] = useState(initial)
  const length = [...value.trim()].length

  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSave(value.trim() || null)
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label htmlFor="nickname" className="text-sm font-medium">
          이름
        </label>
        <input
          id="nickname"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={googleName}
          className={`${inputClass} mt-1.5`}
        />
        <p className={`mt-1.5 text-xs ${length > MAX_LENGTH ? 'text-text-danger' : 'text-text-tertiary'}`}>
          투표와 멤버 목록에 이 이름으로 보여요. {MAX_LENGTH}자까지 쓸 수 있어요.{length > MAX_LENGTH && ` (지금 ${length}자)`}
        </p>
      </div>
      {error && (
        <p role="alert" className="text-sm text-text-danger">
          {error}
        </p>
      )}
      <div className="flex items-center justify-between gap-2 pt-2">
        {hasNickname ? (
          <button type="button" onClick={() => onSave(null)} disabled={pending} className="text-sm font-medium text-text-secondary hover:underline disabled:opacity-50">
            구글 이름으로 되돌리기
          </button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onCancel}>
            취소
          </Button>
          <Button type="submit" disabled={pending || length > MAX_LENGTH || value.trim() === initial}>
            저장
          </Button>
        </div>
      </div>
    </form>
  )
}
