import { type ReactNode, useState } from 'react'
import { ProfileViewerContext, useProfileViewer } from '../hooks/useProfileViewer.ts'
import type { Person } from '../queries/polls.ts'
import MemberProfileModal from './MemberProfileModal.tsx'

/**
 * 조직 화면(OrgLayout) 전체에서 멤버 프로필 모달을 하나만 둔다. 멤버 목록·참여자 칩·댓글·채팅이 같은 모달을 연다.
 * 모달이 React 트리에서 채팅 시트(<dialog>) 밖에 있어서, 닫혀도 close 이벤트가 시트로 올라가 시트까지 닫지 않는다.
 */
export function ProfileViewerProvider({ orgId, children }: { orgId: number; children: ReactNode }) {
  const [person, setPerson] = useState<Person | null>(null)
  return (
    <ProfileViewerContext value={setPerson}>
      {children}
      <MemberProfileModal orgId={orgId} person={person} onClose={() => setPerson(null)} />
    </ProfileViewerContext>
  )
}

type ButtonProps = {
  /** null(탈퇴한 사용자)이면 누를 수 없다. */
  person: Person | null
  className?: string
  /**
   * false면 탭으로 가지 않고 보조 기술에서도 숨긴다. 이름 버튼 옆의 아바타처럼 같은 사람을 여는 두 번째 버튼에 쓴다.
   */
  focusable?: boolean
  children: ReactNode
}

/**
 * 누르면 그 사람의 프로필을 여는 버튼. 프로필을 열 수 없으면(조직 화면 밖, 탈퇴한 사용자) 그냥 글자로 보여준다.
 * 메뉴 카드(role="button") 안에서도 쓰므로 클릭·키 입력을 카드로 올려 보내지 않는다(누르면 투표가 바뀌지 않게).
 */
export function ProfileButton({ person, className = '', focusable = true, children }: ButtonProps) {
  const openProfile = useProfileViewer()
  if (!openProfile || !person) return <span className={className}>{children}</span>
  return (
    <button
      type="button"
      aria-haspopup="dialog"
      tabIndex={focusable ? undefined : -1}
      aria-hidden={focusable ? undefined : true}
      onClick={(e) => {
        e.stopPropagation()
        openProfile(person)
      }}
      onKeyDown={(e) => e.stopPropagation()}
      className={`cursor-pointer text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand ${className}`}
    >
      {children}
    </button>
  )
}
