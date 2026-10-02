import { type FormEvent, type ReactNode, useState } from 'react'
import { COMMENT_MAX, commentLength, commentsPanelId } from '../lib/comments.ts'
import { linkify } from '../lib/noticeBody.ts'
import { formatClock } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { type MenuComment, useAddComment, useDeleteComment, useEditComment, useOptionComments } from '../queries/comments.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import ConfirmDialog from './ConfirmDialog.tsx'
import { LinkedLine } from './NoticeBody.tsx'

type Props = {
  pollId: number
  optionId: number
  menuName: string
  /** 마감된 투표: 읽기만 한다(Figma MenuComments Readonly) */
  readOnly: boolean
}

/**
 * Figma MenuComments(05-K·05b-K): 메뉴 카드 바로 아래로 펼친 댓글.
 * 진행 중에는 쓰고 내 글을 고치고 지운다(Open·Empty). 마감된 투표는 기록이라 읽기만 한다(Readonly).
 * 목록은 펼칠 때 받고 다른 사람의 새 댓글을 실시간으로 받지는 않는다(댓글 수는 투표 상세 폴링으로 바뀐다).
 * 카드(role="button") 안에 입력창을 넣지 않도록 카드 밖, 바로 아래에 그린다.
 */
export default function OptionComments({ pollId, optionId, menuName, readOnly }: Props) {
  const comments = useOptionComments(pollId, optionId)
  const add = useAddComment(pollId, optionId)
  const edit = useEditComment(pollId, optionId)
  const remove = useDeleteComment(pollId, optionId)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [removing, setRemoving] = useState<MenuComment | null>(null)
  const closeRemove = () => {
    remove.reset()
    setRemoving(null)
  }

  return (
    <section id={commentsPanelId(optionId)} aria-label={`${menuName} 댓글`} className="space-y-3 rounded-xl border border-border-default bg-bg-subtle p-3">
      <CommentList
        comments={comments.data}
        loading={comments.isPending}
        error={comments.error?.message}
        empty={readOnly ? '댓글이 없어요' : '아직 댓글이 없어요. 웨이팅·휴무 같은 정보를 남겨 주세요'}
        render={(comment) =>
          !readOnly && editingId === comment.id ? (
            <CommentForm
              initial={comment.body}
              label="댓글 고치기"
              submitLabel="저장"
              pending={edit.isPending}
              error={edit.error?.message}
              onCancel={() => {
                edit.reset()
                setEditingId(null)
              }}
              onSubmit={(body) => edit.mutate({ commentId: comment.id, body }, { onSuccess: () => setEditingId(null) })}
            />
          ) : (
            <CommentRow
              comment={comment}
              actions={
                !readOnly &&
                comment.mine && (
                  <>
                    <button type="button" onClick={() => setEditingId(comment.id)} className="font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
                      고치기
                    </button>
                    <button type="button" onClick={() => setRemoving(comment)} className="font-medium text-text-danger hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
                      삭제
                    </button>
                  </>
                )
              }
            />
          )
        }
      />
      {readOnly ? (
        <p className="text-xs text-text-tertiary">마감된 투표의 댓글은 읽기만 할 수 있어요</p>
      ) : (
        <CommentForm
          label={`${menuName} 댓글`}
          placeholder={`댓글 달기 (${COMMENT_MAX}자)`}
          submitLabel="등록"
          pending={add.isPending}
          error={add.error?.message}
          onSubmit={(body, clear) => add.mutate(body, { onSuccess: clear })}
        />
      )}
      <ConfirmDialog
        open={removing != null}
        onClose={closeRemove}
        onConfirm={() => removing && remove.mutate(removing.id, { onSuccess: closeRemove })}
        title="댓글을 삭제할까요?"
        confirmLabel="삭제"
        danger
        pending={remove.isPending}
        error={remove.error?.message}
      >
        <p className="break-words">「{removing?.body}」</p>
      </ConfirmDialog>
    </section>
  )
}

/** 댓글 목록(불러오는 중·오류·빈 상태 포함). 관리자 콘솔도 쓴다. */
export function CommentList({ comments, loading, error, empty, render }: { comments: MenuComment[] | undefined; loading: boolean; error?: string; empty: string; render: (comment: MenuComment) => ReactNode }) {
  if (loading) return <p className="text-xs text-text-tertiary">댓글을 불러오는 중이에요</p>
  if (error) {
    return (
      <p role="alert" className="text-xs text-text-danger">
        {error}
      </p>
    )
  }
  if (!comments || comments.length === 0) return <p className="text-xs text-text-tertiary">{empty}</p>
  return (
    <ul className="space-y-3">
      {comments.map((comment) => (
        <li key={comment.id}>{render(comment)}</li>
      ))}
    </ul>
  )
}

/** 댓글 한 개: 아바타, 이름(탈퇴했으면 「탈퇴한 사용자」)·시각·수정됨, 본문(http(s) 주소는 새 탭 링크) */
export function CommentRow({ comment, actions }: { comment: MenuComment; actions?: ReactNode }) {
  const name = comment.author?.name ?? '탈퇴한 사용자'
  return (
    <div className="flex gap-2">
      <Avatar name={name} imageUrl={comment.author?.profileImageUrl} size="sm" />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-1.5 text-xs">
          <span className="font-medium text-text-primary">
            {name}
            {comment.mine && ' (나)'}
          </span>
          <span className="text-text-tertiary">
            {formatClock(comment.createdAt)}
            {comment.edited && ' · 수정됨'}
          </span>
          {actions && <span className="ml-auto flex gap-2">{actions}</span>}
        </div>
        <p className="mt-0.5 text-sm break-words text-text-secondary">
          <LinkedLine line={linkify(comment.body)} />
        </p>
      </div>
    </div>
  )
}

type FormProps = {
  initial?: string
  label: string
  placeholder?: string
  submitLabel: string
  pending: boolean
  error?: string
  /** clear: 입력창을 비운다(새 댓글을 등록한 뒤) */
  onSubmit: (body: string, clear: () => void) => void
  onCancel?: () => void
}

function CommentForm({ initial = '', label, placeholder, submitLabel, pending, error, onSubmit, onCancel }: FormProps) {
  const [value, setValue] = useState(initial)
  const length = commentLength(value)
  const over = length > COMMENT_MAX
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (length === 0 || over || pending) return
    onSubmit(value.trim(), () => setValue(''))
  }

  return (
    <form onSubmit={submit} className="space-y-1.5">
      <div className="flex gap-2">
        <input value={value} onChange={(e) => setValue(e.target.value)} placeholder={placeholder} aria-label={label} className={inputClass} />
        <Button type="submit" variant="secondary" className="shrink-0" disabled={length === 0 || over || pending}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button variant="ghost" className="shrink-0" onClick={onCancel}>
            취소
          </Button>
        )}
      </div>
      {length > COMMENT_MAX - 20 && (
        <p className={`text-right text-xs ${over ? 'text-text-danger' : 'text-text-tertiary'}`}>
          {length}/{COMMENT_MAX}
        </p>
      )}
      {error && (
        <p role="alert" className="text-xs text-text-danger">
          {error}
        </p>
      )}
    </form>
  )
}
