import { personalColorLabel } from '../lib/profileDetails.ts'
import type { ProfileDetails } from '../queries/me.ts'
import Badge from './Badge.tsx'

/**
 * 상세 프로필 보기(Figma ProfileDetailList): 직급·나이·MBTI·퍼스널컬러·취미(Badge Neutral, 좋아하는 음식은 Brand).
 * 마이페이지 「내 정보」, 멤버 프로필 모달, 관리자 회원 상세에서 쓴다. 채우지 않은 사람은 쓰는 쪽에서 그리지 않는다.
 */
export default function ProfileDetailList({ details, className = '' }: { details: ProfileDetails; className?: string }) {
  const rows = [
    ['직급', details.jobTitle],
    ['나이', `${details.age}세`],
    ['MBTI', details.mbti],
    ['퍼스널컬러', personalColorLabel(details.personalColor)],
  ]
  return (
    <dl className={`space-y-2 rounded-xl bg-bg-subtle px-3.5 py-3 text-left text-sm ${className}`}>
      {rows.map(([label, value]) => (
        <div key={label} className="flex items-baseline gap-3">
          <dt className="w-16 shrink-0 text-xs text-text-tertiary">{label}</dt>
          <dd className="min-w-0 break-words">{value}</dd>
        </div>
      ))}
      <div className="flex items-baseline gap-3">
        <dt className="w-16 shrink-0 text-xs text-text-tertiary">취미</dt>
        <dd className="min-w-0">
          <ul aria-label="취미" className="flex flex-wrap gap-1">
            {details.hobbies.map((hobby) => (
              <li key={hobby}>
                <Badge tone="neutral">{hobby}</Badge>
              </li>
            ))}
          </ul>
        </dd>
      </div>
    </dl>
  )
}
