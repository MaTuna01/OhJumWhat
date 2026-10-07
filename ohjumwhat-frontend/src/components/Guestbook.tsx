import { type FormEvent, type KeyboardEvent, useEffect, useId, useRef, useState } from 'react'
import { useGuestbookCooldown } from '../hooks/useGuestbookCooldown.ts'
import { useNow } from '../hooks/useNow.ts'
import { useRestriction } from '../hooks/useRestriction.ts'
import { ApiError } from '../lib/api.ts'
import { authorLabel, GUESTBOOK_MAX, guestbookLength, isNewEntry, newestCreatedAt, RESTRICTED_TEXT } from '../lib/guestbook.ts'
import { linkify } from '../lib/noticeBody.ts'
import { formatDayTime } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { type GuestbookEntry, useDeleteGuestbookEntry, useGuestbook, useMarkGuestbookSeen, useWriteGuestbook } from '../queries/guestbook.ts'
import { useMe } from '../queries/me.ts'
import Avatar from './Avatar.tsx'
import Badge from './Badge.tsx'
import Button from './Button.tsx'
import ConfirmDialog from './ConfirmDialog.tsx'
import GuestbookReportDialog from './GuestbookReportDialog.tsx'
import { LinkedLine } from './NoticeBody.tsx'
import Pager from './Pager.tsx'
import { ProfileButton } from './ProfileViewer.tsx'
import RestrictionNotice from './RestrictionNotice.tsx'

type PanelProps = {
  ownerId: number
  /** 방명록 주인 이름(입력창 안내 「OO님에게 한마디 남겨 보세요」) */
  ownerName: string
}

/**
 * 한 사람의 방명록(Figma 07-P4 멤버 프로필, 07-P5 내 프로필, 03-N5 마이페이지): 입력창·목록(10개씩 최신순)·쪽 넘기기.
 * 주인은 자기 방명록에 쓸 수 없어 입력창이 없고, 아직 보지 않은 글에 NEW를 붙이고 「봤음」을 보낸다(점·배지가 꺼진다).
 * 지우기 확인(07-M3)과 신고(07-M2)는 프로필 모달 안에 그려도 된다(Modal은 자기 자신이 닫힐 때만 onClose를 부른다).
 * 관리자가 방명록 쓰기를 제한했으면 입력창 대신 RestrictionNotice를 둔다(지우기는 그대로).
 */
export function GuestbookPanel({ ownerId, ownerName }: PanelProps) {
  const { data: me } = useMe()
  const [page, setPage] = useState(0)
  const guestbook = useGuestbook(ownerId, page)
  const { mutate: markSeen } = useMarkGuestbookSeen()
  const remove = useDeleteGuestbookEntry(ownerId)
  const [removing, setRemoving] = useState<GuestbookEntry | null>(null)
  const [reporting, setReporting] = useState<GuestbookEntry | null>(null)
  // NEW는 처음 받은 seenAt으로 계산한다. 「봤음」을 보낸 뒤에도 이번에 보는 동안은 NEW가 남는다.
  const [seen, setSeen] = useState<{ at: string | null } | null>(null)
  const markedUntil = useRef(0)
  const now = useNow(60_000)
  const blocked = useRestriction('GUESTBOOK')

  const data = guestbook.data
  // 쪽을 넘기는 동안 보이는 앞 쪽(placeholder)이 아닌, 지금 쪽의 실제 응답
  const fresh = data && !guestbook.isPlaceholderData ? data : undefined
  const owner = data ? data.owner : ownerId === me?.id

  if (fresh?.owner && seen === null) setSeen({ at: fresh.seenAt })
  // 지워서 마지막 쪽이 비면 남은 마지막 쪽으로 간다.
  if (fresh && page > 0 && page >= fresh.totalPages) setPage(Math.max(0, fresh.totalPages - 1))

  // 0쪽이 보이면 가장 최근 글까지 봤다고 알린다(한 번 보낸 시각보다 새 글이 생겼을 때만 다시 보낸다).
  useEffect(() => {
    if (!fresh?.owner || fresh.page !== 0) return
    const newest = newestCreatedAt(fresh.entries)
    if (newest === null) return
    const at = Date.parse(newest)
    if (at <= markedUntil.current) return
    markedUntil.current = at
    if (fresh.seenAt === null || at > Date.parse(fresh.seenAt)) markSeen(newest)
  }, [fresh, markSeen])

  const closeRemove = () => {
    remove.reset()
    setRemoving(null)
  }

  return (
    <div className="space-y-2">
      {!owner &&
        (blocked ? (
          <RestrictionNotice type="GUESTBOOK" restriction={blocked} />
        ) : (
          <GuestbookForm ownerId={ownerId} ownerName={ownerName} onWritten={() => setPage(0)} />
        ))}
      {data === undefined ? (
        guestbook.isPending ? (
          <p role="status" className="py-8 text-center text-sm text-text-tertiary">
            불러오는 중…
          </p>
        ) : (
          <p role="alert" className="py-8 text-center text-sm text-text-danger">
            {guestbook.error?.message ?? '방명록을 불러오지 못했어요.'}
          </p>
        )
      ) : data.totalCount === 0 ? (
        <p className="py-8 text-center text-sm text-text-secondary">
          {owner ? '아직 받은 방명록이 없어요. 같은 조직 멤버가 내 프로필에서 남길 수 있어요.' : '아직 방명록이 없어요. 첫 한마디를 남겨 보세요.'}
        </p>
      ) : (
        <>
          <ul className="divide-y divide-border-default">
            {data.entries.map((entry) => (
              <GuestbookRow
                key={entry.id}
                entry={entry}
                now={now}
                owner={data.owner}
                isNew={data.owner && seen !== null && isNewEntry(entry.createdAt, seen.at)}
                onDelete={() => {
                  remove.reset()
                  setRemoving(entry)
                }}
                onReport={() => setReporting(entry)}
              />
            ))}
          </ul>
          <Pager page={page} totalPages={data.totalPages} onChange={setPage} busy={guestbook.isPlaceholderData} />
        </>
      )}
      <ConfirmDialog
        open={removing !== null}
        onClose={closeRemove}
        onConfirm={() => removing && remove.mutate(removing.id, { onSuccess: closeRemove })}
        title="방명록 글을 지울까요?"
        confirmLabel="지우기"
        danger
        pending={remove.isPending}
        error={remove.error ? (remove.error instanceof ApiError ? remove.error.message : '지우지 못했어요.') : null}
      >
        지운 글은 되돌릴 수 없어요.
      </ConfirmDialog>
      <GuestbookReportDialog ownerId={ownerId} entry={reporting} onClose={() => setReporting(null)} />
    </div>
  )
}

type RowProps = {
  entry: GuestbookEntry
  now: number
  /** 보는 사람이 방명록 주인(신고할 수 있다) */
  owner: boolean
  /** 주인이 아직 보지 않은 글 */
  isNew: boolean
  onDelete: () => void
  onReport: () => void
}

/**
 * Figma GuestbookEntry(Default·Mine·Owner·Reported·Restricted·Withdrawn·New): 사진·이름(누르면 프로필)·시각·NEW와 본문.
 * 관리자가 제한한 글은 본문 대신 제한 문구만 보이고, 주인만 지울 수 있다(신고는 없다). 탈퇴한 사용자는 회색 「?」와 이름으로 남는다.
 */
export function GuestbookRow({ entry, now, owner, isNew, onDelete, onReport }: RowProps) {
  const name = authorLabel(entry)
  // 제한된 글은 주인만 지울 수 있고(서버의 canDelete가 같은 규칙), 신고는 할 수 없다.
  const showDelete = entry.canDelete
  const showReport = owner && !entry.restricted && (entry.canReport || entry.reported)

  return (
    <li className="flex gap-2.5 py-3">
      <ProfileButton person={entry.author} focusable={false} className="mt-0.5 h-fit shrink-0 rounded-full">
        {entry.author ? (
          <Avatar name={name} imageUrl={entry.author.profileImageUrl} size="sm" />
        ) : (
          <span aria-hidden className="inline-flex size-6 items-center justify-center rounded-full bg-bg-muted text-xs font-bold text-text-tertiary">
            ?
          </span>
        )}
      </ProfileButton>
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <p className="flex min-w-0 flex-1 flex-wrap items-center gap-x-1.5 gap-y-0.5">
            <ProfileButton person={entry.author} className={`rounded text-sm font-bold ${entry.author ? 'hover:underline' : 'text-text-tertiary'}`}>
              {name}
              {entry.mine && ' (나)'}
            </ProfileButton>
            <span className="text-xs text-text-tertiary">{formatDayTime(entry.createdAt, now)}</span>
            {isNew && <Badge tone="brand">NEW</Badge>}
          </p>
          {(showDelete || showReport) && (
            <span className="flex shrink-0 items-center gap-1.5 pt-0.5 text-xs font-medium">
              {showDelete && (
                <button type="button" onClick={onDelete} className="rounded text-text-danger hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
                  지우기
                </button>
              )}
              {showDelete && showReport && (
                <span aria-hidden className="text-text-placeholder">
                  ·
                </span>
              )}
              {showReport &&
                (entry.reported ? (
                  <span className="text-text-tertiary">신고함</span>
                ) : (
                  <button type="button" onClick={onReport} className="rounded text-text-secondary hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
                    신고
                  </button>
                ))}
            </span>
          )}
        </div>
        {entry.restricted || entry.body === null ? (
          <p className="mt-1.5 rounded-lg bg-bg-muted px-3 py-2 text-sm text-text-tertiary">{RESTRICTED_TEXT}</p>
        ) : (
          <p className="mt-0.5 text-sm break-words">
            <LinkedLine line={linkify(entry.body)} />
          </p>
        )}
      </div>
    </li>
  )
}

type FormProps = {
  ownerId: number
  ownerName: string
  /** 남긴 뒤(내 글이 맨 위에 생긴 0쪽으로 돌아간다) */
  onWritten: () => void
}

/**
 * Figma GuestbookInput(Empty·Near·Over·Cooldown·Error): 한 줄 입력과 「남기기」. Enter로 남긴다(한글 조합 중의 Enter는 빼고).
 * 남긴 뒤 5초는 앱 전체에서 다시 남길 수 없어 입력창과 버튼을 막고 남은 초를 보여준다.
 */
function GuestbookForm({ ownerId, ownerName, onWritten }: FormProps) {
  const write = useWriteGuestbook(ownerId)
  const cooldown = useGuestbookCooldown()
  const [value, setValue] = useState('')
  const [empty, setEmpty] = useState(false)
  const captionId = useId()
  const length = guestbookLength(value)
  const over = length > GUESTBOOK_MAX
  const waiting = cooldown > 0
  const error = empty
    ? '내용을 입력해 주세요.'
    : write.error
      ? write.error instanceof ApiError
        ? write.error.message
        : '남기지 못했어요. 연결을 확인하고 다시 시도해 주세요.'
      : null
  const showCount = length > GUESTBOOK_MAX - 10
  const caption = error ?? (waiting ? `${cooldown}초 뒤에 다시 남길 수 있어요` : null)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (over || waiting || write.isPending) return
    if (length === 0) {
      setEmpty(true)
      return
    }
    write.mutate(value.trim(), {
      onSuccess: () => {
        setValue('')
        onWritten()
      },
    })
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // 한글 조합 중의 Enter는 글자를 끝내는 키라 남기지 않는다(조합이 끝난 뒤의 Enter만 쓴다).
    if (e.key === 'Enter' && (e.nativeEvent.isComposing || e.keyCode === 229)) e.preventDefault()
  }

  return (
    <form onSubmit={submit} className="space-y-1.5">
      <div className="flex gap-2">
        <input
          value={value}
          onChange={(e) => {
            setValue(e.target.value)
            setEmpty(false)
            if (write.isError) write.reset()
          }}
          onKeyDown={onKeyDown}
          readOnly={waiting}
          placeholder={`${ownerName}님에게 한마디 남겨 보세요`}
          aria-label={`${ownerName}님 방명록에 남길 말`}
          aria-invalid={over || undefined}
          aria-describedby={caption || showCount ? captionId : undefined}
          className={`${inputClass} read-only:cursor-not-allowed read-only:bg-bg-subtle`}
        />
        <Button type="submit" className="shrink-0" disabled={over || waiting || write.isPending}>
          남기기
        </Button>
      </div>
      {(caption || showCount) && (
        <div id={captionId} className="flex items-start gap-2 text-xs">
          {caption && (
            <p role={error ? 'alert' : undefined} className={`min-w-0 flex-1 ${error ? 'text-text-danger' : 'text-text-tertiary'}`}>
              {caption}
            </p>
          )}
          {showCount && (
            <p className={`ml-auto shrink-0 tabular-nums ${over ? 'text-text-danger' : 'text-text-tertiary'}`}>
              {length}/{GUESTBOOK_MAX}
            </p>
          )}
        </div>
      )}
    </form>
  )
}
