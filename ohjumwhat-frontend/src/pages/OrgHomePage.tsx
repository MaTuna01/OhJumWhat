import { useState } from 'react'
import { Link } from 'react-router'
import Badge from '../components/Badge.tsx'
import Button from '../components/Button.tsx'
import CreatePollModal from '../components/CreatePollModal.tsx'
import MemberList from '../components/MemberList.tsx'
import { PageLoader, Section } from '../components/PageState.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useNow } from '../hooks/useNow.ts'
import { useOrgId } from '../hooks/useOrgId.ts'
import { daysLabel } from '../lib/daysOfWeek.ts'
import { formatClock, formatRemaining, formatTimeRange } from '../lib/time.ts'
import { columnsClass } from '../lib/ui.ts'
import { type PollSummary, useTodayPolls } from '../queries/polls.ts'
import { useOrganization } from '../queries/orgs.ts'
import { useSchedules } from '../queries/schedules.ts'

/** Figma 04 조직 홈: 오늘 열린 투표 카드와 투표 만들기 */
export default function OrgHomePage() {
  const orgId = useOrgId()
  const polls = useTodayPolls(orgId)
  const [creating, setCreating] = useState(false)
  const { data: org } = useOrganization(orgId)
  useDocumentTitle(org?.name)

  return (
    <div className={`flex flex-col gap-4 ${columnsClass}`}>
      <div className="min-w-0 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-bold">오늘 열린 투표</h2>
          <Button onClick={() => setCreating(true)}>+ 투표 만들기</Button>
        </div>
        {polls.isPending ? (
          <PageLoader />
        ) : polls.isError ? (
          <p className="text-sm text-text-danger">{polls.error.message}</p>
        ) : polls.data.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-border-strong bg-bg-surface px-4 py-10 text-center">
            <p className="font-medium">오늘 열린 투표가 없어요</p>
            <p className="mt-1 text-sm text-text-tertiary">투표를 만들면 바로 열리고, 멤버들이 메뉴를 올릴 수 있어요.</p>
          </div>
        ) : (
          <ul className="space-y-3">
            {/* 진행 중인 투표를 먼저, 같은 상태끼리는 서버 순서(열린 시각 순) */}
            {[...polls.data].sort((a, b) => Number(b.status === 'OPEN') - Number(a.status === 'OPEN')).map((poll) => (
              <li key={poll.id}>
                <PollCard orgId={orgId} poll={poll} />
              </li>
            ))}
          </ul>
        )}
        <ScheduleHint orgId={orgId} />
      </div>
      {/* 데스크톱 사이드: 정기 투표 요약과 멤버. 모바일은 위의 한 줄 요약(ScheduleHint)만 보여준다. */}
      <aside className="hidden space-y-4 lg:block">
        <ScheduleCard orgId={orgId} />
        <MemberList orgId={orgId} limit={6} />
      </aside>
      <CreatePollModal orgId={orgId} open={creating} onClose={() => setCreating(false)} />
    </div>
  )
}

const NO_SCHEDULE = '정기 투표를 만들면 매일 정해진 시간에 투표가 자동으로 열려요.'

/** Figma 04 하단(모바일): 정기 투표 한 줄 요약과 관리 링크 */
function ScheduleHint({ orgId }: { orgId: number }) {
  const { data: schedules } = useSchedules(orgId)
  if (!schedules) return null
  const summary =
    schedules.length === 0
      ? NO_SCHEDULE
      : `정기 투표 · ${schedules.map((s) => `${s.name} ${daysLabel(s.daysOfWeek)} ${formatTimeRange(s.openTime, s.closeTime)}`).join(', ')}`
  return (
    <div className="flex items-center gap-2 rounded-xl bg-bg-muted px-3.5 py-3 lg:hidden">
      <p className="flex-1 text-xs text-text-secondary">{summary}</p>
      <Link to={`/orgs/${orgId}/schedules`} className="shrink-0 text-xs font-medium text-text-brand hover:underline">
        {schedules.length === 0 ? '설정하기 ›' : '관리 ›'}
      </Link>
    </div>
  )
}

/** Figma D04 사이드(데스크톱): 정기 투표 규칙 목록과 관리 링크 */
function ScheduleCard({ orgId }: { orgId: number }) {
  const { data: schedules } = useSchedules(orgId)
  if (!schedules) return null
  return (
    <Section title="정기 투표">
      {schedules.length === 0 ? (
        <p className="text-sm text-text-tertiary">{NO_SCHEDULE}</p>
      ) : (
        <ul className="space-y-3">
          {schedules.map((s) => (
            <li key={s.id}>
              <p className="truncate text-sm font-bold">{s.name}</p>
              <p className="text-sm text-text-secondary">
                {daysLabel(s.daysOfWeek)} · {formatTimeRange(s.openTime, s.closeTime)}
              </p>
            </li>
          ))}
        </ul>
      )}
      <Link to={`/orgs/${orgId}/schedules`} className="mt-3 inline-block text-xs font-medium text-text-brand hover:underline">
        {schedules.length === 0 ? '정기 투표 만들기 ›' : '정기 투표 관리 ›'}
      </Link>
    </Section>
  )
}

function PollCard({ orgId, poll }: { orgId: number; poll: PollSummary }) {
  const now = useNow()
  const open = poll.status === 'OPEN'
  const remaining = open ? formatRemaining(poll.closesAt, now) : null
  const myChoice =
    poll.myResponse === 'OPTION' ? `내 선택: ${poll.myOptionName}` : poll.myResponse === 'PASS' ? '내 선택: 오늘은 패스' : '아직 응답하지 않았어요'

  return (
    <Link
      to={`/orgs/${orgId}/polls/${poll.id}`}
      className="flex flex-col gap-2.5 rounded-2xl border border-border-default bg-bg-surface p-4 transition-colors hover:border-border-brand-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
    >
      <div className="flex items-center gap-2">
        <span className="truncate font-bold">{poll.title}</span>
        <Badge tone={open ? 'brand' : 'neutral'}>{open ? '진행 중' : '마감'}</Badge>
        <span className="ml-auto text-lg text-icon-muted" aria-hidden>
          ›
        </span>
      </div>
      <p className={`text-sm ${open ? 'font-medium text-text-brand' : 'text-text-tertiary'}`}>
        {open ? `${formatClock(poll.closesAt)} 마감${remaining ? ` · ${remaining}` : ''}` : `${formatClock(poll.closesAt)}에 마감됐어요`}
      </p>
      <p className="text-sm text-text-secondary">
        응답 {poll.respondedCount}/{poll.memberCount}명 · {open ? `메뉴 ${poll.optionCount}개` : `${poll.teamCount}팀`}
      </p>
      <span
        className={`self-start rounded-lg px-2.5 py-1 text-xs font-medium ${
          poll.myResponse === 'OPTION' ? 'bg-bg-brand-soft text-text-brand-strong' : 'bg-bg-muted text-text-tertiary'
        }`}
      >
        {myChoice}
      </span>
    </Link>
  )
}
