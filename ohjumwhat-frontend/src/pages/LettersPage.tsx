import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import AnonymousAvatar from '../components/AnonymousAvatar.tsx'
import Avatar from '../components/Avatar.tsx'
import Badge from '../components/Badge.tsx'
import Button from '../components/Button.tsx'
import LetterBlocksModal from '../components/LetterBlocksModal.tsx'
import LetterModal from '../components/LetterModal.tsx'
import { PageLoader, Section } from '../components/PageState.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useLetterComposer } from '../hooks/useLetterComposer.ts'
import { useNow } from '../hooks/useNow.ts'
import { firstLine } from '../lib/chat.ts'
import { counterpartLabel, organizationLabel, unreadLetterLabel } from '../lib/letters.ts'
import { formatClock, formatDay } from '../lib/time.ts'
import { columnsClass } from '../lib/ui.ts'
import { type Letter, type LetterBox, letterKeys, useLetterBlocks, useLetters, useUnreadLetters } from '../queries/letters.ts'

/**
 * 쪽지함(Figma 10 받은 쪽지, 10b 보낸 쪽지, 10c 비었을 때, 데스크톱 D10). 상단 바 봉투 아이콘에서 온다.
 * 탭은 주소의 ?box=sent로 기억한다. 쪽지를 누르면 쪽지 보기(10-M2), 「쪽지 쓰기」는 받는 사람을 고르는 쪽지 쓰기(10-M1b).
 */
export default function LettersPage() {
  const [params, setParams] = useSearchParams()
  const box: LetterBox = params.get('box') === 'sent' ? 'SENT' : 'RECEIVED'
  const letters = useLetters(box)
  const unread = useUnreadLetters()
  const blocks = useLetterBlocks(true)
  const { compose } = useLetterComposer()
  // 연 쪽지: 목록이 다시 받아져 그 쪽지가 빠져도(새 쪽지에 밀림, 다른 탭에서 지움) 보기 창이 갑자기 닫히지 않게 열 때의 값을 둔다.
  const [openLetter, setOpenLetter] = useState<Letter | null>(null)
  const [managingBlocks, setManagingBlocks] = useState(false)
  const queryClient = useQueryClient()
  const now = useNow(60_000)
  useDocumentTitle('쪽지')

  // 보고 있는 동안 새 쪽지가 오면(안 읽은 수가 늘면) 받은 쪽지함을 다시 받는다.
  const unreadCount = unread.data?.count ?? 0
  const lastUnread = useRef<number | null>(null)
  useEffect(() => {
    if (unread.data === undefined) return
    const count = unread.data.count
    if (lastUnread.current !== null && count > lastUnread.current) queryClient.invalidateQueries({ queryKey: letterKeys.box('RECEIVED') })
    lastUnread.current = count
  }, [unread.data, queryClient])

  const all = letters.data?.pages.flatMap((page) => page.letters) ?? []
  const opened = openLetter ? (all.find((l) => l.id === openLetter.id) ?? openLetter) : null
  const blockCount = blocks.data?.length ?? 0

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">쪽지</h1>
          <p className="mt-1 text-sm text-text-tertiary">같은 조직 멤버와 주고받은 쪽지예요</p>
        </div>
        <Button onClick={() => compose({ kind: 'new' })} className="shrink-0 lg:hidden">
          쪽지 쓰기
        </Button>
      </div>

      <div className={`flex flex-col gap-6 ${columnsClass}`}>
        <div className="min-w-0 space-y-4">
          <nav className="flex gap-1 border-b border-border-default" aria-label="쪽지함">
            {(['RECEIVED', 'SENT'] as const).map((tab) => (
              <button
                key={tab}
                type="button"
                aria-current={box === tab ? 'page' : undefined}
                onClick={() => setParams(tab === 'SENT' ? { box: 'sent' } : {}, { replace: true })}
                className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
                  box === tab ? 'border-border-brand text-text-brand' : 'border-transparent text-text-tertiary hover:text-text-primary'
                }`}
              >
                {tab === 'RECEIVED' ? '받은 쪽지' : '보낸 쪽지'}
                {tab === 'RECEIVED' && unreadCount > 0 && <span className="ml-1">{unreadLetterLabel(unreadCount)}</span>}
              </button>
            ))}
          </nav>

          {letters.isPending ? (
            <PageLoader />
          ) : letters.isError ? (
            <p className="text-sm text-text-danger">{letters.error.message}</p>
          ) : all.length === 0 ? (
            <div className="rounded-2xl border border-border-default bg-bg-surface px-5 py-10 text-center">
              <p className="text-sm font-medium">{box === 'RECEIVED' ? '아직 받은 쪽지가 없어요' : '아직 보낸 쪽지가 없어요'}</p>
              <p className="mt-1 text-xs text-text-tertiary">멤버를 누르면 뜨는 프로필에서 「쪽지 보내기」로 먼저 보내 보세요.</p>
            </div>
          ) : (
            <ul className="divide-y divide-border-default rounded-2xl border border-border-default bg-bg-surface px-5 py-1">
              {all.map((letter) => (
                <li key={letter.id}>
                  <LetterItem letter={letter} now={now} onOpen={() => setOpenLetter(letter)} />
                </li>
              ))}
            </ul>
          )}
          {letters.hasNextPage && (
            <div className="flex justify-center">
              <Button variant="ghost" onClick={() => letters.fetchNextPage()} disabled={letters.isFetchingNextPage}>
                더 보기
              </Button>
            </div>
          )}
          {box === 'RECEIVED' && (
            <div className="flex justify-center lg:hidden">
              <ManageBlocksButton count={blockCount} onClick={() => setManagingBlocks(true)} />
            </div>
          )}
        </div>

        <aside className="hidden lg:block">
          <Section title="쪽지 보내기">
            <p className="text-sm text-text-secondary">투표가 없을 때도 같은 조직 멤버에게 쪽지를 보낼 수 있어요. 익명으로도 보낼 수 있어요.</p>
            <Button onClick={() => compose({ kind: 'new' })} className="mt-4 w-full">
              쪽지 쓰기
            </Button>
            <div className="mt-3">
              <ManageBlocksButton count={blockCount} onClick={() => setManagingBlocks(true)} />
            </div>
          </Section>
        </aside>
      </div>

      <LetterModal key={opened?.id ?? 'none'} letter={opened} onClose={() => setOpenLetter(null)} />
      <LetterBlocksModal open={managingBlocks} onClose={() => setManagingBlocks(false)} />
    </div>
  )
}

function ManageBlocksButton({ count, onClick }: { count: number; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded text-sm font-medium text-text-secondary hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
    >
      차단한 사람 관리{count > 0 ? ` (${count})` : ''}
    </button>
  )
}

/** Figma LetterItem: 받은 쪽지(안 읽음은 굵은 이름과 브랜드 점)·보낸 쪽지(읽음/안 읽음, 익명으로 보냄) */
function LetterItem({ letter, now, onOpen }: { letter: Letter; now: number; onOpen: () => void }) {
  const received = letter.box === 'RECEIVED'
  const unread = received && !letter.readAt
  const name = counterpartLabel(letter)
  const today = formatDay(letter.createdAt, now) === '오늘'

  return (
    <button
      type="button"
      onClick={onOpen}
      aria-haspopup="dialog"
      className="flex w-full items-start gap-3 py-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
    >
      {letter.counterpartHidden ? <AnonymousAvatar /> : <Avatar name={name} imageUrl={letter.counterpart?.profileImageUrl} size="lg" />}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          {/* 이름이 조직 이름보다 먼저 보이게: 이름은 줄이지 않고(최대 절반), 조직 이름을 줄인다. */}
          <span
            className={`max-w-[50%] shrink-0 truncate text-sm ${unread ? 'font-bold' : 'font-medium'} ${letter.counterpart || letter.counterpartHidden ? '' : 'text-text-secondary'}`}
          >
            {name}
          </span>
          <span className="min-w-0 truncate text-xs text-text-tertiary">{organizationLabel(letter)}</span>
          <span className="flex-1" />
          <span className="shrink-0 text-xs text-text-tertiary">{today ? formatClock(letter.createdAt) : formatDay(letter.createdAt, now)}</span>
          {unread && (
            <span className="size-2 shrink-0 rounded-full bg-bg-brand">
              <span className="sr-only">안 읽음</span>
            </span>
          )}
        </div>
        <p className={`mt-0.5 truncate text-sm ${unread ? 'text-text-primary' : 'text-text-secondary'}`}>{firstLine(letter.body)}</p>
        {!received && (
          <div className="mt-1.5 flex flex-wrap gap-1">
            {letter.readAt ? <Badge tone="success">읽음</Badge> : <Badge tone="neutral">안 읽음</Badge>}
            {letter.anonymous && <Badge tone="brand">익명으로 보냄</Badge>}
          </div>
        )}
      </div>
    </button>
  )
}
