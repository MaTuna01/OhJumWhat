import { Link } from 'react-router'
import { useMe } from '../queries/me.ts'
import { useMembers } from '../queries/orgs.ts'
import { useMonthlyMakers } from '../queries/ranking.ts'
import Avatar from './Avatar.tsx'
import { PageLoader, Section } from './PageState.tsx'
import { ProfileButton } from './ProfileViewer.tsx'
import { MakerBadge } from './RankingBadges.tsx'

type Props = {
  orgId: number
  /** 앞에서부터 이만큼만 보여주고, 나머지는 "+ N명 더"(조직 설정 링크)로 줄인다. */
  limit?: number
  className?: string
}

/**
 * Figma 「멤버 N명」 카드. 조직 설정 화면과 데스크톱 조직 홈의 사이드에서 쓴다. 사람을 누르면 프로필(07-P)을 연다.
 * 지난달 메뉴 메이커 1위는 이름 옆에 🏅(MakerBadge Compact, D04-R)를 붙인다.
 */
export default function MemberList({ orgId, limit, className }: Props) {
  const members = useMembers(orgId)
  const { data: me } = useMe()
  const makers = useMonthlyMakers(orgId)
  const shown = limit ? members.data?.slice(0, limit) : members.data
  const rest = (members.data?.length ?? 0) - (shown?.length ?? 0)

  return (
    <Section title={members.data ? `멤버 ${members.data.length}명` : '멤버'} className={className}>
      {members.isPending ? (
        <PageLoader />
      ) : members.isError ? (
        <p className="text-sm text-text-danger">{members.error.message}</p>
      ) : (
        <>
          <ul className={`grid gap-3 ${limit ? 'grid-cols-2' : 'sm:grid-cols-2'}`}>
            {shown?.map((member) => (
              <li key={member.userId} className="min-w-0">
                <ProfileButton person={member} className="-m-1 flex w-[calc(100%+0.5rem)] min-w-0 items-center gap-2.5 rounded-lg p-1 hover:bg-bg-subtle">
                  <Avatar name={member.name} imageUrl={member.profileImageUrl} />
                  <span className="truncate text-sm">{member.name}</span>
                  {makers.has(member.userId) && <MakerBadge compact />}
                  {member.userId === me?.id && (
                    <span className="shrink-0 rounded-full bg-bg-muted px-2 py-0.5 text-xs text-text-tertiary">나</span>
                  )}
                </ProfileButton>
              </li>
            ))}
          </ul>
          {rest > 0 && (
            <Link
              to={`/orgs/${orgId}/settings`}
              className="mt-3 inline-block rounded text-xs font-medium text-text-tertiary hover:text-text-secondary focus-visible:outline-2 focus-visible:outline-border-brand"
            >
              + {rest}명 더
            </Link>
          )}
        </>
      )}
    </Section>
  )
}
