import Badge from './Badge.tsx'

/** 투표 상태 배지: 진행 중(Brand) / 마감(Neutral). 조직 홈·지난 투표·투표 상세·관리자가 같이 쓴다 */
export default function PollStatusBadge({ open }: { open: boolean }) {
  return <Badge tone={open ? 'brand' : 'neutral'}>{open ? '진행 중' : '마감'}</Badge>
}
