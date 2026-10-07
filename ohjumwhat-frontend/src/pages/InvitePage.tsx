import { Link, Navigate, useNavigate, useParams } from 'react-router'
import Button from '../components/Button.tsx'
import { PageLoader, PageMessage } from '../components/PageState.tsx'
import RestrictionNotice from '../components/RestrictionNotice.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useRestriction } from '../hooks/useRestriction.ts'
import { buttonClass } from '../lib/ui.ts'
import { useInvite, useJoinInvite } from '../queries/orgs.ts'

export default function InvitePage() {
  const { token = '' } = useParams()
  const invite = useInvite(token)
  const join = useJoinInvite(token)
  const navigate = useNavigate()
  // 활동이 정지된 사람은 초대로 참여할 수 없다(참여 버튼 대신 안내).
  const suspended = useRestriction('SUSPEND')
  useDocumentTitle(invite.data ? `${invite.data.name} 초대` : '초대')

  if (invite.isPending) return <PageLoader />
  if (invite.isError) {
    return (
      <PageMessage title={invite.error.message}>
        <p>링크가 잘못됐거나 조직이 삭제됐을 수 있어요.</p>
        <Link to="/me" className={buttonClass('secondary', 'mt-4')}>
          마이페이지로
        </Link>
      </PageMessage>
    )
  }
  if (invite.data.alreadyMember && !join.isPending) {
    return <Navigate to={`/orgs/${invite.data.organizationId}`} replace />
  }

  // Figma 02(모바일)·D02(데스크톱): 가운데 초대 카드
  return (
    <div className="mx-auto max-w-sm pt-12 lg:max-w-[25rem] lg:pt-16">
      <div className="rounded-2xl border border-border-default bg-bg-surface px-6 pt-8 pb-6 text-center">
        <span
          aria-hidden
          className="mx-auto flex size-14 items-center justify-center rounded-xl bg-bg-brand-muted text-2xl font-bold text-text-brand-strong"
        >
          {invite.data.name.slice(0, 1)}
        </span>
        <p className="mt-3 text-sm text-text-tertiary">초대를 받았어요</p>
        <h1 className="mt-1 text-2xl font-bold tracking-tight">{invite.data.name}</h1>
        <p className="mt-1 text-sm text-text-tertiary">멤버 {invite.data.memberCount}명</p>
        {join.error && (
          <p role="alert" className="mt-4 text-sm text-text-danger">
            {join.error.message}
          </p>
        )}
        {suspended ? (
          <RestrictionNotice type="SUSPEND" restriction={suspended} className="mt-6 text-left" />
        ) : (
          <>
            <Button
              className="mt-6 w-full py-3 text-base"
              disabled={join.isPending}
              onClick={() =>
                join.mutate(undefined, {
                  onSuccess: ({ organizationId }) => navigate(`/orgs/${organizationId}`, { replace: true }),
                })
              }
            >
              참여하기
            </Button>
            <p className="mt-3 text-xs text-text-tertiary">참여하면 이 조직의 투표를 보고 메뉴를 올릴 수 있어요.</p>
          </>
        )}
      </div>
    </div>
  )
}
