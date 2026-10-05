import { Link } from 'react-router'
import { useMe } from '../queries/me.ts'
import { useMembers } from '../queries/orgs.ts'
import type { Person } from '../queries/polls.ts'
import Avatar from './Avatar.tsx'
import FoodTags from './FoodTags.tsx'
import Modal from './Modal.tsx'

type Props = {
  orgId: number
  /** 프로필을 볼 사람(없으면 닫힘) */
  person: Person | null
  onClose: () => void
}

/** 멤버 프로필(Figma 07-P·07-P2): 사진·이름·한줄 소개·좋아하는 음식. 보기 전용이라 ✕로 닫는다. */
export default function MemberProfileModal({ orgId, person, onClose }: Props) {
  return (
    <Modal open={person !== null} onClose={onClose} title="프로필" closable>
      {person && <MemberProfile orgId={orgId} person={person} />}
    </Modal>
  )
}

/**
 * 소개는 조직 멤버 목록(조직당 10~20명이라 한 번에 받는다)에서 찾는다. 댓글·채팅·지난 참여자처럼 이제 멤버가 아닌
 * 사람은 연 곳에서 받은 이름·사진만 보여준다(소개는 같은 조직 멤버에게만 보인다).
 */
function MemberProfile({ orgId, person }: { orgId: number; person: Person }) {
  const members = useMembers(orgId)
  const { data: me } = useMe()
  const member = members.data?.find((m) => m.userId === person.userId)
  const name = member?.name ?? person.name
  const isMe = person.userId === me?.id
  const hasIntro = member != null && (member.bio != null || member.foodTags.length > 0)

  return (
    <div className="flex flex-col items-center gap-3 pb-1 text-center">
      <Avatar name={name} imageUrl={member ? member.profileImageUrl : person.profileImageUrl} size="xl" />
      <div className="flex max-w-full items-center gap-1.5">
        <p className="truncate text-lg font-bold">{name}</p>
        {isMe && <span className="shrink-0 rounded-full bg-bg-muted px-2 py-0.5 text-xs text-text-tertiary">나</span>}
      </div>
      {members.isPending ? (
        <p role="status" className="text-sm text-text-tertiary">
          불러오는 중…
        </p>
      ) : members.isError ? (
        <p className="text-sm text-text-danger">소개를 불러오지 못했어요.</p>
      ) : !member ? (
        <p className="text-sm text-text-tertiary">지금은 이 조직 멤버가 아니에요</p>
      ) : hasIntro ? (
        <>
          {member.bio && <p className="w-full text-sm break-words text-text-secondary">{member.bio}</p>}
          {member.foodTags.length > 0 && (
            <div className="flex w-full flex-col items-center gap-1.5">
              <p className="text-xs text-text-tertiary">좋아하는 음식</p>
              <FoodTags tags={member.foodTags} className="justify-center" />
            </div>
          )}
        </>
      ) : (
        <p className="text-sm text-text-tertiary">아직 소개가 없어요</p>
      )}
      {isMe && member && (
        <Link
          to="/me"
          className="rounded text-sm font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
        >
          마이페이지에서 고치기
        </Link>
      )}
    </div>
  )
}
