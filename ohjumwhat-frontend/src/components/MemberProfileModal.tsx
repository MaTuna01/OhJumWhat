import { type KeyboardEvent, useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useLetterComposer } from '../hooks/useLetterComposer.ts'
import { guestbookTabLabel } from '../lib/guestbook.ts'
import { useGuestbook } from '../queries/guestbook.ts'
import { useMe } from '../queries/me.ts'
import { type Member, useMembers } from '../queries/orgs.ts'
import type { Person } from '../queries/polls.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import FoodTags from './FoodTags.tsx'
import { GuestbookPanel } from './Guestbook.tsx'
import Modal from './Modal.tsx'
import ProfileDetailList from './ProfileDetailList.tsx'

type Props = {
  orgId: number
  /** 프로필을 볼 사람(없으면 닫힘) */
  person: Person | null
  onClose: () => void
}

/**
 * 멤버 프로필(Figma 07-P·07-P2·07-P4·07-P5): 사진·이름 아래 탭 [프로필 정보] | [방명록 N]. 보기 전용이라 ✕로 닫는다.
 * 방명록에서 다른 사람을 누르면 그 사람의 프로필로 바뀌고, 사람마다 새로 그려져 탭이 「프로필 정보」로 돌아간다.
 */
export default function MemberProfileModal({ orgId, person, onClose }: Props) {
  return (
    <Modal open={person !== null} onClose={onClose} title="프로필" closable>
      {person && <MemberProfile key={person.userId} orgId={orgId} person={person} onClose={onClose} />}
    </Modal>
  )
}

type Tab = 'info' | 'guestbook'

/**
 * 소개는 조직 멤버 목록(조직당 10~20명이라 한 번에 받는다)에서 찾는다. 댓글·채팅·지난 참여자처럼 이제 멤버가 아닌
 * 사람은 연 곳에서 받은 이름·사진만 보여준다(소개·방명록은 지금 같은 조직 멤버에게만 탭으로 보인다).
 */
function MemberProfile({ orgId, person, onClose }: { orgId: number; person: Person; onClose: () => void }) {
  const members = useMembers(orgId)
  const { data: me } = useMe()
  const member = members.data?.find((m) => m.userId === person.userId)
  const name = member?.name ?? person.name
  const isMe = person.userId === me?.id
  const [tab, setTab] = useState<Tab>('info')
  const guestbook = useGuestbook(person.userId, 0, member != null)
  const tabsId = useId()

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
      ) : (
        <>
          <ProfileTabs id={tabsId} tab={tab} onChange={setTab} guestbookCount={guestbook.data?.totalCount} />
          <div role="tabpanel" id={`${tabsId}-${tab}`} aria-labelledby={`${tabsId}-${tab}-tab`} className="w-full">
            {tab === 'info' ? (
              <ProfileInfo orgId={orgId} member={member} isMe={isMe} onClose={onClose} />
            ) : (
              <div className="text-left">
                <GuestbookPanel ownerId={member.userId} ownerName={name} />
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}

const tabs: { value: Tab; label: string }[] = [
  { value: 'info', label: '프로필 정보' },
  { value: 'guestbook', label: '방명록' },
]

/** Figma ProfileTabs: 반씩 나눈 밑줄 탭. 방명록 탭 옆에 글 수(없으면 「방명록」만). 좌우 화살표로도 옮긴다. */
function ProfileTabs({ id, tab, onChange, guestbookCount }: { id: string; tab: Tab; onChange: (tab: Tab) => void; guestbookCount: number | undefined }) {
  const refs = useRef<Partial<Record<Tab, HTMLButtonElement | null>>>({})
  const count = guestbookTabLabel(guestbookCount)

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    const next = tab === 'info' ? 'guestbook' : 'info'
    onChange(next)
    refs.current[next]?.focus()
  }

  return (
    <div role="tablist" aria-label="프로필" className="flex w-full border-b border-border-default">
      {tabs.map(({ value, label }) => (
        <button
          key={value}
          ref={(el) => {
            refs.current[value] = el
          }}
          type="button"
          role="tab"
          id={`${id}-${value}-tab`}
          aria-selected={tab === value}
          // 고른 탭의 내용만 그리므로 그 탭만 내용을 가리킨다.
          aria-controls={tab === value ? `${id}-${value}` : undefined}
          tabIndex={tab === value ? 0 : -1}
          onClick={() => onChange(value)}
          onKeyDown={onKeyDown}
          className={`-mb-px flex-1 border-b-2 px-3 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-brand ${
            tab === value ? 'border-border-brand text-text-brand' : 'border-transparent text-text-secondary hover:text-text-primary'
          }`}
        >
          {label}
          {value === 'guestbook' && count && <span className="ml-1">{count}</span>}
        </button>
      ))}
    </div>
  )
}

/** 「프로필 정보」 탭: 한줄 소개·좋아하는 음식·상세 프로필과 쪽지 보내기(남) 또는 마이페이지 링크(나) */
function ProfileInfo({ orgId, member, isMe, onClose }: { orgId: number; member: Member; isMe: boolean; onClose: () => void }) {
  const { compose } = useLetterComposer()
  const hasIntro = member.bio != null || member.foodTags.length > 0 || member.details != null

  return (
    <div className="flex flex-col items-center gap-3">
      {hasIntro ? (
        <>
          {member.bio && <p className="w-full text-sm break-words text-text-secondary">{member.bio}</p>}
          {member.foodTags.length > 0 && (
            <div className="flex w-full flex-col items-center gap-1.5">
              <p className="text-xs text-text-tertiary">좋아하는 음식</p>
              <FoodTags tags={member.foodTags} className="justify-center" />
            </div>
          )}
          {member.details && <ProfileDetailList details={member.details} className="w-full" />}
        </>
      ) : (
        <p className="text-sm text-text-tertiary">아직 소개가 없어요</p>
      )}
      {isMe ? (
        <Link
          to="/me"
          className="rounded text-sm font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
        >
          마이페이지에서 고치기
        </Link>
      ) : (
        // 지금 같은 조직 멤버에게만(Figma 07-P). 이 모달을 닫고 받는 사람이 정해진 쪽지 쓰기(10-M1)를 연다.
        <Button
          variant="secondary"
          className="mt-1 w-full"
          onClick={() => {
            onClose()
            compose({ kind: 'new', organizationId: orgId, recipient: { userId: member.userId, name: member.name, profileImageUrl: member.profileImageUrl } })
          }}
        >
          쪽지 보내기
        </Button>
      )}
    </div>
  )
}
