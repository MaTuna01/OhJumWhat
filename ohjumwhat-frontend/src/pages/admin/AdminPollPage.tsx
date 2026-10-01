import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ActionRow, DangerZone, EmptyRow } from '../../components/AdminParts.tsx'
import Badge from '../../components/Badge.tsx'
import Button from '../../components/Button.tsx'
import ConfirmDialog from '../../components/ConfirmDialog.tsx'
import { PageLoader, PageMessage, Section } from '../../components/PageState.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { ApiError } from '../../lib/api.ts'
import { formatClock } from '../../lib/time.ts'
import { columnsClass } from '../../lib/ui.ts'
import { useAdminOrg, useAdminPoll, useDeleteMenuOption, useDeletePoll } from '../../queries/admin.ts'
import type { Person, PollOption } from '../../queries/polls.ts'

/** Figma A06·DA06 투표 관리: 메뉴(참여자가 있어도) 강제 삭제, 투표 삭제(정기 규칙 함께 삭제 선택) */
export default function AdminPollPage() {
  const pollId = Number(useParams().pollId)
  const poll = useAdminPoll(pollId)
  const org = useAdminOrg(poll.data?.organizationId)
  const navigate = useNavigate()
  const deletePoll = useDeletePoll()
  const deleteOption = useDeleteMenuOption()
  const [removingOption, setRemovingOption] = useState<PollOption | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [withSchedule, setWithSchedule] = useState(true)
  useDocumentTitle(poll.data?.title, '관리자 콘솔')

  if (poll.error instanceof ApiError && poll.error.status === 404) {
    return (
      <PageMessage title="투표를 찾을 수 없어요">
        <p>이미 삭제된 투표일 수 있어요.</p>
        <Link to="/admin/orgs" className="mt-2 inline-block font-medium text-text-brand">
          조직 목록으로
        </Link>
      </PageMessage>
    )
  }
  if (poll.isError) return <PageMessage title="투표를 불러오지 못했어요">{poll.error.message}</PageMessage>
  if (poll.isPending) return <PageLoader />

  const p = poll.data
  const open = p.status === 'OPEN'
  const responded = p.memberCount - p.nonRespondents.length
  const orgPath = `/admin/orgs/${p.organizationId}`

  return (
    <div className="space-y-4">
      <Link to={orgPath} className="inline-block text-sm font-medium text-text-tertiary hover:text-text-secondary">
        ‹ {org.data?.organization.name ?? '조직'}
      </Link>
      <header className="space-y-2">
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold tracking-tight">{p.title}</h1>
          <Badge tone={open ? 'brand' : 'neutral'}>{open ? '진행 중' : '마감'}</Badge>
        </div>
        <p className="text-sm text-text-tertiary">
          {formatClock(p.closesAt)} 마감{p.scheduled && ' · 정기 투표'} · 응답 {responded} / {p.memberCount}명
        </p>
      </header>

      <div className={`flex flex-col gap-4 ${columnsClass}`}>
        <div className="min-w-0">
          <Section title={`메뉴 ${p.options.length}개`}>
            <ul className="divide-y divide-border-default">
              {p.options.length === 0 && <EmptyRow>올라온 메뉴가 없어요.</EmptyRow>}
              {p.options.map((o) => (
                <ActionRow
                  key={o.id}
                  title={`${o.name} · ${o.voters.length}명`}
                  subtitle={`${o.voters.length > 0 ? names(o.voters) : '참여자 없음'} · ${o.createdBy?.name ?? '탈퇴한 사용자'}가 추가`}
                  action={
                    <Button variant="ghost" className="shrink-0 py-1.5" onClick={() => setRemovingOption(o)}>
                      삭제
                    </Button>
                  }
                />
              ))}
            </ul>
            {p.options.length > 0 && <p className="mt-3 text-xs text-text-tertiary">참여자가 있는 메뉴도 지울 수 있어요. 그 메뉴에 참여한 사람은 미응답이 돼요.</p>}
          </Section>
        </div>

        <aside className="space-y-4">
          {(p.passed.length > 0 || p.nonRespondents.length > 0) && (
            <section className="space-y-2 rounded-2xl bg-bg-muted p-4 text-sm">
              {p.passed.length > 0 && <Row label={`오늘은 패스 · ${p.passed.length}명`} value={names(p.passed)} />}
              {p.nonRespondents.length > 0 && <Row label={`응답하지 않음 · ${p.nonRespondents.length}명`} value={names(p.nonRespondents)} />}
            </section>
          )}
          <DangerZone title="투표 삭제" description="메뉴와 응답이 모두 함께 삭제돼요.">
            {p.scheduled && (
              <label className="flex items-start gap-2 text-sm text-text-secondary">
                <input type="checkbox" checked={withSchedule} onChange={(e) => setWithSchedule(e.target.checked)} className="mt-0.5 size-4 accent-bg-brand" />
                <span>정기 투표 규칙도 함께 삭제{open && ' (진행 중인 정기 투표는 규칙을 남기면 1분 안에 다시 열려요)'}</span>
              </label>
            )}
            <Button variant="danger" className="w-full" onClick={() => setConfirmDelete(true)}>
              투표 삭제
            </Button>
          </DangerZone>
        </aside>
      </div>

      <ConfirmDialog
        open={removingOption != null}
        onClose={() => {
          deleteOption.reset()
          setRemovingOption(null)
        }}
        onConfirm={() => removingOption && deleteOption.mutate(removingOption.id, { onSuccess: () => setRemovingOption(null) })}
        title={`${removingOption?.name ?? ''} 메뉴를 삭제할까요?`}
        confirmLabel="삭제"
        danger
        pending={deleteOption.isPending}
        error={deleteOption.error?.message}
      >
        <p>{removingOption && removingOption.voters.length > 0 ? `참여한 ${removingOption.voters.length}명은 미응답이 돼요.` : '아직 참여한 사람이 없어요.'}</p>
      </ConfirmDialog>

      <ConfirmDialog
        open={confirmDelete}
        onClose={() => {
          deletePoll.reset()
          setConfirmDelete(false)
        }}
        onConfirm={() => deletePoll.mutate({ pollId: p.id, withSchedule: p.scheduled && withSchedule }, { onSuccess: () => navigate(orgPath, { replace: true }) })}
        title={`${p.title} 투표를 삭제할까요?`}
        confirmLabel="투표 삭제"
        danger
        pending={deletePoll.isPending}
        error={deletePoll.error?.message}
      >
        <p>
          메뉴 {p.options.length}개와 응답 {responded}개가 모두 삭제돼요.{p.scheduled && withSchedule && ' 정기 투표 규칙도 함께 삭제돼요.'}
        </p>
      </ConfirmDialog>
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
  return people.map((person) => person.name).join(', ')
}
