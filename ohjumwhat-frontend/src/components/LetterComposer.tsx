import { type FormEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { type ComposeTarget, LetterComposerContext } from '../hooks/useLetterComposer.ts'
import { ApiError } from '../lib/api.ts'
import { counterpartLabel, LETTER_MAX, letterLength, organizationLabel } from '../lib/letters.ts'
import { firstLine } from '../lib/chat.ts'
import { inputClass } from '../lib/ui.ts'
import { useSendLetter } from '../queries/letters.ts'
import { useMe } from '../queries/me.ts'
import { useMembers, useMyOrganizations } from '../queries/orgs.ts'
import type { Person } from '../queries/polls.ts'
import AnonymousAvatar from './AnonymousAvatar.tsx'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

/**
 * 쪽지 쓰기(Figma 10-M1·10-M1b·10-M1c)를 앱 전체에 하나만 둔다(AppLayout). 멤버 프로필의 「쪽지 보내기」, 쪽지함의
 * 「쪽지 쓰기」·「답장」이 같은 모달을 연다. 채팅 시트(<dialog>) 밖에 그려져서 닫아도 다른 모달에 영향이 없다.
 */
export function LetterComposerProvider({ children }: { children: ReactNode }) {
  const [target, setTarget] = useState<ComposeTarget | null>(null)
  const [toast, setToast] = useState<{ message: string; key: number } | null>(null)

  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), 2500)
    return () => clearTimeout(timer)
  }, [toast])

  const notify = useCallback((message: string) => setToast({ message, key: Date.now() }), [])
  const value = useMemo(() => ({ compose: setTarget, notify }), [notify])

  return (
    <LetterComposerContext value={value}>
      {children}
      <Modal open={target !== null} onClose={() => setTarget(null)} title={target?.kind === 'reply' ? '답장 쓰기' : '쪽지 쓰기'} closable>
        {target && (
          <ComposeForm
            key={target.kind === 'reply' ? `reply-${target.letter.id}` : `new-${target.organizationId ?? ''}-${target.recipient?.userId ?? ''}`}
            target={target}
            onCancel={() => setTarget(null)}
            onSent={() => {
              setTarget(null)
              notify('쪽지를 보냈어요')
            }}
          />
        )}
      </Modal>
      {toast && <Toast key={toast.key} message={toast.message} />}
    </LetterComposerContext>
  )
}

/**
 * 「쪽지를 보냈어요」 같은 짧은 안내. 채팅 시트(모달 <dialog>) 위에서 보냈어도 보이게 popover로 top layer에 올린다
 * (그냥 fixed로 두면 모달 아래에 가려진다). popover를 지원하지 않는 브라우저에서는 fixed로 보인다.
 */
function Toast({ message }: { message: string }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = ref.current
    if (el && typeof el.showPopover === 'function' && !el.matches(':popover-open')) el.showPopover()
  }, [])
  return (
    <div
      ref={ref}
      popover="manual"
      role="status"
      className="fixed inset-x-0 top-auto bottom-6 z-50 mx-auto my-0 h-fit w-fit max-w-[calc(100%-2rem)] overflow-visible rounded-full border-0 bg-bg-inverse px-4 py-2 text-sm font-medium text-text-on-brand shadow-lg"
    >
      {message}
    </div>
  )
}

function ComposeForm({ target, onCancel, onSent }: { target: ComposeTarget; onCancel: () => void; onSent: () => void }) {
  const { data: me } = useMe()
  const orgs = useMyOrganizations()
  const send = useSendLetter()
  const fixed = target.kind === 'new' && target.recipient && target.organizationId ? { organizationId: target.organizationId, person: target.recipient } : null
  const [organizationId, setOrganizationId] = useState<number | null>(
    target.kind === 'new' ? (target.organizationId ?? me?.lastVisitedOrgId ?? null) : null,
  )
  // 고른 받는 사람은 그 조직과 함께 둔다(조직 목록이 바뀌어 다른 조직으로 넘어가면 받는 사람도 비운다).
  const [recipient, setRecipient] = useState<{ organizationId: number; userId: number } | null>(null)
  const [body, setBody] = useState('')
  // 답장의 익명 여부는 서버가 정한다: 내가 익명으로 보낸 쪽지에 온 답장에 다시 답할 때만 익명(체크를 잠가 보여준다).
  // 그 밖의 답장은 상대가 누구에게 보냈는지 알아서 익명이 될 수 없어 체크를 보여주지 않는다.
  const lockedAnonymous = target.kind === 'reply' && target.letter.replyAnonymous
  const [anonymous, setAnonymous] = useState(lockedAnonymous)

  // 고르는 경우: 기억해 둔 조직이 지금 내 조직이 아니면 첫 조직으로
  const orgList = orgs.data ?? []
  const pickedOrgId = organizationId != null && orgList.some((o) => o.id === organizationId) ? organizationId : (orgList[0]?.id ?? null)
  const recipientId = recipient && recipient.organizationId === pickedOrgId ? recipient.userId : null
  const length = letterLength(body)
  const hasRecipient = target.kind === 'reply' || fixed !== null || recipientId !== null
  const canSend = hasRecipient && length > 0 && length <= LETTER_MAX && !send.isPending

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!canSend) return
    const request =
      target.kind === 'reply'
        ? ({ kind: 'reply', letterId: target.letter.id, body } as const)
        : ({
            kind: 'new',
            organizationId: fixed ? fixed.organizationId : (pickedOrgId as number),
            recipientId: fixed ? fixed.person.userId : (recipientId as number),
            body,
            anonymous,
          } as const)
    send.mutate(request, { onSuccess: onSent })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <p className="text-sm font-medium" id="letter-to-label">
          받는 사람
        </p>
        {target.kind === 'reply' ? (
          <>
            <RecipientChip
              person={target.letter.counterpartHidden ? null : target.letter.counterpart}
              anonymous={target.letter.counterpartHidden}
              name={counterpartLabel(target.letter)}
              organization={organizationLabel(target.letter)}
            />
            <blockquote className="mt-2 border-l-[3px] border-border-strong px-3 py-1.5">
              <p className="text-xs font-medium text-text-tertiary">받은 쪽지</p>
              <p className="text-sm break-words text-text-secondary">{firstLine(target.letter.body)}</p>
            </blockquote>
          </>
        ) : fixed ? (
          <RecipientChip
            person={fixed.person}
            name={fixed.person.name}
            organization={orgList.find((o) => o.id === fixed.organizationId)?.name ?? ''}
          />
        ) : orgList.length === 0 ? (
          <p className="mt-1.5 rounded-lg bg-bg-subtle px-3 py-2.5 text-sm text-text-tertiary">
            {orgs.isPending ? '불러오는 중…' : '속한 조직이 없어요. 조직에 들어가면 같은 조직 멤버에게 쪽지를 보낼 수 있어요.'}
          </p>
        ) : (
          <>
            {orgList.length > 1 && (
              <select
                aria-label="조직"
                value={pickedOrgId ?? ''}
                onChange={(e) => {
                  setOrganizationId(Number(e.target.value))
                  setRecipient(null)
                }}
                className={`${inputClass} mt-1.5`}
              >
                {orgList.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.name}
                  </option>
                ))}
              </select>
            )}
            {pickedOrgId != null && (
              <MemberPicker orgId={pickedOrgId} myId={me?.id} value={recipientId} onChange={(userId) => setRecipient({ organizationId: pickedOrgId, userId })} />
            )}
          </>
        )}
      </div>

      <div>
        <label htmlFor="letter-body" className="text-sm font-medium">
          내용
        </label>
        <textarea
          id="letter-body"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={5}
          placeholder="하고 싶은 말을 적어 주세요"
          aria-invalid={length > LETTER_MAX || undefined}
          className={`${inputClass} mt-1.5 resize-none`}
        />
        <p className={`mt-1 text-right text-xs ${length > LETTER_MAX ? 'text-text-danger' : 'text-text-tertiary'}`}>
          {length}/{LETTER_MAX}
        </p>
      </div>

      {target.kind === 'new' || lockedAnonymous ? (
        <div>
          <label className={`flex items-center gap-2 text-sm font-medium ${lockedAnonymous ? 'opacity-60' : 'cursor-pointer'}`}>
            <input
              type="checkbox"
              checked={anonymous}
              disabled={lockedAnonymous}
              onChange={(e) => setAnonymous(e.target.checked)}
              className="size-4 accent-bg-brand"
              aria-describedby="letter-anonymous-help"
            />
            익명으로 보내기
          </label>
          <p id="letter-anonymous-help" className="mt-1 text-xs text-text-tertiary">
            {lockedAnonymous
              ? '익명으로 보낸 쪽지에 온 답장이라 계속 익명으로 보내요. 상대에게는 「익명」으로 보여요.'
              : '받는 사람에게 내 이름과 사진이 보이지 않아요. 신고되면 서비스 관리자는 보낸 사람을 확인할 수 있어요.'}
          </p>
        </div>
      ) : (
        <p className="rounded-lg bg-bg-subtle px-3 py-2.5 text-xs text-text-tertiary">
          답장은 내 이름으로 보내요. 상대는 자기가 이 쪽지를 누구에게 보냈는지 알고 있어서 답장은 익명이 되지 않아요.
        </p>
      )}
      {send.error && (
        <p role="alert" className="text-sm text-text-danger">
          {send.error instanceof ApiError ? send.error.message : '보내지 못했어요. 연결을 확인하고 다시 시도해 주세요.'}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          취소
        </Button>
        <Button type="submit" disabled={!canSend}>
          보내기
        </Button>
      </div>
    </form>
  )
}

/** 받는 사람(고정·답장). 익명이면 AnonymousAvatar */
function RecipientChip({ person, anonymous, name, organization }: { person: Person | null; anonymous?: boolean; name: string; organization: string }) {
  return (
    <div className="mt-1.5 flex items-center gap-2 rounded-lg bg-bg-subtle px-2.5 py-2">
      {anonymous ? <AnonymousAvatar size="md" /> : <Avatar name={name} imageUrl={person?.profileImageUrl} size="md" />}
      <span className="min-w-0 truncate text-sm font-bold">{name}</span>
      {organization && <span className="shrink-0 text-xs text-text-tertiary">{organization}</span>}
    </div>
  )
}

/** 조직 멤버 중 한 명 고르기(나는 뺀다). 고른 줄은 브랜드 배경과 ✓ */
function MemberPicker({ orgId, myId, value, onChange }: { orgId: number; myId: number | undefined; value: number | null; onChange: (id: number) => void }) {
  const members = useMembers(orgId)
  const others = (members.data ?? []).filter((m) => m.userId !== myId)
  if (members.isPending) return <p className="mt-1.5 text-sm text-text-tertiary">불러오는 중…</p>
  if (others.length === 0) return <p className="mt-1.5 rounded-lg bg-bg-subtle px-3 py-2.5 text-sm text-text-tertiary">이 조직에는 아직 다른 멤버가 없어요.</p>
  return (
    <div role="radiogroup" aria-labelledby="letter-to-label" className="mt-1.5 max-h-48 overflow-y-auto rounded-lg border border-border-default py-1">
      {others.map((m) => {
        const selected = m.userId === value
        return (
          <label
            key={m.userId}
            className={`flex cursor-pointer items-center gap-2 px-2.5 py-2 has-[:focus-visible]:outline-2 has-[:focus-visible]:-outline-offset-2 has-[:focus-visible]:outline-border-brand ${
              selected ? 'bg-bg-brand-soft' : 'hover:bg-bg-subtle'
            }`}
          >
            <input type="radio" name="letter-recipient" checked={selected} onChange={() => onChange(m.userId)} className="sr-only" />
            <Avatar name={m.name} imageUrl={m.profileImageUrl} size="sm" />
            <span className={`min-w-0 flex-1 truncate text-sm ${selected ? 'font-bold' : ''}`}>{m.name}</span>
            {selected && <span className="text-sm font-bold text-text-brand" aria-hidden>✓</span>}
          </label>
        )
      })}
    </div>
  )
}
