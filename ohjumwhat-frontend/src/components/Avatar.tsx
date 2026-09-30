type Props = {
  name: string
  imageUrl?: string | null
  /** Figma Avatar: Sm 24(참여자 명단) / Md 32(상단 바·멤버 목록) / Lg 40(마이페이지) */
  size?: 'sm' | 'md' | 'lg'
}

const sizes = {
  sm: 'size-6 text-xs font-medium',
  md: 'size-8 text-sm font-bold',
  lg: 'size-10 text-base font-bold',
}

export default function Avatar({ name, imageUrl, size = 'md' }: Props) {
  if (imageUrl) {
    // 구글 프로필 이미지는 리퍼러가 있으면 거부될 수 있다.
    return <img src={imageUrl} alt="" referrerPolicy="no-referrer" className={`${sizes[size]} shrink-0 rounded-full object-cover`} />
  }
  return (
    <span
      aria-hidden
      className={`${sizes[size]} inline-flex shrink-0 items-center justify-center rounded-full bg-bg-brand-muted text-text-brand-strong`}
    >
      {name.slice(0, 1)}
    </span>
  )
}
