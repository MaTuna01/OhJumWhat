import { type EffectiveRestriction, type Restriction, restrictionNotice } from '../lib/sanctions.ts'

type Props = {
  type: Restriction
  restriction: EffectiveRestriction
  className?: string
}

/**
 * Figma RestrictionNotice: 관리자가 막은 기능 자리에 입력창 대신 둔다(05-X 채팅, 05-X2 메뉴 올리기, 10-X 쪽지, 03-N7 프로필).
 * 무엇이 언제까지 막혔는지와 그래도 할 수 있는 것(투표 참여)을 알려준다.
 */
export default function RestrictionNotice({ type, restriction, className = '' }: Props) {
  const { title, description } = restrictionNotice(type, restriction)
  return (
    <div className={`flex gap-2.5 rounded-xl bg-bg-muted px-3.5 py-3 ${className}`}>
      <span aria-hidden className="text-sm leading-5">
        🔒
      </span>
      <div className="min-w-0">
        <p className="text-sm font-medium text-text-primary">{title}</p>
        <p className="mt-0.5 text-xs text-text-secondary">{description}</p>
      </div>
    </div>
  )
}
