import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import Avatar from '../components/Avatar.tsx'
import Button from '../components/Button.tsx'
import CreateOrgModal from '../components/CreateOrgModal.tsx'
import FoodTags from '../components/FoodTags.tsx'
import { GuestbookPanel } from '../components/Guestbook.tsx'
import LeaveOrgDialog from '../components/LeaveOrgDialog.tsx'
import NoticeBanner from '../components/NoticeBanner.tsx'
import NotificationSettings from '../components/NotificationSettings.tsx'
import { PageLoader, Section } from '../components/PageState.tsx'
import ProfileDetailList from '../components/ProfileDetailList.tsx'
import ProfileModal from '../components/ProfileModal.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { guestbookTabLabel } from '../lib/guestbook.ts'
import { buttonClass, columnsClass } from '../lib/ui.ts'
import { useGuestbook } from '../queries/guestbook.ts'
import { useLogout, useMe } from '../queries/me.ts'
import { type MyOrganization, useMyOrganizations } from '../queries/orgs.ts'

export default function MyPage() {
  const { data: me } = useMe()
  const orgs = useMyOrganizations()
  const logout = useLogout()
  const [creating, setCreating] = useState(false)
  const [leaving, setLeaving] = useState<MyOrganization | null>(null)
  const [editing, setEditing] = useState(false)
  // 제목 옆 글 수. 아래 방명록 목록의 0쪽과 같은 캐시를 쓴다.
  const guestbook = useGuestbook(me?.id ?? 0, 0, me != null)
  const location = useLocation()
  useDocumentTitle('마이페이지')

  // 프로필 메뉴의 「새 방명록 N」(/me#guestbook)으로 오면 내 조직·방명록을 받아 자리가 잡힌 뒤 방명록으로 내려간다.
  const ready = !orgs.isPending && !guestbook.isPending
  useEffect(() => {
    if (location.hash === '#guestbook' && ready) document.getElementById('guestbook')?.scrollIntoView({ block: 'start' })
  }, [location.key, location.hash, ready])

  if (!me) return null
  const guestbookCount = guestbookTabLabel(guestbook.data?.totalCount)

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">마이페이지</h1>
      {/* 속한 조직이 없으면 조직 홈 대신 이 화면으로 오므로 새 소식 배너를 여기에 둔다. */}
      {orgs.data?.length === 0 && <NoticeBanner />}

      {/* 모바일은 내 정보 → 내 조직 → 방명록 → 알림 → 로그아웃 순서로 쌓고, 데스크톱은 내 조직·방명록을 본문, 나머지를 오른쪽 사이드에 둔다. */}
      <div className={`flex flex-col gap-6 lg:grid-rows-[auto_1fr] lg:gap-y-4 ${columnsClass}`}>
        <Section
          title="내 정보"
          className="lg:col-start-2 lg:row-start-1"
          action={
            <Button variant="secondary" onClick={() => setEditing(true)} className="py-1.5">
              프로필 수정
            </Button>
          }
        >
          <div className="flex items-center gap-3">
            <Avatar name={me.name} imageUrl={me.profileImageUrl} size="lg" />
            <div className="min-w-0">
              <p className="truncate font-medium">{me.name}</p>
              <p className="truncate text-sm text-text-tertiary">{me.email}</p>
              {me.nickname && <p className="truncate text-xs text-text-tertiary">구글 이름 {me.googleName}</p>}
            </div>
          </div>
          {me.bio || me.foodTags.length > 0 ? (
            <div className="mt-4 space-y-2">
              {me.bio && <p className="text-sm break-words text-text-secondary">{me.bio}</p>}
              <FoodTags tags={me.foodTags} />
            </div>
          ) : (
            // 소개 안내(03-N2)는 상세 프로필을 채운 뒤에 보인다. 상세 프로필이 비었으면 그 안내(03-N4)가 먼저다.
            me.details && (
              <FillPrompt
                title="한줄 소개를 써 보세요"
                description="좋아하는 음식과 함께 같은 조직 멤버에게 보여요."
                action="소개 쓰기"
                onClick={() => setEditing(true)}
              />
            )
          )}
          {me.details ? (
            <ProfileDetailList details={me.details} className="mt-4" />
          ) : (
            // 가입 단계가 없어서 처음 가입한 사람·기존 회원 모두 여기서 채우게 안내한다(Figma 03-N4).
            <FillPrompt
              title="상세 프로필을 채워 주세요"
              description="MBTI·퍼스널컬러·취미·나이·직급을 채우면 같은 조직 멤버에게 보여요. 다 채워야 프로필을 저장할 수 있어요."
              action="채우기"
              onClick={() => setEditing(true)}
            />
          )}
        </Section>

        <div className="flex min-w-0 flex-col gap-6 lg:col-start-1 lg:row-span-2 lg:row-start-1 lg:gap-4">
          <Section
            title="내 조직"
            action={
              <Button onClick={() => setCreating(true)} className="py-1.5">
                조직 만들기
              </Button>
            }
          >
            {orgs.isPending ? (
              <PageLoader />
            ) : orgs.isError ? (
              <p className="text-sm text-text-danger">{orgs.error.message}</p>
            ) : orgs.data.length === 0 ? (
              <p className="rounded-xl bg-bg-subtle px-4 py-8 text-center text-sm text-text-tertiary">
                아직 속한 조직이 없어요.
                <br />
                조직을 만들거나 초대 링크로 참여하세요.
              </p>
            ) : (
              <ul className="divide-y divide-border-default">
                {orgs.data.map((org) => (
                  <li key={org.id} className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{org.name}</p>
                      <p className="mt-0.5 flex items-center gap-2 text-sm text-text-tertiary">
                        멤버 {org.memberCount}명
                        {org.hasOpenPollToday && (
                          <span className="rounded-full bg-bg-brand-muted px-2 py-0.5 text-xs font-medium text-text-brand-strong">투표 진행 중</span>
                        )}
                      </p>
                    </div>
                    <Link to={`/orgs/${org.id}`} className={buttonClass('secondary', 'py-1.5')}>
                      들어가기
                    </Link>
                    <Button variant="ghost" onClick={() => setLeaving(org)} className="py-1.5 text-text-tertiary">
                      탈퇴
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </Section>

          {/* Figma 03-N5·D03-N5: 받은 방명록을 모아 보는 곳이라 입력창이 없다(지우기·신고·NEW). */}
          <Section
            id="guestbook"
            title={
              <>
                방명록
                {guestbookCount && <span className="ml-2 text-text-tertiary">{guestbookCount}</span>}
              </>
            }
            className="scroll-mt-20"
          >
            <GuestbookPanel ownerId={me.id} ownerName={me.name} />
          </Section>
        </div>

        {/* 알림 카드(Figma 03-N6·D03-N6)는 푸시가 꺼진 서버에서는 숨으므로 로그아웃과 한 칸에 둔다(빈 줄 간격이 생기지 않게). */}
        <div className="flex flex-col gap-6 lg:col-start-2 lg:row-start-2 lg:gap-4">
          <NotificationSettings userId={me.id} />
          <div className="flex justify-end">
            <Button variant="ghost" onClick={() => logout.mutate()} disabled={logout.isPending}>
              로그아웃
            </Button>
          </div>
        </div>
      </div>

      <CreateOrgModal open={creating} onClose={() => setCreating(false)} />
      <LeaveOrgDialog org={leaving} onClose={() => setLeaving(null)} />
      <ProfileModal me={me} open={editing} onClose={() => setEditing(false)} />
    </div>
  )
}

/** 「내 정보」의 채우기 안내(Figma 03-N2 소개, 03-N4 상세 프로필). 누르면 「프로필 수정」을 연다. */
function FillPrompt({ title, description, action, onClick }: { title: string; description: string; action: string; onClick: () => void }) {
  return (
    <div className="mt-4 flex items-center gap-3 rounded-xl bg-bg-subtle px-3.5 py-3">
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">{title}</p>
        <p className="mt-0.5 text-xs text-text-tertiary">{description}</p>
      </div>
      <button
        type="button"
        onClick={onClick}
        className="shrink-0 rounded text-sm font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
      >
        {action}
      </button>
    </div>
  )
}
