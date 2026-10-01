import { type FormEvent, useState } from 'react'
import { inputClass } from '../lib/ui.ts'
import { type PollOption, useChangeLink } from '../queries/polls.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = { orgId: number; pollId: number; option: PollOption | null; onClose: () => void }

/** 식당 지도 링크 달기·고치기·지우기(Figma 05-M4). 메뉴를 추가한 사람만, 투표가 진행 중일 때 연다. */
export default function MapLinkModal({ orgId, pollId, option, onClose }: Props) {
  const changeLink = useChangeLink(orgId, pollId)
  const close = () => {
    changeLink.reset()
    onClose()
  }

  return (
    <Modal open={option != null} onClose={close} title={`${option?.name ?? ''} 지도 링크`}>
      {option && (
        <MapLinkForm
          initial={option.link ?? ''}
          hasLink={option.link != null}
          pending={changeLink.isPending}
          error={changeLink.error?.message}
          onCancel={close}
          onSave={(link) => changeLink.mutate({ optionId: option.id, link }, { onSuccess: close })}
        />
      )}
    </Modal>
  )
}

type FormProps = {
  initial: string
  hasLink: boolean
  pending: boolean
  error: string | undefined
  onCancel: () => void
  onSave: (link: string | null) => void
}

function MapLinkForm({ initial, hasLink, pending, error, onCancel, onSave }: FormProps) {
  const [link, setLink] = useState(initial)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSave(link.trim() || null)
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label htmlFor="menu-link" className="text-sm font-medium">
          링크
        </label>
        <input
          id="menu-link"
          value={link}
          onChange={(e) => setLink(e.target.value)}
          maxLength={1000}
          placeholder="지도 앱의 공유 링크를 붙여 넣으세요"
          className={`${inputClass} mt-1.5`}
        />
        <p className="mt-1.5 text-xs text-text-tertiary">네이버·카카오·구글 지도 앱의 공유 문구를 그대로 붙여도 돼요.</p>
      </div>
      {error && (
        <p role="alert" className="text-sm text-text-danger">
          {error}
        </p>
      )}
      <div className="flex items-center justify-between gap-2 pt-2">
        {hasLink ? (
          <button type="button" onClick={() => onSave(null)} disabled={pending} className="text-sm font-medium text-text-danger hover:underline disabled:opacity-50">
            링크 지우기
          </button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onCancel}>
            취소
          </Button>
          <Button type="submit" disabled={pending || (!link.trim() && !hasLink)}>
            저장
          </Button>
        </div>
      </div>
    </form>
  )
}
