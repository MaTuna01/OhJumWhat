import { Link, Navigate, useNavigate, useParams } from 'react-router'
import Button from '../components/Button.tsx'
import { PageLoader, PageMessage } from '../components/PageState.tsx'
import { buttonClass } from '../lib/ui.ts'
import { useInvite, useJoinInvite } from '../queries/orgs.ts'

export default function InvitePage() {
  const { token = '' } = useParams()
  const invite = useInvite(token)
  const join = useJoinInvite(token)
  const navigate = useNavigate()

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

  return (
    <div className="mx-auto max-w-sm py-10 text-center">
      <p className="text-sm text-stone-500">초대를 받았어요</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight">{invite.data.name}</h1>
      <p className="mt-1 text-sm text-stone-500">멤버 {invite.data.memberCount}명</p>
      {join.error && (
        <p role="alert" className="mt-4 text-sm text-red-600">
          {join.error.message}
        </p>
      )}
      <Button
        className="mt-8 w-full py-3 text-base"
        disabled={join.isPending}
        onClick={() =>
          join.mutate(undefined, {
            onSuccess: ({ organizationId }) => navigate(`/orgs/${organizationId}`, { replace: true }),
          })
        }
      >
        참여하기
      </Button>
    </div>
  )
}
