import type { Me, PersonalColor, ProfileDetails } from '../queries/me.ts'
import { charCount, pendingTags, singleLine, type TagRule, type TagState } from './profile.ts'

/**
 * 상세 프로필(Notion 「22. 프로필 항목 추가」, Figma 03-M2 「상세 프로필」): MBTI·퍼스널컬러·취미·나이·직급.
 * 다섯 항목 모두 필수이고 오류 문구는 기획서 그대로다. 서버 ProfileDetails와 같은 규칙이다.
 */

/** MBTI 네 축. 축마다 둘 중 하나를 고른다. */
export const MBTI_AXES = [
  [
    { letter: 'E', label: '외향' },
    { letter: 'I', label: '내향' },
  ],
  [
    { letter: 'S', label: '감각' },
    { letter: 'N', label: '직관' },
  ],
  [
    { letter: 'T', label: '사고' },
    { letter: 'F', label: '감정' },
  ],
  [
    { letter: 'J', label: '판단' },
    { letter: 'P', label: '인식' },
  ],
] as const

export const PERSONAL_COLORS: { value: PersonalColor; label: string }[] = [
  { value: 'SPRING_WARM', label: '봄 웜' },
  { value: 'SUMMER_COOL', label: '여름 쿨' },
  { value: 'AUTUMN_WARM', label: '가을 웜' },
  { value: 'WINTER_COOL', label: '겨울 쿨' },
]

export function personalColorLabel(value: PersonalColor): string {
  return PERSONAL_COLORS.find((c) => c.value === value)?.label ?? value
}

/** 누르면 바로 취미로 더하는 추천(이미 더한 것은 보이지 않는다) */
export const HOBBY_SUGGESTIONS = ['운동', '독서', '영화', '음악', '게임', '여행', '요리', '카페', '등산', '사진']

export const HOBBY_MAX_LENGTH = 10
export const HOBBIES_MAX = 5
export const AGE_MIN = 1
export const AGE_MAX = 120
export const JOB_TITLE_MAX_LENGTH = 15

export const HOBBY_RULE: TagRule = {
  maxLength: HOBBY_MAX_LENGTH,
  max: HOBBIES_MAX,
  lengthMessage: `취미는 ${HOBBY_MAX_LENGTH}자 이하로 입력해 주세요.`,
  tooManyMessage: `취미는 ${HOBBIES_MAX}개까지 적을 수 있어요.`,
  duplicateMessage: '이미 적은 취미예요.',
}

export type DetailField = 'mbti' | 'personalColor' | 'hobbies' | 'age' | 'jobTitle'

/** 화면 순서(서버도 이 순서로 확인한다) */
export const DETAIL_FIELDS: DetailField[] = ['mbti', 'personalColor', 'hobbies', 'age', 'jobTitle']

export const DETAIL_LABELS: Record<DetailField, string> = {
  mbti: 'MBTI',
  personalColor: '퍼스널컬러',
  hobbies: '취미',
  age: '나이',
  jobTitle: '직급',
}

/** 기획서의 오류 문구 */
export const DETAIL_MESSAGES: Record<DetailField, string> = {
  mbti: '4가지 성향을 모두 선택해 주세요.',
  personalColor: '퍼스널컬러를 선택해 주세요.',
  hobbies: '최소 1개 이상의 취미를 등록해 주세요.',
  age: `올바른 나이를 입력해 주세요. (${AGE_MIN}~${AGE_MAX}세)`,
  jobTitle: `직급을 입력해 주세요. (최대 ${JOB_TITLE_MAX_LENGTH}자)`,
}

/** 입력 중인 상세 프로필. mbti는 축마다 고른 글자(아직 안 골랐으면 null), personalColor는 안 골랐으면 '' */
export type DetailsForm = {
  mbti: (string | null)[]
  personalColor: PersonalColor | ''
  hobbies: TagState
  age: string
  jobTitle: string
}

/** 지금 상세 프로필로 폼을 채운다(채우지 않았으면 빈 폼). */
export function initialDetailsForm(details: ProfileDetails | null): DetailsForm {
  return {
    mbti: details ? [...details.mbti] : [null, null, null, null],
    personalColor: details?.personalColor ?? '',
    hobbies: { tags: details?.hobbies ?? [], draft: '', error: null },
    age: details ? String(details.age) : '',
    jobTitle: details?.jobTitle ?? '',
  }
}

/** 나이 입력에서 숫자만 남긴다(세 자리까지). 숫자 키패드가 없는 기기에서 붙여 넣어도 숫자만 들어간다. */
export function ageInput(raw: string): string {
  return raw.replace(/\D/g, '').slice(0, 3)
}

/** 나이 입력을 숫자로. 1~120의 정수가 아니면 null */
export function parseAge(input: string): number | null {
  if (!/^\d+$/.test(input.trim())) return null
  const age = Number(input.trim())
  return age >= AGE_MIN && age <= AGE_MAX ? age : null
}

/** 다섯 항목 모두 비었는지(취미는 아직 더하지 않은 글까지). 관리자 「프로필 수정」은 비어 있으면 상세 프로필을 건너뛴다. */
export function isBlankDetailsForm(form: DetailsForm): boolean {
  return (
    form.mbti.every((letter) => letter === null) &&
    !form.personalColor &&
    form.hobbies.tags.length === 0 &&
    !form.hobbies.draft.trim() &&
    !form.age.trim() &&
    !form.jobTitle.trim()
  )
}

/** 항목별 오류 문구(문제가 없는 항목은 빠진다). 취미는 아직 더하지 않은 글까지 넣어 확인한다. */
export function detailErrors(form: DetailsForm): Partial<Record<DetailField, string>> {
  const errors: Partial<Record<DetailField, string>> = {}
  if (form.mbti.some((letter) => letter === null)) errors.mbti = DETAIL_MESSAGES.mbti
  if (!form.personalColor) errors.personalColor = DETAIL_MESSAGES.personalColor
  const hobbies = pendingTags(form.hobbies, HOBBY_RULE)
  if (hobbies.error) errors.hobbies = hobbies.error
  else if (hobbies.tags.length === 0) errors.hobbies = DETAIL_MESSAGES.hobbies
  if (parseAge(form.age) === null) errors.age = DETAIL_MESSAGES.age
  const jobTitle = singleLine(form.jobTitle)
  if (!jobTitle || charCount(jobTitle) > JOB_TITLE_MAX_LENGTH) errors.jobTitle = DETAIL_MESSAGES.jobTitle
  return errors
}

/** 오류가 남은 항목 이름(「MBTI·나이」). 「저장」 위 안내에 쓴다. */
export function missingDetailLabels(errors: Partial<Record<DetailField, string>>): string {
  return DETAIL_FIELDS.filter((field) => errors[field])
    .map((field) => DETAIL_LABELS[field])
    .join('·')
}

/**
 * 저장할 상세 프로필과 지금과 달라졌는지. 오류가 있으면 details는 null이다.
 * 비교는 서버가 정리한 값과 같은 기준(공백 정리, 취미 순서)으로 한다.
 */
export function detailsChange(current: Pick<Me, 'details'>, form: DetailsForm): { details: ProfileDetails | null; changed: boolean } {
  if (Object.keys(detailErrors(form)).length > 0) return { details: null, changed: true }
  const details: ProfileDetails = {
    mbti: form.mbti.join(''),
    personalColor: form.personalColor as PersonalColor,
    hobbies: pendingTags(form.hobbies, HOBBY_RULE).tags,
    age: parseAge(form.age) as number,
    jobTitle: singleLine(form.jobTitle),
  }
  const now = current.details
  const changed =
    now === null ||
    now.mbti !== details.mbti ||
    now.personalColor !== details.personalColor ||
    now.age !== details.age ||
    now.jobTitle !== details.jobTitle ||
    now.hobbies.length !== details.hobbies.length ||
    now.hobbies.some((hobby, i) => hobby !== details.hobbies[i])
  return { details, changed }
}
