type Props = {
  /** 심볼 크기(px) */
  size?: number
  /** 워드마크 "오점왓"을 함께 보여줄지 */
  wordmark?: boolean
  className?: string
}

/** Figma 디자인 시스템의 로고: 밥그릇 위로 피어오르는 물음표 심볼 + 워드마크 */
export default function Logo({ size = 28, wordmark = true, className = '' }: Props) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <LogoMark size={size} />
      {wordmark && (
        <span className="font-black tracking-tight text-text-brand" style={{ fontSize: Math.round(size * 0.68) }}>
          오점왓
        </span>
      )}
    </span>
  )
}

export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg viewBox="0 0 96 96" width={size} height={size} aria-hidden className="shrink-0">
      <rect width="96" height="96" rx="25" className="fill-bg-brand" />
      <path d="M18 64a30 30 0 0 0 60 0z" className="fill-text-on-brand" />
      <rect x="14" y="60" width="68" height="7" rx="3.5" className="fill-text-on-brand" />
      <text x="48" y="47" textAnchor="middle" fontSize="40" fontWeight="900" className="fill-text-on-brand">
        ?
      </text>
    </svg>
  )
}
