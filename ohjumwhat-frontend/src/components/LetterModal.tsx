import { useEffect, useRef, useState } from 'react'
import { useLetterComposer } from '../hooks/useLetterComposer.ts'
import { useNow } from '../hooks/useNow.ts'
import { ApiError } from '../lib/api.ts'
import { counterpartLabel, organizationLabel, REPORT_REASON_MAX, letterLength } from '../lib/letters.ts'
import { formatClock, formatDayTime } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { type Letter, useBlockLetterSender, useDeleteLetter, useMarkLetterRead, useReportLetter } from '../queries/letters.ts'
import AnonymousAvatar from './AnonymousAvatar.tsx'
import Avatar from './Avatar.tsx'
import Badge from './Badge.tsx'
import Button from './Button.tsx'
import ConfirmDialog from './ConfirmDialog.tsx'
import MessageBody from './MessageBody.tsx'
import Modal from './Modal.tsx'

type Dialog = 'delete' | 'block' | 'report' | null

/**
 * 쪽지 보기(Figma 10-M2 받은 쪽지, 10-M2b 익명 쪽지, 10-M2c 보낸 쪽지). 받은 쪽지는 열 때 읽음으로 바꾼다.
 * 확인 창(지우기·차단 10-M5·신고 10-M3)은 이 모달 밖에 그린다(<dialog> 안의 <dialog>는 닫힘 이벤트가 바깥으로 올라온다).
 */
export default function LetterModal({ letter, onClose }: { letter: Letter | null; onClose: () => void }) {
  const [dialog, setDialog] = useState<Dialog>(null)
  const { compose, notify } = useLetterComposer()
  const markRead = useMarkLetterRead()
  const remove = useDeleteLetter()
  const block = useBlockLetterSender()
  const markedId = useRef<number | null>(null)

  // 받은 쪽지를 처음 열면 한 번만 읽음으로 바꾼다.
  useEffect(() => {
    if (letter && letter.box === 'RECEIVED' && !letter.readAt && markedId.current !== letter.id) {
      markedId.current = letter.id
      markRead.mutate(letter.id)
    }
  }, [letter, markRead])

  const closeAll = () => {
    setDialog(null)
    onClose()
  }

  return (
    <>
      <Modal open={letter !== null} onClose={onClose} title={letter?.box === 'SENT' ? '보낸 쪽지' : '받은 쪽지'} closable>
        {letter && (
          <LetterView
            letter={letter}
            onReply={() => {
              onClose()
              compose({ kind: 'reply', letter })
            }}
            onDialog={setDialog}
          />
        )}
      </Modal>
      <ConfirmDialog
        open={dialog === 'delete'}
        onClose={() => setDialog(null)}
        title="쪽지를 지울까요?"
        confirmLabel="지우기"
        danger
        pending={remove.isPending}
        error={remove.error instanceof ApiError ? remove.error.message : remove.error ? '지우지 못했어요.' : null}
        onConfirm={() => letter && remove.mutate(letter.id, { onSuccess: closeAll })}
      >
        내 쪽지함에서만 지워져요. 상대 쪽지함에는 남아요.
      </ConfirmDialog>
      <ConfirmDialog
        open={dialog === 'block'}
        onClose={() => setDialog(null)}
        title="이 사람을 차단할까요?"
        confirmLabel="차단"
        danger
        pending={block.isPending}
        error={block.error instanceof ApiError ? block.error.message : block.error ? '차단하지 못했어요.' : null}
        onConfirm={() =>
          letter &&
          block.mutate(letter.id, {
            onSuccess: () => {
              closeAll()
              notify('차단했어요')
            },
          })
        }
      >
        <p>차단하면 이 사람의 쪽지를 더는 받지 않아요. 지금까지 받은 쪽지도 목록에서 숨겨져요.</p>
        <p className="mt-3 rounded-xl bg-bg-subtle px-3.5 py-3 text-xs">보낸 사람에게는 알리지 않아요. 「차단한 사람 관리」에서 언제든 풀 수 있어요.</p>
      </ConfirmDialog>
      {letter && (
        <ReportDialog
          letter={letter}
          open={dialog === 'report'}
          onClose={() => setDialog(null)}
          onReported={() => {
            closeAll()
            notify('신고했어요')
          }}
        />
      )}
    </>
  )
}

function LetterView({ letter, onReply, onDialog }: { letter: Letter; onReply: () => void; onDialog: (dialog: Dialog) => void }) {
  const now = useNow(60_000)
  const received = letter.box === 'RECEIVED'
  const name = counterpartLabel(letter)

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        {letter.counterpartHidden ? <AnonymousAvatar /> : <Avatar name={name} imageUrl={letter.counterpart?.profileImageUrl} size="lg" />}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold">{received ? name : `받는 사람 · ${name}`}</p>
          <p className="truncate text-xs text-text-tertiary">
            {organizationLabel(letter)} · {formatDayTime(letter.createdAt, now)}
          </p>
        </div>
        {received && letter.anonymous && <Badge tone="neutral">익명 쪽지</Badge>}
      </div>
      {!received && (
        <div className="flex flex-wrap gap-1">
          {letter.readAt ? <Badge tone="success">읽음 · {formatClock(letter.readAt)}</Badge> : <Badge tone="neutral">안 읽음</Badge>}
          {letter.anonymous && <Badge tone="brand">익명으로 보냄</Badge>}
        </div>
      )}
      {letter.replyTo && (
        <blockquote className="border-l-[3px] border-border-strong px-3 py-1.5">
          <p className="text-xs font-medium text-text-tertiary">{received ? '내가 보낸 쪽지에 온 답장' : '받은 쪽지에 쓴 답장'}</p>
          <p className="text-sm break-words text-text-secondary">{letter.replyTo.preview ?? ''}</p>
        </blockquote>
      )}
      <p className="text-sm leading-relaxed break-words">
        <MessageBody body={letter.body} />
      </p>
      <div className="flex items-center gap-2 border-t border-border-default pt-3">
        {received && (
          <div className="flex flex-1 items-center gap-3.5">
            <button type="button" onClick={() => onDialog('block')} className="rounded text-sm font-medium text-text-secondary hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
              차단
            </button>
            {letter.reported ? (
              <span className="text-sm font-medium text-text-tertiary">신고함</span>
            ) : (
              <button type="button" onClick={() => onDialog('report')} className="rounded text-sm font-medium text-text-danger hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
                신고
              </button>
            )}
          </div>
        )}
        <div className={`flex gap-2 ${received ? '' : 'ml-auto'}`}>
          <Button variant="secondary" onClick={() => onDialog('delete')}>
            삭제
          </Button>
          {received && letter.canReply && <Button onClick={onReply}>답장</Button>}
        </div>
      </div>
    </div>
  )
}

/** 신고(Figma 10-M3): 사유(선택, 100자)와 「보낸 사람 차단하기」(기본 켜짐) */
function ReportDialog({ letter, open, onClose, onReported }: { letter: Letter; open: boolean; onClose: () => void; onReported: () => void }) {
  return (
    <Modal open={open} onClose={onClose} title="쪽지 신고" closable>
      <ReportForm letter={letter} onClose={onClose} onReported={onReported} />
    </Modal>
  )
}

/** 모달을 열 때마다 새로 그려져 사유·차단 선택이 처음 상태로 돌아간다. */
function ReportForm({ letter, onClose, onReported }: { letter: Letter; onClose: () => void; onReported: () => void }) {
  const report = useReportLetter()
  const [reason, setReason] = useState('')
  const [block, setBlock] = useState(true)
  const length = letterLength(reason)
  const now = useNow(60_000)

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault()
        report.mutate({ letterId: letter.id, reason, block }, { onSuccess: onReported })
      }}
    >
      <p className="text-sm text-text-secondary">
        이 쪽지를 서비스 관리자에게 알려요. 관리자는 신고된 쪽지와 보낸 사람(익명이어도)을 확인하고 조치해요. 보낸 사람에게는 신고 사실을 알리지 않아요.
      </p>
      <blockquote className="border-l-[3px] border-border-strong px-3 py-1.5">
        <p className="text-xs font-medium text-text-tertiary">
          {counterpartLabel(letter)} · {organizationLabel(letter)} · {formatDayTime(letter.createdAt, now)}
        </p>
        <p className="line-clamp-3 text-sm break-words text-text-secondary">{letter.body}</p>
      </blockquote>
      <div>
        <label htmlFor="report-reason" className="text-sm font-medium">
          사유 (선택)
        </label>
        <textarea
          id="report-reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder="예: 기분 나쁜 말이 있어요"
          aria-invalid={length > REPORT_REASON_MAX || undefined}
          className={`${inputClass} mt-1.5 resize-none`}
        />
        <p className={`mt-1 text-right text-xs ${length > REPORT_REASON_MAX ? 'text-text-danger' : 'text-text-tertiary'}`}>
          {length}/{REPORT_REASON_MAX}
        </p>
      </div>
      <div>
        <label className="flex cursor-pointer items-center gap-2 text-sm font-medium">
          <input type="checkbox" checked={block} onChange={(e) => setBlock(e.target.checked)} className="size-4 accent-bg-brand" />
          보낸 사람 차단하기
        </label>
        <p className="mt-1 text-xs text-text-tertiary">
          이 사람의 {letter.anonymous ? '익명 ' : ''}쪽지를 더는 받지 않아요. 「차단한 사람 관리」에서 풀 수 있어요.
        </p>
      </div>
      {report.error && (
        <p role="alert" className="text-sm text-text-danger">
          {report.error instanceof ApiError ? report.error.message : '신고하지 못했어요. 연결을 확인하고 다시 시도해 주세요.'}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button type="submit" variant="danger" disabled={report.isPending || length > REPORT_REASON_MAX}>
          신고하기
        </Button>
      </div>
    </form>
  )
}
