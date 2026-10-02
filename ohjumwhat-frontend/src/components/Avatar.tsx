import { useState } from 'react'

type Props = {
  name: string
  imageUrl?: string | null
  /** Figma Avatar: Sm 24(참여자 명단) / Md 32(상단 바·멤버 목록) / Lg 40(마이페이지·관리자) / Xl 80(프로필 수정 미리보기) */
  size?: 'sm' | 'md' | 'lg' | 'xl'
}

const sizes = {
  sm: 'size-6 text-xs font-medium',
  md: 'size-8 text-sm font-bold',
  lg: 'size-10 text-base font-bold',
  xl: 'size-20 text-3xl font-bold',
}

/** 프로필 사진(올린 사진, 없으면 구글 사진). 사진이 없거나 불러오지 못하면 이름의 첫 글자를 보여준다. */
export default function Avatar({ name, imageUrl, size = 'md' }: Props) {
  // 지워진 사진·만료된 구글 주소처럼 불러오지 못한 주소. 주소가 바뀌면 다시 시도한다.
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  if (imageUrl && imageUrl !== failedUrl) {
    // 구글 프로필 이미지는 리퍼러가 있으면 거부될 수 있다.
    return (
      <img
        src={imageUrl}
        alt=""
        referrerPolicy="no-referrer"
        onError={() => setFailedUrl(imageUrl)}
        className={`${sizes[size]} shrink-0 rounded-full object-cover`}
      />
    )
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
