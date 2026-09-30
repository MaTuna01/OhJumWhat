type Props = {
  name: string
  imageUrl?: string | null
  size?: 'sm' | 'md'
}

const sizes = {
  sm: 'size-6 text-xs',
  md: 'size-8 text-sm',
}

export default function Avatar({ name, imageUrl, size = 'md' }: Props) {
  if (imageUrl) {
    // 구글 프로필 이미지는 리퍼러가 있으면 거부될 수 있다.
    return <img src={imageUrl} alt="" referrerPolicy="no-referrer" className={`${sizes[size]} shrink-0 rounded-full object-cover`} />
  }
  return (
    <span
      aria-hidden
      className={`${sizes[size]} inline-flex shrink-0 items-center justify-center rounded-full bg-orange-100 font-semibold text-orange-700`}
    >
      {name.slice(0, 1)}
    </span>
  )
}
