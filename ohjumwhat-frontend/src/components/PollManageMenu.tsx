import { useCallback, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useDismiss } from '../hooks/useDismiss.ts'
import { toKstHhmm } from '../lib/time.ts'
import { type PollDetail, useClosePoll, useDeletePoll, useUpdatePoll } from '../queries/polls.ts'
import ConfirmDialog from './ConfirmDialog.tsx'
import Modal from './Modal.tsx'
import PollForm from './PollForm.tsx'

type Dialog = 'edit' | 'close' | 'delete' | null

/**
 * 진행 중인 투표의 ⋯ 관리 메뉴(Figma 05-A·D05-A): 수정(05-M1), 지금 마감(05-M2), 삭제(05-M3).
 * 조직 멤버 누구나 쓸 수 있다. 정기 투표로 열린 투표는 지워도 스케줄러가 다시 열기 때문에 삭제를 막는다.
 */
export default function PollManageMenu({ orgId, poll }: { orgId: number; poll: PollDetail }) {
  const [open, setOpen] = useState(false)
  const [dialog, setDialog] = useState<Dialog>(null)
  const ref = useRef<HTMLDivElement>(null)
  const closeMenu = useCallback(() => setOpen(false), [])
  useDismiss(ref, open, closeMenu)

  const show = (next: Dialog) => {
    setOpen(false)
    setDialog(next)
  }

  return (
    <div ref={ref} className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="투표 관리"
        className="flex size-8 items-center justify-center rounded-lg border border-border-strong bg-bg-surface text-text-secondary hover:bg-bg-subtle focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand aria-expanded:bg-bg-subtle"
      >
        <span aria-hidden className="text-base leading-none font-bold">
          ⋯
        </span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-20 mt-2 w-56 overflow-hidden rounded-xl border border-border-default bg-bg-surface shadow-lg">
          <MenuItem onClick={() => show('edit')}>제목·마감 시간 수정</MenuItem>
          <MenuItem onClick={() => show('close')}>지금 마감하기</MenuItem>
          <div className="border-t border-border-default">
            <MenuItem
              onClick={() => show('delete')}
              disabled={poll.scheduled}
              className="text-text-danger disabled:text-text-placeholder"
              caption={poll.scheduled ? '정기 투표는 삭제 대신 마감해 주세요' : undefined}
            >
              투표 삭제
            </MenuItem>
          </div>
        </div>
      )}

      <EditPollModal orgId={orgId} poll={poll} open={dialog === 'edit'} onClose={() => setDialog(null)} />
      <ClosePollDialog orgId={orgId} poll={poll} open={dialog === 'close'} onClose={() => setDialog(null)} />
      <DeletePollDialog orgId={orgId} poll={poll} open={dialog === 'delete'} onClose={() => setDialog(null)} />
    </div>
  )
}

function MenuItem({ onClick, disabled, className = '', caption, children }: { onClick: () => void; disabled?: boolean; className?: string; caption?: string; children: string }) {
  return (
    <button
      role="menuitem"
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`block w-full px-4 py-2.5 text-left text-sm hover:bg-bg-subtle disabled:cursor-not-allowed disabled:hover:bg-transparent ${className}`}
    >
      {children}
      {caption && <span className="mt-0.5 block text-xs text-text-tertiary">{caption}</span>}
    </button>
  )
}

type DialogProps = { orgId: number; poll: PollDetail; open: boolean; onClose: () => void }

function EditPollModal({ orgId, poll, open, onClose }: DialogProps) {
  const update = useUpdatePoll(orgId, poll.id)
  const close = () => {
    update.reset()
    onClose()
  }

  return (
    <Modal open={open} onClose={close} title="투표 수정">
      <PollForm
        initial={{ title: poll.title, closesAt: toKstHhmm(new Date(poll.closesAt)) }}
        submitLabel="저장"
        pending={update.isPending}
        error={update.error?.message}
        onCancel={close}
        onSubmit={(values) => update.mutate(values, { onSuccess: close })}
      />
    </Modal>
  )
}

function ClosePollDialog({ orgId, poll, open, onClose }: DialogProps) {
  const closePoll = useClosePoll(orgId, poll.id)
  const close = () => {
    closePoll.reset()
    onClose()
  }
  const waiting = poll.nonRespondents.length

  return (
    <ConfirmDialog
      open={open}
      onClose={close}
      onConfirm={() => closePoll.mutate(undefined, { onSuccess: close })}
      title="지금 마감할까요?"
      confirmLabel="지금 마감"
      pending={closePoll.isPending}
      error={closePoll.error?.message}
    >
      <p>마감하면 메뉴를 추가하거나 바꿀 수 없고, 지금까지 참여한 사람들로 팀이 확정돼요.</p>
      {waiting > 0 && (
        <p className="mt-3 rounded-lg bg-bg-muted px-3 py-2.5 text-xs font-medium text-text-secondary">
          아직 응답하지 않은 {waiting}명은 미응답으로 남아요.
        </p>
      )}
    </ConfirmDialog>
  )
}

function DeletePollDialog({ orgId, poll, open, onClose }: DialogProps) {
  const deletePoll = useDeletePoll(orgId, poll.id)
  const navigate = useNavigate()
  const close = () => {
    deletePoll.reset()
    onClose()
  }

  return (
    <ConfirmDialog
      open={open}
      onClose={close}
      onConfirm={() => deletePoll.mutate(undefined, { onSuccess: () => navigate(`/orgs/${orgId}`, { replace: true }) })}
      title="투표를 삭제할까요?"
      confirmLabel="삭제"
      danger
      pending={deletePoll.isPending}
      error={deletePoll.error?.message}
    >
      <p>
        <strong className="font-bold text-text-primary">{poll.title}</strong>에 올라온 메뉴와 모두의 응답이 함께 삭제돼요.
      </p>
      <p className="mt-3 rounded-lg bg-bg-danger-soft px-3 py-2.5 text-xs font-medium text-text-danger">삭제한 투표는 되돌릴 수 없어요.</p>
    </ConfirmDialog>
  )
}
