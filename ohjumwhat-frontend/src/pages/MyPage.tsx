import { useState } from 'react'
import { Link } from 'react-router'
import Avatar from '../components/Avatar.tsx'
import Button from '../components/Button.tsx'
import CreateOrgModal from '../components/CreateOrgModal.tsx'
import LeaveOrgDialog from '../components/LeaveOrgDialog.tsx'
import { PageLoader, Section } from '../components/PageState.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { buttonClass } from '../lib/ui.ts'
import { useLogout, useMe } from '../queries/me.ts'
import { type MyOrganization, useMyOrganizations } from '../queries/orgs.ts'

export default function MyPage() {
  const { data: me } = useMe()
  const orgs = useMyOrganizations()
  const logout = useLogout()
  const [creating, setCreating] = useState(false)
  const [leaving, setLeaving] = useState<MyOrganization | null>(null)
  useDocumentTitle('마이페이지')

  if (!me) return null

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">마이페이지</h1>

      <Section title="내 정보">
        <div className="flex items-center gap-3">
          <Avatar name={me.name} imageUrl={me.profileImageUrl} />
          <div className="min-w-0">
            <p className="truncate font-medium">{me.name}</p>
            <p className="truncate text-sm text-text-tertiary">{me.email}</p>
          </div>
        </div>
      </Section>

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

      <div className="flex justify-end">
        <Button variant="ghost" onClick={() => logout.mutate()} disabled={logout.isPending}>
          로그아웃
        </Button>
      </div>

      <CreateOrgModal open={creating} onClose={() => setCreating(false)} />
      <LeaveOrgDialog org={leaving} onClose={() => setLeaving(null)} />
    </div>
  )
}
