/** Figma AnonymousAvatar: 익명 쪽지를 보낸 사람. 사진·이름 대신 회색 원과 ? */
const sizes = {
  md: 'size-8 text-sm',
  lg: 'size-10 text-base',
}

export default function AnonymousAvatar({ size = 'lg' }: { size?: 'md' | 'lg' }) {
  return (
    <span aria-hidden className={`${sizes[size]} inline-flex shrink-0 items-center justify-center rounded-full bg-bg-muted font-bold text-text-tertiary`}>
      ?
    </span>
  )
}
