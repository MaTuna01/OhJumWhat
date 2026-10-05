import { Link } from 'react-router'
import { ListRow, StatCard } from '../../components/AdminParts.tsx'
import Badge from '../../components/Badge.tsx'
import { PageLoader, Section } from '../../components/PageState.tsx'
import { useDocumentTitle } from '../../hooks/useDocumentTitle.ts'
import { useNow } from '../../hooks/useNow.ts'
import { formatDay } from '../../lib/time.ts'
import { useAdminOrgs, useAdminStats, useAdminUsers } from '../../queries/admin.ts'

const RECENT = 5

/** Figma A01·DA01 관리자 콘솔 개요: 숫자 카드(처리할 신고는 신고 탭으로), 최근 가입한 회원, 최근 만든 조직 */
export default function AdminOverviewPage() {
  const stats = useAdminStats()
  const users = useAdminUsers('')
  const orgs = useAdminOrgs('')
  const now = useNow(60_000)
  useDocumentTitle('관리자 콘솔')

  return (
    <div className="space-y-6">
      {stats.isPending ? (
        <PageLoader />
      ) : stats.isError ? (
        <p className="text-sm text-text-danger">{stats.error.message}</p>
      ) : (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-7">
          <StatCard label="전체 회원" value={stats.data.userCount} />
          <StatCard label="조직" value={stats.data.organizationCount} />
          <StatCard label="오늘 투표" value={stats.data.todayPollCount} />
          <StatCard label="진행 중 투표" value={stats.data.openPollCount} />
          <StatCard label="최근 7일 가입" value={stats.data.newUserCount} />
          <StatCard label="차단한 계정" value={stats.data.blockedCount} />
          <Link to="/admin/reports" className="rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand">
            <StatCard label="처리할 신고" value={stats.data.openReportCount} hint="신고 탭 ›" />
          </Link>
        </div>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-2">
        <Section title="최근 가입한 회원" action={<MoreLink to="/admin/users" />}>
          {users.data && (
            <ul className="divide-y divide-border-default">
              {users.data.slice(0, RECENT).map((u) => (
                <ListRow
                  key={u.id}
                  to={`/admin/users/${u.id}`}
                  person={{ name: u.name, imageUrl: u.profileImageUrl }}
                  title={u.name}
                  badge={u.role === 'ADMIN' && <Badge tone="brand">관리자</Badge>}
                  subtitle={u.email}
                  meta={formatDay(u.createdAt, now)}
                />
              ))}
            </ul>
          )}
        </Section>
        <Section title="최근 만든 조직" action={<MoreLink to="/admin/orgs" />}>
          {orgs.data &&
            (orgs.data.length === 0 ? (
              <p className="text-sm text-text-tertiary">아직 만든 조직이 없어요.</p>
            ) : (
              <ul className="divide-y divide-border-default">
                {orgs.data.slice(0, RECENT).map((o) => (
                  <ListRow key={o.id} to={`/admin/orgs/${o.id}`} title={o.name} subtitle={`멤버 ${o.memberCount}명 · 투표 ${o.pollCount}개`} meta={formatDay(o.createdAt, now)} />
                ))}
              </ul>
            ))}
        </Section>
      </div>
    </div>
  )
}

function MoreLink({ to }: { to: string }) {
  return (
    <Link to={to} className="text-xs font-medium text-text-brand hover:underline">
      전체 보기 ›
    </Link>
  )
}
