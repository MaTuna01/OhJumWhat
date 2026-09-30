import { useLeaveOrganization } from '../queries/orgs.ts'
import ConfirmDialog from './ConfirmDialog.tsx'

type Props = {
  org: { id: number; name: string; memberCount: number } | null
  onClose: () => void
  onLeft?: () => void
}

/** 조직 탈퇴 확인창. 마지막 멤버라면 조직이 삭제된다고 알린다. */
export default function LeaveOrgDialog({ org, onClose, onLeft }: Props) {
  const leave = useLeaveOrganization()
  const isLastMember = (org?.memberCount ?? 0) <= 1

  const close = () => {
    leave.reset()
    onClose()
  }

  return (
    <ConfirmDialog
      open={org != null}
      onClose={close}
      onConfirm={() =>
        org &&
        leave.mutate(org.id, {
          onSuccess: () => {
            close()
            onLeft?.()
          },
        })
      }
      title={`${org?.name ?? ''}에서 탈퇴할까요?`}
      confirmLabel={isLastMember ? '탈퇴하고 조직 삭제' : '탈퇴'}
      danger
      pending={leave.isPending}
      error={leave.error?.message}
    >
      {isLastMember ? (
        <p>
          마지막 멤버라서 탈퇴하면 <strong className="text-text-danger">조직과 투표 기록이 모두 삭제</strong>돼요.
        </p>
      ) : (
        <p>진행 중인 투표에서 내 선택이 사라져요. 다시 참여하려면 초대 링크가 필요해요.</p>
      )}
    </ConfirmDialog>
  )
}
