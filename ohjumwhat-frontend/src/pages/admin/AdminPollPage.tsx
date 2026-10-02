import { Fragment, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ActionRow, DangerZone, EmptyRow } from '../../components/AdminParts.tsx'
import Badge from '../../components/Badge.tsx'
import Button from '../../components/Button.tsx'
import ConfirmDialog from '../../components/ConfirmDialog.tsx'
import { CommentList, CommentRow } from '../../components/OptionComments.tsx'
import { PageLoader, PageMessage, Section } from '../../components/PageState.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { ApiError } from '../../lib/api.ts'
import { withJosa } from '../../lib/josa.ts'
import { formatClock } from '../../lib/time.ts'
import { columnsClass } from '../../lib/ui.ts'
import { commentsPanelId } from '../../lib/comments.ts'
import { useAdminOptionComments, useAdminOrg, useAdminPoll, useDeleteMenuComment, useDeleteMenuOption, useDeletePoll } from '../../queries/admin.ts'
import type { MenuComment } from '../../queries/comments.ts'
import type { Person, PollOption } from '../../queries/polls.ts'

/**
 * Figma A06·DA06 투표 관리: 메뉴(참여자가 있어도) 강제 삭제, 투표 삭제(정기 규칙 함께 삭제 선택).
 * 메뉴의 「💬 댓글 N」을 펼쳐 부적절한 댓글을 마감과 상관없이 지운다(A06-K).
 */
export default function AdminPollPage() {
  const pollId = Number(useParams().pollId)
  const poll = useAdminPoll(pollId)
  const org = useAdminOrg(poll.data?.organizationId)
  const navigate = useNavigate()
  const deletePoll = useDeletePoll()
  const deleteOption = useDeleteMenuOption()
  const [removingOption, setRemovingOption] = useState<PollOption | null>(null)
  const [openComments, setOpenComments] = useState<number | null>(null)
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
                <Fragment key={o.id}>
                  <ActionRow
                    title={`${o.name} · ${o.voters.length}명`}
                    subtitle={`${o.voters.length > 0 ? names(o.voters) : '참여자 없음'} · ${withJosa(o.createdBy?.name ?? '탈퇴한 사용자', '이/가')} 추가`}
                    extra={
                      o.commentCount > 0 && (
                        <button
                          type="button"
                          aria-expanded={openComments === o.id}
                          aria-controls={openComments === o.id ? commentsPanelId(o.id) : undefined}
                          onClick={() => setOpenComments(openComments === o.id ? null : o.id)}
                          className="mt-1 text-xs font-medium text-text-secondary hover:text-text-primary focus-visible:outline-2 focus-visible:outline-border-brand"
                        >
                          <span aria-hidden>💬</span> 댓글 {o.commentCount} <span aria-hidden>{openComments === o.id ? '▴' : '▾'}</span>
                        </button>
                      )
                    }
                    action={
                      <Button variant="ghost" className="shrink-0 py-1.5" onClick={() => setRemovingOption(o)}>
                        삭제
                      </Button>
                    }
                  />
                  {openComments === o.id && o.commentCount > 0 && (
                    <li className="pb-3">
                      <AdminComments option={o} />
                    </li>
                  )}
                </Fragment>
              ))}
            </ul>
            {p.options.length > 0 && (
              <p className="mt-3 text-xs text-text-tertiary">
                참여자가 있는 메뉴도 지울 수 있어요. 그 메뉴에 참여한 사람은 미응답이 돼요. 메뉴를 지우면 댓글도 함께 지워져요.
              </p>
            )}
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

/** 메뉴의 댓글(A06-K): 마감과 상관없이 지운다. */
function AdminComments({ option }: { option: PollOption }) {
  const comments = useAdminOptionComments(option.id)
  const remove = useDeleteMenuComment()
  const [removing, setRemoving] = useState<MenuComment | null>(null)
  const close = () => {
    remove.reset()
    setRemoving(null)
  }
  return (
    <section id={commentsPanelId(option.id)} aria-label={`${option.name} 댓글`} className="rounded-xl border border-border-default bg-bg-subtle p-3">
      <CommentList
        comments={comments.data}
        loading={comments.isPending}
        error={comments.error?.message}
        empty="댓글이 없어요"
        render={(comment) => (
          <CommentRow
            comment={comment}
            actions={
              <button type="button" onClick={() => setRemoving(comment)} className="font-medium text-text-danger hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
                삭제
              </button>
            }
          />
        )}
      />
      <ConfirmDialog
        open={removing != null}
        onClose={close}
        onConfirm={() => removing && remove.mutate(removing.id, { onSuccess: close })}
        title="댓글을 삭제할까요?"
        confirmLabel="삭제"
        danger
        pending={remove.isPending}
        error={remove.error?.message}
      >
        <p className="break-words">
          {removing?.author?.name ?? '탈퇴한 사용자'}: 「{removing?.body}」
        </p>
      </ConfirmDialog>
    </section>
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
