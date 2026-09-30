import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router'
import Avatar from '../components/Avatar.tsx'
import Button from '../components/Button.tsx'
import InviteLinkField from '../components/InviteLinkField.tsx'
import LeaveOrgDialog from '../components/LeaveOrgDialog.tsx'
import { PageLoader, Section } from '../components/PageState.tsx'
import { useOrgId } from '../hooks/useOrgId.ts'
import { inputClass } from '../lib/ui.ts'
import { useMe } from '../queries/me.ts'
import { type Organization, useMembers, useOrganization, useRenameOrganization } from '../queries/orgs.ts'

export default function OrgSettingsPage() {
  const orgId = useOrgId()
  const { data: org } = useOrganization(orgId)
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)

  if (!org) return null

  return (
    <div className="space-y-6">
      <Section title="초대 링크">
        <p className="mb-3 text-sm text-stone-500">이 링크를 받은 사람은 누구나 조직에 참여할 수 있어요.</p>
        <InviteLinkField token={org.inviteToken} />
      </Section>

      <Section title="조직 이름">
        {/* 이름이 바뀌면(다른 멤버가 바꾼 경우 포함) 입력창을 새 값으로 초기화한다. */}
        <RenameForm key={org.name} org={org} />
      </Section>

      <MemberList orgId={orgId} />

      <Section title="조직 탈퇴">
        <div className="flex items-center justify-between gap-4">
          <p className="text-sm text-stone-500">
            {org.memberCount <= 1 ? '마지막 멤버라서 탈퇴하면 조직이 삭제돼요.' : '탈퇴해도 조직과 다른 멤버의 기록은 남아요.'}
          </p>
          <Button variant="secondary" className="shrink-0 text-red-600" onClick={() => setLeaving(true)}>
            탈퇴
          </Button>
        </div>
      </Section>

      <LeaveOrgDialog org={leaving ? org : null} onClose={() => setLeaving(false)} onLeft={() => navigate('/me', { replace: true })} />
    </div>
  )
}

function RenameForm({ org }: { org: Organization }) {
  const [name, setName] = useState(org.name)
  const rename = useRenameOrganization(org.id)
  const trimmed = name.trim()

  const submit = (e: FormEvent) => {
    e.preventDefault()
    rename.mutate(trimmed)
  }

  return (
    <form onSubmit={submit}>
      <div className="flex gap-2">
        <input
          aria-label="조직 이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={50}
          className={inputClass}
        />
        <Button type="submit" className="shrink-0" disabled={!trimmed || trimmed === org.name || rename.isPending}>
          저장
        </Button>
      </div>
      {rename.error && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {rename.error.message}
        </p>
      )}
    </form>
  )
}

function MemberList({ orgId }: { orgId: number }) {
  const members = useMembers(orgId)
  const { data: me } = useMe()

  return (
    <Section title={members.data ? `멤버 ${members.data.length}명` : '멤버'}>
      {members.isPending ? (
        <PageLoader />
      ) : members.isError ? (
        <p className="text-sm text-red-600">{members.error.message}</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {members.data.map((member) => (
            <li key={member.userId} className="flex items-center gap-2.5">
              <Avatar name={member.name} imageUrl={member.profileImageUrl} />
              <span className="truncate text-sm">{member.name}</span>
              {member.userId === me?.id && (
                <span className="rounded-full bg-stone-100 px-2 py-0.5 text-xs text-stone-500">나</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}
