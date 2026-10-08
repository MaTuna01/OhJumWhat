import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ActionRow, DangerZone, EmptyRow, ListRow } from '../../components/AdminParts.tsx'
import Button from '../../components/Button.tsx'
import ConfirmDialog from '../../components/ConfirmDialog.tsx'
import { PageLoader, PageMessage, Section } from '../../components/PageState.tsx'
import PollStatusBadge from '../../components/PollStatusBadge.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { ApiError } from '../../lib/api.ts'
import { daysLabel } from '../../lib/daysOfWeek.ts'
import { formatDate, formatDay, formatTimeRange } from '../../lib/time.ts'
import { columnsClass } from '../../lib/ui.ts'
import { type AdminMember, useAdminOrg, useDeleteOrg, useDeleteSchedule, useRemoveMember } from '../../queries/admin.ts'
import type { Schedule } from '../../queries/schedules.ts'

const MEMBER_PREVIEW = 6

/** Figma A05·DA05 조직 상세: 최근 투표, 정기 투표 규칙, 멤버(내보내기), 조직 삭제 */
export default function AdminOrgPage() {
  const orgId = Number(useParams().orgId)
  const detail = useAdminOrg(orgId)
  const navigate = useNavigate()
  const now = useNow(60_000)
  const deleteOrg = useDeleteOrg()
  const removeMember = useRemoveMember(orgId)
  const deleteSchedule = useDeleteSchedule()
  const [showAllMembers, setShowAllMembers] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [kicking, setKicking] = useState<AdminMember | null>(null)
  const [removingSchedule, setRemovingSchedule] = useState<Schedule | null>(null)
  useDocumentTitle(detail.data?.organization.name, '관리자 콘솔')

  const back = (
    <Link to="/admin/orgs" className="inline-block text-sm font-medium text-text-tertiary hover:text-text-secondary">
      ‹ 조직 목록
    </Link>
  )
  if (detail.error instanceof ApiError && detail.error.status === 404) {
    return (
      <div className="space-y-4">
        {back}
        <PageMessage title="조직을 찾을 수 없어요">이미 삭제된 조직일 수 있어요.</PageMessage>
      </div>
    )
  }
  if (detail.isError) return <PageMessage title="조직 정보를 불러오지 못했어요">{detail.error.message}</PageMessage>
  if (detail.isPending) return <PageLoader />

  const { organization: org, members, polls, schedules } = detail.data
  const shownMembers = showAllMembers ? members : members.slice(0, MEMBER_PREVIEW)
  const toList = () => navigate('/admin/orgs', { replace: true })

  return (
    <div className="space-y-4">
      {back}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{org.name}</h1>
        <p className="mt-1 text-sm text-text-tertiary">
          {formatDate(org.createdAt)} 만듦 · 멤버 {org.memberCount}명 · 투표 {org.pollCount}개
        </p>
      </div>

      <div className={`flex flex-col gap-4 ${columnsClass}`}>
        <div className="min-w-0 space-y-4">
          <Section title="최근 투표">
            <ul className="divide-y divide-border-default">
              {polls.length === 0 && <EmptyRow>아직 투표가 없어요.</EmptyRow>}
              {polls.map((p) => (
                <ListRow
                  key={p.id}
                  to={`/admin/polls/${p.id}`}
                  title={p.title}
                  badge={<PollStatusBadge open={p.status === 'OPEN'} />}
                  subtitle={`${formatDay(p.pollDate, now)} · 메뉴 ${p.optionCount}개 · 응답 ${p.responseCount}명`}
                  meta={p.scheduled ? '정기' : undefined}
                />
              ))}
            </ul>
          </Section>

          <Section title="정기 투표 규칙">
            <ul className="divide-y divide-border-default">
              {schedules.length === 0 && <EmptyRow>정기 투표 규칙이 없어요.</EmptyRow>}
              {schedules.map((s) => (
                <ActionRow
                  key={s.id}
                  title={s.name}
                  subtitle={`${daysLabel(s.daysOfWeek)} · ${formatTimeRange(s.openTime, s.closeTime)}`}
                  action={
                    <Button variant="ghost" className="shrink-0 py-1.5" onClick={() => setRemovingSchedule(s)}>
                      삭제
                    </Button>
                  }
                />
              ))}
            </ul>
          </Section>
        </div>

        <aside className="space-y-4">
          <Section title={`멤버 ${members.length}명 · 가입 순`}>
            <ul className="divide-y divide-border-default">
              {shownMembers.map((m) => (
                <ActionRow
                  key={m.userId}
                  person={{ name: m.name, imageUrl: m.profileImageUrl }}
                  title={
                    <Link to={`/admin/users/${m.userId}`} className="hover:text-text-brand">
                      {m.name}
                    </Link>
                  }
                  subtitle={`${m.email} · ${formatDay(m.joinedAt, now)}`}
                  action={
                    <Button variant="ghost" className="shrink-0 py-1.5" onClick={() => setKicking(m)}>
                      내보내기
                    </Button>
                  }
                />
              ))}
            </ul>
            {members.length > MEMBER_PREVIEW && (
              <button type="button" onClick={() => setShowAllMembers((v) => !v)} className="mt-3 text-xs font-medium text-text-brand hover:underline">
                {showAllMembers ? '접기' : `+ ${members.length - MEMBER_PREVIEW}명 더 보기`}
              </button>
            )}
          </Section>

          <DangerZone title="조직 삭제" description="멤버·투표·메뉴·응답·정기 투표가 모두 함께 삭제되고 되돌릴 수 없어요.">
            <Button variant="danger" className="w-full" onClick={() => setConfirmDelete(true)}>
              조직 삭제
            </Button>
          </DangerZone>
        </aside>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        onClose={() => {
          deleteOrg.reset()
          setConfirmDelete(false)
        }}
        onConfirm={() => deleteOrg.mutate(org.id, { onSuccess: toList })}
        title={`${org.name} 조직을 삭제할까요?`}
        confirmLabel="조직 삭제"
        danger
        pending={deleteOrg.isPending}
        error={deleteOrg.error?.message}
      >
        <p>
          멤버 {org.memberCount}명, 투표 {org.pollCount}개와 그 메뉴·응답, 정기 투표 규칙이 모두 삭제돼요. 되돌릴 수 없어요.
        </p>
      </ConfirmDialog>

      <ConfirmDialog
        open={kicking != null}
        onClose={() => {
          removeMember.reset()
          setKicking(null)
        }}
        onConfirm={() =>
          kicking &&
          removeMember.mutate(kicking.userId, {
            onSuccess: ({ organizationDeleted }) => (organizationDeleted ? toList() : setKicking(null)),
          })
        }
        title={`${kicking?.name ?? ''} 님을 내보낼까요?`}
        confirmLabel="내보내기"
        danger
        pending={removeMember.isPending}
        error={removeMember.error?.message}
      >
        <p>진행 중인 투표의 응답은 지워지고 마감된 투표 기록은 남아요. 초대 링크가 있으면 다시 들어올 수 있어요.</p>
        {members.length === 1 && <p className="mt-2 font-medium text-text-danger">마지막 멤버라서 내보내면 조직이 삭제돼요.</p>}
      </ConfirmDialog>

      <ConfirmDialog
        open={removingSchedule != null}
        onClose={() => {
          deleteSchedule.reset()
          setRemovingSchedule(null)
        }}
        onConfirm={() => removingSchedule && deleteSchedule.mutate(removingSchedule.id, { onSuccess: () => setRemovingSchedule(null) })}
        title={`${removingSchedule?.name ?? ''} 규칙을 삭제할까요?`}
        confirmLabel="삭제"
        danger
        pending={deleteSchedule.isPending}
        error={deleteSchedule.error?.message}
      >
        <p>앞으로 이 규칙으로는 투표가 열리지 않아요. 이미 열린 투표는 그대로 남아요.</p>
      </ConfirmDialog>
    </div>
  )
}
