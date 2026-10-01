import { useState } from 'react'
import { Link, useParams } from 'react-router'
import Badge from '../components/Badge.tsx'
import MapLinkModal from '../components/MapLinkModal.tsx'
import MenuInput from '../components/MenuInput.tsx'
import OptionCard from '../components/OptionCard.tsx'
import { PageLoader, PageMessage, Section } from '../components/PageState.tsx'
import PersonChip from '../components/PersonChip.tsx'
import PollManageMenu from '../components/PollManageMenu.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useNow } from '../hooks/useNow.ts'
import { useOrgId } from '../hooks/useOrgId.ts'
import { ApiError } from '../lib/api.ts'
import { confirmedTeams } from '../lib/pollDetail.ts'
import { formatClock, formatRemaining } from '../lib/time.ts'
import { buttonClass, columnsClass } from '../lib/ui.ts'
import { useMe } from '../queries/me.ts'
import { useOrganization } from '../queries/orgs.ts'
import { type PollDetail, type Person, useAddOption, useDeleteOption, usePollDetail, useVote } from '../queries/polls.ts'

/**
 * Figma 05 투표 상세(진행 중) / 05b(마감 결과). 진행 중에는 3초마다 다시 불러온다.
 * 데스크톱(D05·D05b)은 메뉴를 본문에, 응답 현황·패스·미응답을 오른쪽 사이드에 둔다.
 * 진행 중일 때 제목 옆 ⋯(05-A)에서 수정·지금 마감·삭제를 한다.
 */
export default function PollDetailPage() {
  const orgId = useOrgId()
  const pollId = Number(useParams().pollId)
  const poll = usePollDetail(orgId, pollId)
  const { data: org } = useOrganization(orgId)
  useDocumentTitle(poll.data?.title, org?.name)

  if (!Number.isInteger(pollId) || (poll.error instanceof ApiError && poll.error.status === 404)) {
    return (
      <PageMessage title="투표를 찾을 수 없어요">
        <p>삭제됐거나 볼 수 없는 투표예요.</p>
        <Link to={`/orgs/${orgId}`} className={buttonClass('secondary', 'mt-4')}>
          투표 목록으로
        </Link>
      </PageMessage>
    )
  }
  if (poll.isError) return <PageMessage title="투표를 불러오지 못했어요">{poll.error.message}</PageMessage>
  if (poll.isPending) return <PageLoader />

  return (
    <div className="space-y-4">
      <Link to={`/orgs/${orgId}`} className="inline-block text-sm font-medium text-text-tertiary hover:text-text-secondary">
        ‹ {org?.name ?? '조직'} 투표 목록
      </Link>
      {poll.data.status === 'OPEN' ? <OpenPoll orgId={orgId} poll={poll.data} /> : <ClosedPoll poll={poll.data} />}
    </div>
  )
}

function OpenPoll({ orgId, poll }: { orgId: number; poll: PollDetail }) {
  const { data: me } = useMe()
  const now = useNow(10_000)
  const vote = useVote(orgId, poll.id, me)
  const addOption = useAddOption(orgId, poll.id)
  const deleteOption = useDeleteOption(orgId, poll.id)
  const [linkOptionId, setLinkOptionId] = useState<number | null>(null)
  const remaining = formatRemaining(poll.closesAt, now)
  const error = vote.error ?? addOption.error ?? deleteOption.error
  const passedMe = poll.myResponse === 'PASS'

  return (
    <div className={`flex flex-col gap-4 ${columnsClass}`}>
      <div className="min-w-0 space-y-4">
        <header className="space-y-2">
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
              <h1 className="min-w-0 text-2xl font-bold tracking-tight break-words">{poll.title}</h1>
              <Badge tone="brand">진행 중</Badge>
            </div>
            <PollManageMenu orgId={orgId} poll={poll} />
          </div>
          <p className="text-sm font-medium text-text-brand">
            {formatClock(poll.closesAt)} 마감 · {remaining ?? '곧 결과가 나와요'}
          </p>
          <div className="pt-1 lg:hidden">
            <ResponseProgress poll={poll} />
          </div>
        </header>

        <MenuInput
          orgId={orgId}
          existing={poll.options.map((o) => o.name)}
          pending={addOption.isPending}
          onAdd={(name, link) => addOption.mutateAsync({ name, link })}
        />

        {error && (
          <p role="alert" className="rounded-lg bg-bg-danger-soft px-3 py-2 text-sm text-text-danger">
            {error.message}
          </p>
        )}

        <section className="space-y-2.5" aria-label="메뉴">
          {poll.options.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-border-strong bg-bg-surface px-4 py-8 text-center text-sm text-text-tertiary">
              아직 메뉴가 없어요. 먹고 싶은 메뉴를 먼저 올려 보세요.
            </p>
          ) : (
            <>
              <p className="text-xs font-medium text-text-tertiary">메뉴 {poll.options.length}개 · 카드를 누르면 그 메뉴로 참여해요</p>
              {poll.options.map((option) => (
                <OptionCard
                  key={option.id}
                  option={option}
                  meId={me?.id ?? -1}
                  selected={poll.myOptionId === option.id}
                  onSelect={() => poll.myOptionId !== option.id && vote.mutate(option.id)}
                  onDelete={() => deleteOption.mutate(option.id)}
                  onEditLink={() => setLinkOptionId(option.id)}
                  disabled={deleteOption.isPending}
                />
              ))}
            </>
          )}
        </section>
        <MapLinkModal
          orgId={orgId}
          pollId={poll.id}
          option={poll.options.find((o) => o.id === linkOptionId) ?? null}
          onClose={() => setLinkOptionId(null)}
        />
      </div>

      <aside className="space-y-4">
        <Section title="응답 현황" className="hidden lg:block">
          <ResponseProgress poll={poll} />
        </Section>

        <div className="flex flex-col items-center gap-2">
          <button
            type="button"
            aria-pressed={passedMe}
            onClick={() => !passedMe && vote.mutate(null)}
            className={`w-full rounded-xl border px-5 py-3 text-base font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand ${
              passedMe ? 'border-border-brand bg-bg-brand-soft text-text-brand ring-1 ring-border-brand' : 'border-border-strong bg-bg-surface hover:bg-bg-subtle'
            }`}
          >
            {passedMe ? '✓ 오늘은 패스했어요' : '오늘은 패스'}
          </button>
          {poll.passed.length > 0 && <p className="text-xs text-text-tertiary">패스한 사람: {names(poll.passed)}</p>}
        </div>

        {poll.nonRespondents.length > 0 && (
          <section className="space-y-3 rounded-2xl bg-bg-muted p-4" aria-label="아직 응답하지 않은 사람">
            <h2 className="text-sm font-bold text-text-secondary">아직 응답하지 않은 사람 {poll.nonRespondents.length}명</h2>
            <div className="flex flex-wrap gap-1.5">
              {poll.nonRespondents.map((p) => (
                <PersonChip key={p.userId} person={p} isMe={p.userId === me?.id} tone="plain" />
              ))}
            </div>
          </section>
        )}

        <p className="text-xs text-text-placeholder">3초마다 자동으로 새로 불러와요</p>
      </aside>
    </div>
  )
}

/** 응답 진행 막대. 모바일은 제목 아래, 데스크톱은 사이드의 「응답 현황」 카드에 둔다. */
function ResponseProgress({ poll }: { poll: PollDetail }) {
  const responded = poll.memberCount - poll.nonRespondents.length
  return (
    <>
      <div className="flex justify-between text-xs">
        <span className="font-medium text-text-secondary">
          응답 {responded} / {poll.memberCount}명
        </span>
        <span className="text-text-tertiary">
          패스 {poll.passed.length} · 미응답 {poll.nonRespondents.length}
        </span>
      </div>
      <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-bg-muted" role="progressbar" aria-valuenow={responded} aria-valuemin={0} aria-valuemax={poll.memberCount} aria-label="응답한 멤버">
        <div className="h-full rounded-full bg-bg-brand transition-[width]" style={{ width: `${poll.memberCount ? (responded / poll.memberCount) * 100 : 0}%` }} />
      </div>
    </>
  )
}

function ClosedPoll({ poll }: { poll: PollDetail }) {
  const { data: me } = useMe()
  const teams = confirmedTeams(poll)
  const myTeam = teams.find((t) => t.id === poll.myOptionId)

  return (
    <div className={`flex flex-col gap-4 ${columnsClass}`}>
      <div className="min-w-0 space-y-4">
        <header className="space-y-2">
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight">{poll.title}</h1>
            <Badge tone="neutral">마감</Badge>
          </div>
          <p className="text-sm text-text-secondary">
            {formatClock(poll.closesAt)}에 마감됐어요 · {teams.length > 0 ? `${teams.length}팀으로 나뉘었어요` : '참여한 메뉴가 없어요'}
          </p>
        </header>

        {myTeam ? (
          <section className="rounded-2xl bg-bg-brand p-4 text-text-on-brand">
            <p className="text-xs font-medium">오늘 내 팀</p>
            <p className="mt-0.5 text-lg font-bold">
              {myTeam.name} · {myTeam.voters.length}명
            </p>
            <p className="mt-0.5 text-sm">{myTeam.voters.map((v) => (v.userId === me?.id ? `${v.name}(나)` : v.name)).join(', ')}</p>
          </section>
        ) : (
          <section className="rounded-2xl bg-bg-muted p-4 text-sm text-text-secondary">
            {poll.myResponse === 'PASS' ? '오늘은 패스했어요.' : '이 투표에 응답하지 않았어요.'}
          </section>
        )}

        {teams.length > 0 && (
          <section className="space-y-2.5" aria-label="확정 팀">
            <h2 className="font-bold">확정 팀</h2>
            {teams.map((option) => (
              <OptionCard key={option.id} option={option} meId={me?.id ?? -1} result />
            ))}
          </section>
        )}
      </div>

      <aside className="space-y-4">
        {(poll.passed.length > 0 || poll.nonRespondents.length > 0) && (
          <section className="space-y-2 rounded-2xl bg-bg-muted p-4 text-sm">
            {poll.passed.length > 0 && <Row label={`오늘은 패스 · ${poll.passed.length}명`} value={names(poll.passed)} />}
            {poll.nonRespondents.length > 0 && <Row label={`응답하지 않음 · ${poll.nonRespondents.length}명`} value={names(poll.nonRespondents)} />}
          </section>
        )}

        <p className="text-xs text-text-placeholder">마감된 투표는 메뉴를 추가하거나 바꿀 수 없어요</p>
      </aside>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="shrink-0 font-bold text-text-secondary">{label}</span>
      <span className="text-right break-keep text-text-tertiary">{value}</span>
    </div>
  )
}

function names(people: Person[]) {
  return people.map((p) => p.name).join(', ')
}
