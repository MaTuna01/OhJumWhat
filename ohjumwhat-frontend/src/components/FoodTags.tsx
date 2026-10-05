import Badge from './Badge.tsx'

/** 좋아하는 음식 배지 묶음(Figma Badge Brand). 마이페이지, 멤버 프로필, 관리자 회원 상세에서 쓴다. 없으면 그리지 않는다. */
export default function FoodTags({ tags, className = '' }: { tags: string[]; className?: string }) {
  if (tags.length === 0) return null
  return (
    <ul aria-label="좋아하는 음식" className={`flex flex-wrap gap-1.5 ${className}`}>
      {tags.map((tag) => (
        <li key={tag}>
          <Badge tone="brand">{tag}</Badge>
        </li>
      ))}
    </ul>
  )
}
