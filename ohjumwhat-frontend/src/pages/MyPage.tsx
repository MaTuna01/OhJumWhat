import { useState } from 'react'
import { Link } from 'react-router'
import Avatar from '../components/Avatar.tsx'
import Button from '../components/Button.tsx'
import CreateOrgModal from '../components/CreateOrgModal.tsx'
import LeaveOrgDialog from '../components/LeaveOrgDialog.tsx'
import NoticeBanner from '../components/NoticeBanner.tsx'
import { PageLoader, Section } from '../components/PageState.tsx'
import ProfileModal from '../components/ProfileModal.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { buttonClass, columnsClass } from '../lib/ui.ts'
import { useLogout, useMe } from '../queries/me.ts'
import { type MyOrganization, useMyOrganizations } from '../queries/orgs.ts'

export default function MyPage() {
  const { data: me } = useMe()
  const orgs = useMyOrganizations()
  const logout = useLogout()
  const [creating, setCreating] = useState(false)
  const [leaving, setLeaving] = useState<MyOrganization | null>(null)
  const [editing, setEditing] = useState(false)
  useDocumentTitle('마이페이지')

  if (!me) return null

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">마이페이지</h1>
      {/* 속한 조직이 없으면 조직 홈 대신 이 화면으로 오므로 새 소식 배너를 여기에 둔다. */}
      {orgs.data?.length === 0 && <NoticeBanner />}

      {/* 모바일은 내 정보 → 내 조직 → 로그아웃 순서로 쌓고, 데스크톱은 내 조직을 본문, 나머지를 오른쪽 사이드에 둔다. */}
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
        </Section>

        <Section
          title="내 조직"
          className="lg:col-start-1 lg:row-span-2 lg:row-start-1"
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

        <div className="flex justify-end lg:col-start-2 lg:row-start-2">
          <Button variant="ghost" onClick={() => logout.mutate()} disabled={logout.isPending}>
            로그아웃
          </Button>
        </div>
      </div>

      <CreateOrgModal open={creating} onClose={() => setCreating(false)} />
      <LeaveOrgDialog org={leaving} onClose={() => setLeaving(null)} />
      <ProfileModal me={me} open={editing} onClose={() => setEditing(false)} />
    </div>
  )
}
