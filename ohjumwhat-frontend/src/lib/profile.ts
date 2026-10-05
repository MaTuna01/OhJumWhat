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

export const BIO_MAX_LENGTH = 50
export const FOOD_TAG_MAX_LENGTH = 10
export const FOOD_TAGS_MAX = 3

/** 앞뒤 공백을 지우고 연속 공백은 하나로(서버 ProfileIntro와 같다) */
function singleLine(text: string) {
  return text.trim().replace(/\s+/g, ' ')
}

/** 글자 수는 서버처럼 코드 포인트로 센다(이모지도 한 글자). */
function charCount(text: string) {
  return [...text].length
}

/** 음식 태그 하나 정리: 앞뒤 공백과 앞의 #을 지우고 공백을 하나로. 비면 빈 문자열 */
export function normalizeFoodTag(raw: string): string {
  return singleLine(raw.trim().replace(/^#+/, ''))
}

/** 띄어쓰기·대소문자만 다른 음식은 같은 음식이다(「김치찌개 = 김치 찌개」). */
function foodTagKey(tag: string) {
  return tag.replace(/ /g, '').toLowerCase()
}

/**
 * 입력한 글을 태그로 더한다. 쉼표로 나눠 여러 개를 한 번에 붙여 넣을 수도 있다.
 * 너무 길거나 3개가 넘으면 거기서 멈추고 error를 준다. 이미 있는 음식은 건너뛰고, 더한 것 없이 건너뛰기만 했으면
 * duplicate다(저장은 막지 않고 안내만 한다).
 */
export function addFoodTags(tags: string[], raw: string): { tags: string[]; error: string | null; duplicate: boolean } {
  let next = tags
  let skipped = false
  for (const part of raw.split(/[,，]/)) {
    const tag = normalizeFoodTag(part)
    if (!tag) continue
    if (charCount(tag) > FOOD_TAG_MAX_LENGTH) {
      return { tags: next, error: `음식 이름은 ${FOOD_TAG_MAX_LENGTH}자 이하로 입력해 주세요.`, duplicate: false }
    }
    if (next.some((t) => foodTagKey(t) === foodTagKey(tag))) {
      skipped = true
      continue
    }
    if (next.length >= FOOD_TAGS_MAX) {
      return { tags: next, error: `좋아하는 음식은 ${FOOD_TAGS_MAX}개까지 적을 수 있어요.`, duplicate: false }
    }
    next = [...next, tag]
  }
  return { tags: next, error: null, duplicate: skipped && next === tags }
}

/** 한줄 소개·좋아하는 음식 입력을 저장할 값으로. 소개를 비우면 null이고, 둘 다 지금과 같으면 바뀌지 않은 것이다. */
export function introChange(me: Me, bioInput: string, foodTags: string[]) {
  const trimmed = singleLine(bioInput)
  const bio = trimmed || null
  const tagsChanged = foodTags.length !== me.foodTags.length || foodTags.some((tag, i) => tag !== me.foodTags[i])
  return { bio, foodTags, changed: bio !== me.bio || tagsChanged, bioLength: charCount(trimmed) }
}
