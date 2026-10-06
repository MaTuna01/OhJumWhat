import { type ReactNode, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import Badge from '../components/Badge.tsx'
import Button from '../components/Button.tsx'
import ChatPanel from '../components/ChatPanel.tsx'
import ChatSheet from '../components/ChatSheet.tsx'
import MenuInput from '../components/MenuInput.tsx'
import OptionCard from '../components/OptionCard.tsx'
import OptionComments from '../components/OptionComments.tsx'
import { PageLoader, PageMessage, Section } from '../components/PageState.tsx'
import PersonChip from '../components/PersonChip.tsx'
import PlaceModal from '../components/PlaceModal.tsx'
import PollManageMenu from '../components/PollManageMenu.tsx'
import PollPlacesMap from '../components/PollPlacesMap.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { DESKTOP_QUERY, useMediaQuery } from '../hooks/useMediaQuery.ts'
import { useNow } from '../hooks/useNow.ts'
import { useOrgId } from '../hooks/useOrgId.ts'
import { usePollChatSocket } from '../hooks/usePollChatSocket.ts'
import { ApiError } from '../lib/api.ts'
import { NO_PLACE, naverPlaceSearchUrl } from '../lib/place.ts'
import { confirmedTeams } from '../lib/pollDetail.ts'
import { copyText, resultText } from '../lib/share.ts'
import { formatClock, formatRemaining } from '../lib/time.ts'
import { buttonClass, columnsClass } from '../lib/ui.ts'
import { useClientConfig, useMapKey } from '../queries/config.ts'
import { useMe } from '../queries/me.ts'
import { useOrganization } from '../queries/orgs.ts'
import { distancesByOption, placeOptionIds, usePlaces } from '../queries/places.ts'
import { useRefreshRankingOnClose } from '../queries/ranking.ts'
import {
  type PollDetail,
  type PollOption,
  type Person,
  useAddOption,
  useChangePlace,
  useDeleteOption,
  usePollDetail,
  useVote,
} from '../queries/polls.ts'

/**
 * Figma 05 투표 상세(진행 중) / 05b(마감 결과). 진행 중에는 3초마다 다시 불러온다.
 * 데스크톱(D05·D05b)은 메뉴를 본문에, 응답 현황·패스·미응답을 오른쪽 사이드에 둔다.
 * 진행 중일 때 제목 옆 ⋯(05-A)에서 수정·지금 마감·삭제를 한다.
 * 식당 위치를 찾은 메뉴가 있으면 지도(05-G·D05-G)를 모바일은 메뉴 목록 위에(진행 중에는 접어서), 데스크톱은 사이드 맨 위에 둔다.
 * 메뉴 카드의 「💬 댓글 N」을 누르면 카드 아래로 댓글이 펼쳐진다(05-K, 마감된 투표는 읽기만 05b-K).
 * 펼친 메뉴는 마감돼도 그대로 두고 읽기 전용으로 바꾼다(쓰던 입력은 닫힌다).
 * 투표 채팅(05-C·D05-C)은 모바일은 하단 고정 버튼 → 채팅 시트, 데스크톱은 사이드 열(응답 현황 아래)에 둔다.
 * 받기 연결(WebSocket)은 결과 모드로 바뀌어도 끊기지 않게 이 화면이 들고 있다(마감 1시간 뒤까지).
 */
export default function PollDetailPage() {
  const orgId = useOrgId()
  const pollId = Number(useParams().pollId)
  const poll = usePollDetail(orgId, pollId)
  const { data: org } = useOrganization(orgId)
  const comments = useCommentsToggle()
  const connection = usePollChatSocket(pollId, poll.data?.chatClosesAt)
  const isDesktop = useMediaQuery(DESKTOP_QUERY)
  useDocumentTitle(poll.data?.title, org?.name)
  useRefreshRankingOnClose(orgId, poll.data?.status)

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

  const chat = isDesktop ? <ChatPanel pollId={pollId} chatClosesAt={poll.data.chatClosesAt} connection={connection} variant="side" /> : null

  return (
    <div className="space-y-4 pb-20 lg:pb-0">
      <Link to={`/orgs/${orgId}`} className="inline-block text-sm font-medium text-text-tertiary hover:text-text-secondary">
        ‹ {org?.name ?? '조직'} 투표 목록
      </Link>
      {poll.data.status === 'OPEN' ? (
        <OpenPoll orgId={orgId} poll={poll.data} comments={comments} chat={chat} />
      ) : (
        <ClosedPoll orgId={orgId} poll={poll.data} comments={comments} chat={chat} />
      )}
      {!isDesktop && <ChatSheet pollId={pollId} chatClosesAt={poll.data.chatClosesAt} connection={connection} />}
    </div>
  )
}

/** 댓글을 펼친 메뉴들 */
type CommentsToggle = { open: ReadonlySet<number>; toggle: (optionId: number) => void }

function useCommentsToggle(): CommentsToggle {
  const [open, setOpen] = useState<ReadonlySet<number>>(() => new Set())
  const toggle = (optionId: number) =>
    setOpen((prev) => {
      const next = new Set(prev)
      if (!next.delete(optionId)) next.add(optionId)
      return next
    })
  return { open, toggle }
}

/**
 * 투표 지도와 카드의 거리. 위치는 폴링과 따로, 메뉴의 식당이 바뀔 때만 다시 받는다.
 * 지도는 화면 크기에 맞는 한 곳(모바일: 메뉴 위, 데스크톱: 사이드)에만 그린다.
 */
function usePollMap(orgId: number, poll: PollDetail, options: PollOption[], collapsible: boolean) {
  const { data: config } = useClientConfig()
  const mapKey = useMapKey()
  const isDesktop = useMediaQuery(DESKTOP_QUERY)
  const { data: org } = useOrganization(orgId)
  const places = usePlaces(orgId, org?.officeAddress ?? null, options, Boolean(config?.placeSearch) && placeOptionIds(options).length > 0)
  const [highlighted, setHighlighted] = useState<number | null>(null)

  useEffect(() => {
    if (highlighted == null) return
    const timer = setTimeout(() => setHighlighted(null), 2000)
    return () => clearTimeout(timer)
  }, [highlighted])

  const focusOption = (optionId: number) => {
    document.getElementById(`option-${optionId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setHighlighted(optionId)
  }

  // 카카오 식당은 이름을 저장하지 않으므로, 다시 찾은 이름과 그 이름으로 만든 네이버 지도 링크를 쓴다.
  const resolved = new Map<number, { name: string; link: string }>()
  for (const spot of places.data?.places ?? []) {
    if (spot.name) resolved.set(spot.optionId, { name: spot.name, link: naverPlaceSearchUrl(spot.name, spot.roadAddress) })
  }

  const distances = distancesByOption(places.data)

  const map =
    mapKey && places.data ? (
      <PollPlacesMap
        keyId={mapKey}
        title={poll.title}
        orgName={org?.name ?? '조직'}
        places={places.data}
        distances={distances}
        resolved={resolved}
        options={options}
        myOptionId={poll.myOptionId}
        collapsible={collapsible && !isDesktop}
        defaultOpen={!collapsible || isDesktop}
        onSelect={focusOption}
      />
    ) : null

  return {
    distances,
    resolved,
    highlighted,
    mobileMap: isDesktop ? null : map,
    desktopMap: isDesktop ? map : null,
  }
}

function OpenPoll({ orgId, poll, comments, chat }: { orgId: number; poll: PollDetail; comments: CommentsToggle; chat: ReactNode }) {
  const { data: me } = useMe()
  const now = useNow(10_000)
  const vote = useVote(orgId, poll.id, me)
  const addOption = useAddOption(orgId, poll.id)
  const deleteOption = useDeleteOption(orgId, poll.id)
  const changePlace = useChangePlace(orgId, poll.id)
  const [placeOptionId, setPlaceOptionId] = useState<number | null>(null)
  const editing = poll.options.find((o) => o.id === placeOptionId) ?? null
  const closeEdit = () => {
    changePlace.reset()
    setPlaceOptionId(null)
  }
  const remaining = formatRemaining(poll.closesAt, now)
  const error = vote.error ?? addOption.error ?? deleteOption.error
  const passedMe = poll.myResponse === 'PASS'
  const { distances, resolved, highlighted, mobileMap, desktopMap } = usePollMap(orgId, poll, poll.options, true)

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
          onAdd={(name, place) => addOption.mutateAsync({ name, ...place })}
        />

        {error && (
          <p role="alert" className="rounded-lg bg-bg-danger-soft px-3 py-2 text-sm text-text-danger">
            {error.message}
          </p>
        )}

        {mobileMap}

        <section className="space-y-2.5" aria-label="메뉴">
          {poll.options.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-border-strong bg-bg-surface px-4 py-8 text-center text-sm text-text-tertiary">
              아직 메뉴가 없어요. 먹고 싶은 메뉴를 먼저 올려 보세요.
            </p>
          ) : (
            <>
              <p className="text-xs font-medium text-text-tertiary">메뉴 {poll.options.length}개 · 누르면 참여, 한 번 더 누르면 취소해요</p>
              {poll.options.map((option) => (
                <div key={option.id} className="space-y-1.5">
                  <OptionCard
                    option={option}
                    meId={me?.id ?? -1}
                    selected={poll.myOptionId === option.id}
                    onSelect={() => vote.mutate(poll.myOptionId === option.id ? 'NONE' : option.id)}
                    onDelete={() => deleteOption.mutate(option.id)}
                    onEditLink={() => setPlaceOptionId(option.id)}
                    disabled={deleteOption.isPending}
                    distance={distances.get(option.id)}
                    resolved={resolved.get(option.id)}
                    highlighted={highlighted === option.id}
                    commentsOpen={comments.open.has(option.id)}
                    onToggleComments={() => comments.toggle(option.id)}
                  />
                  {comments.open.has(option.id) && <OptionComments pollId={poll.id} optionId={option.id} menuName={option.name} readOnly={false} />}
                </div>
              ))}
            </>
          )}
        </section>
        <PlaceModal
          orgId={orgId}
          open={editing != null}
          title={`${editing?.name ?? ''} 식당`}
          menuName={editing?.name ?? ''}
          current={
            editing
              ? {
                  link: { link: editing.kakaoPlaceId ? '' : (editing.link ?? ''), name: editing.placeName ?? '', address: editing.placeAddress ?? '' },
                  kakaoPlaceId: editing.kakaoPlaceId,
                  placeQuery: editing.placeQuery,
                }
              : undefined
          }
          confirmLabel="저장"
          pending={changePlace.isPending}
          error={changePlace.error?.message}
          onConfirm={(picked) => editing && changePlace.mutate({ optionId: editing.id, ...picked.input }, { onSuccess: closeEdit })}
          onRemove={editing?.link ? () => changePlace.mutate({ optionId: editing.id, ...NO_PLACE }, { onSuccess: closeEdit }) : undefined}
          onClose={closeEdit}
        />
      </div>

      <aside className="space-y-4">
        {desktopMap}

        <Section title="응답 현황" className="hidden lg:block">
          <ResponseProgress poll={poll} />
        </Section>

        {chat}

        <div className="flex flex-col items-center gap-2">
          <button
            type="button"
            aria-pressed={passedMe}
            onClick={() => vote.mutate(passedMe ? 'NONE' : 'PASS')}
            className={`w-full rounded-xl border px-5 py-3 text-base font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand ${
              passedMe ? 'border-border-brand bg-bg-brand-soft text-text-brand ring-1 ring-border-brand' : 'border-border-strong bg-bg-surface hover:bg-bg-subtle'
            }`}
          >
            {passedMe ? (
              <>
                ✓ 오늘은 패스했어요 <span className="text-sm font-normal whitespace-nowrap">· 다시 누르면 취소</span>
              </>
            ) : (
              '오늘은 패스'
            )}
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

function ClosedPoll({ orgId, poll, comments, chat }: { orgId: number; poll: PollDetail; comments: CommentsToggle; chat: ReactNode }) {
  const { data: me } = useMe()
  const teams = confirmedTeams(poll)
  const myTeam = teams.find((t) => t.id === poll.myOptionId)
  const adoption = poll.adoption
  const { distances, resolved, highlighted, mobileMap, desktopMap } = usePollMap(orgId, poll, teams, false)

  return (
    <div className={`flex flex-col gap-4 ${columnsClass}`}>
      <div className="min-w-0 space-y-4">
        <header className="space-y-2">
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
              <h1 className="min-w-0 text-2xl font-bold tracking-tight break-words">{poll.title}</h1>
              <Badge tone="neutral">마감</Badge>
            </div>
            <CopyResultButton poll={poll} resolved={resolved} />
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

        {mobileMap}

        {teams.length > 0 && (
          <section className="space-y-2.5" aria-label="확정 팀">
            <h2 className="font-bold">확정 팀</h2>
            {adoption && adoption.optionId == null && adoption.participants > 0 && (
              // Figma 05b-W2: 메뉴를 고른 사람이 마감 당시 인원의 30%보다 적으면 랭킹에 넣지 않는다.
              <p className="rounded-xl bg-bg-muted px-3 py-2.5 text-xs text-text-secondary">
                메뉴를 고른 사람이 {adoption.participants}명이라 조직 인원 {adoption.headcount}명의 30%({adoption.required}명)보다 적어요. 이번
                투표는 랭킹에 들어가지 않아요.
              </p>
            )}
            {teams.map((option) => (
              <div key={option.id} className="space-y-1.5">
                <OptionCard
                  option={option}
                  meId={me?.id ?? -1}
                  result
                  adopted={option.id === adoption?.optionId}
                  distance={distances.get(option.id)}
                  resolved={resolved.get(option.id)}
                  highlighted={highlighted === option.id}
                  commentsOpen={comments.open.has(option.id)}
                  onToggleComments={() => comments.toggle(option.id)}
                />
                {comments.open.has(option.id) && option.commentCount > 0 && (
                  <OptionComments pollId={poll.id} optionId={option.id} menuName={option.name} readOnly />
                )}
              </div>
            ))}
            {adoption?.optionId != null && (
              <p className="text-xs text-text-tertiary">
                <span aria-hidden>👑 </span>채택: 메뉴를 고른 사람이 가장 많은 메뉴예요(같으면 먼저 제안한 메뉴).{' '}
                {teams.find((t) => t.id === adoption.optionId)?.createdBy
                  ? '제안한 사람에게 랭킹 1회가 쌓여요.'
                  : '제안한 사람이 탈퇴해서 랭킹에는 들어가지 않아요.'}
              </p>
            )}
          </section>
        )}
      </div>

      <aside className="space-y-4">
        {desktopMap}

        {chat}

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

/** 결과 복사(Figma 05b-S): 메신저에 붙일 결과 글을 복사하고 2초 동안 "✓ 복사했어요"를 보여준다. */
function CopyResultButton({ poll, resolved }: { poll: PollDetail; resolved: Map<number, { name: string; link: string }> }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  useEffect(() => {
    if (state === 'idle') return
    const timer = setTimeout(() => setState('idle'), 2000)
    return () => clearTimeout(timer)
  }, [state])

  const copy = async () => {
    const url = `${window.location.origin}/orgs/${poll.organizationId}/polls/${poll.id}`
    setState((await copyText(resultText(poll, url, resolved))) ? 'copied' : 'failed')
  }

  return (
    <Button variant="secondary" onClick={copy} className="shrink-0 py-1.5" aria-live="polite">
      {state === 'copied' ? '✓ 복사했어요' : state === 'failed' ? '복사하지 못했어요' : '결과 복사'}
    </Button>
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
