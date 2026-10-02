import type { Me, PhotoChange } from '../queries/me.ts'

/** 「프로필 수정」(Figma 03-M2)에서 고르고 아직 저장하지 않은 사진 */
export type PendingPhoto = { kind: 'none' } | { kind: 'upload'; blob: Blob; dataUrl: string } | { kind: 'reset' }

export const NICKNAME_MAX_LENGTH = 20

/** 고를 수 있는 원본 사진의 최대 크기. 브라우저에서 512px로 줄여 보내므로 휴대폰 사진도 넉넉히 받는다. */
export const PHOTO_MAX_BYTES = 20 * 1024 * 1024

/** 미리보기 사진: 방금 고른 사진 → 되돌릴 구글 사진 → 지금 사진 */
export function previewPhoto(me: Me, pending: PendingPhoto): string | null {
  if (pending.kind === 'upload') return pending.dataUrl
  if (pending.kind === 'reset') return me.googleProfileImageUrl
  return me.profileImageUrl
}

/** 「구글 사진으로 되돌리기」는 올린 사진이나 방금 고른 사진이 있을 때만 보인다. */
export function canRevertPhoto(me: Me, pending: PendingPhoto): boolean {
  return pending.kind === 'upload' || (pending.kind === 'none' && me.customPhoto)
}

/** 되돌리기: 올린 사진이 있으면 지우기로, 방금 고른 사진만 있으면 고르기 전으로 */
export function revertPhoto(me: Me): PendingPhoto {
  return me.customPhoto ? { kind: 'reset' } : { kind: 'none' }
}

/** 저장할 사진 변경(없으면 null) */
export function photoChange(pending: PendingPhoto): PhotoChange | null {
  if (pending.kind === 'upload') return { kind: 'upload', blob: pending.blob }
  if (pending.kind === 'reset') return { kind: 'reset' }
  return null
}

/**
 * 이름 입력을 별명으로. 비우면 null(구글 이름)이고, 화면 이름과 같으면 바뀌지 않은 것으로 본다.
 * 글자 수는 서버(Nicknames)처럼 코드 포인트로 센다.
 */
export function nicknameChange(me: Me, input: string) {
  const trimmed = input.trim().replace(/\s+/g, ' ')
  const nickname = trimmed || null
  const changed = nickname === null ? me.nickname !== null : nickname !== me.name
  return { nickname, changed, length: [...trimmed].length }
}

/** 고른 파일을 쓸 수 없으면 안내 문구 */
export function photoFileError(file: { type: string; size: number }): string | null {
  if (!file.type.startsWith('image/')) return '사진 파일만 올릴 수 있어요.'
  if (file.size > PHOTO_MAX_BYTES) return '20MB보다 작은 사진을 골라 주세요.'
  return null
}
