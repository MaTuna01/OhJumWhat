import { describe, expect, it } from 'vitest'
import type { Me, ProfileDetails } from '../queries/me.ts'
import { addTags, pendingTags, pickTag } from './profile.ts'
import {
  ageInput,
  type DetailsForm,
  detailErrors,
  detailsChange,
  HOBBY_RULE,
  initialDetailsForm,
  isBlankDetailsForm,
  missingDetailLabels,
  parseAge,
  personalColorLabel,
} from './profileDetails.ts'

const details: ProfileDetails = { mbti: 'ENFP', personalColor: 'AUTUMN_WARM', hobbies: ['등산', '독서'], age: 32, jobTitle: '개발팀 매니저' }

const me: Me = {
  id: 1,
  name: '김철수',
  nickname: null,
  googleName: '김철수',
  email: 'kim@example.com',
  profileImageUrl: null,
  googleProfileImageUrl: null,
  customPhoto: false,
  bio: null,
  foodTags: [],
  details,
  lastVisitedOrgId: 1,
  admin: false,
}

const filled: DetailsForm = initialDetailsForm(details)

describe('initialDetailsForm', () => {
  it('지금 상세 프로필로 채운다', () => {
    expect(filled).toEqual({
      mbti: ['E', 'N', 'F', 'P'],
      personalColor: 'AUTUMN_WARM',
      hobbies: { tags: ['등산', '독서'], draft: '', error: null },
      age: '32',
      jobTitle: '개발팀 매니저',
    })
  })

  it('채우지 않았으면 빈 폼이고 다섯 항목 모두 기획서 문구로 오류다', () => {
    const errors = detailErrors(initialDetailsForm(null))
    expect(errors).toEqual({
      mbti: '4가지 성향을 모두 선택해 주세요.',
      personalColor: '퍼스널컬러를 선택해 주세요.',
      hobbies: '최소 1개 이상의 취미를 등록해 주세요.',
      age: '올바른 나이를 입력해 주세요. (1~120세)',
      jobTitle: '직급을 입력해 주세요. (최대 15자)',
    })
    expect(missingDetailLabels(errors)).toBe('MBTI·퍼스널컬러·취미·나이·직급')
  })
})

describe('detailErrors', () => {
  it('다 채우면 오류가 없다', () => {
    expect(detailErrors(filled)).toEqual({})
  })

  it('MBTI는 네 축을 모두 골라야 한다', () => {
    expect(detailErrors({ ...filled, mbti: ['E', 'N', null, 'P'] })).toEqual({ mbti: '4가지 성향을 모두 선택해 주세요.' })
  })

  it('취미는 아직 더하지 않은 글도 센다', () => {
    const typed = { ...filled, hobbies: { tags: [], draft: '러닝', error: null } }
    expect(detailErrors(typed)).toEqual({})
    const tooLong = { ...filled, hobbies: { tags: [], draft: '가'.repeat(11), error: null } }
    expect(detailErrors(tooLong)).toEqual({ hobbies: '취미는 10자 이하로 입력해 주세요.' })
  })

  it('직급은 공백을 정리해 1~15자(글자 수는 코드 포인트)', () => {
    expect(detailErrors({ ...filled, jobTitle: '   ' }).jobTitle).toBe('직급을 입력해 주세요. (최대 15자)')
    expect(detailErrors({ ...filled, jobTitle: '😀'.repeat(15) })).toEqual({})
    expect(detailErrors({ ...filled, jobTitle: '😀'.repeat(16) }).jobTitle).toBe('직급을 입력해 주세요. (최대 15자)')
  })

  it('항목 이름은 화면 순서로 잇는다', () => {
    expect(missingDetailLabels({ age: 'x', mbti: 'x' })).toBe('MBTI·나이')
  })
})

describe('나이', () => {
  it('1~120의 정수만 받는다', () => {
    expect(parseAge('0')).toBeNull()
    expect(parseAge('121')).toBeNull()
    expect(parseAge('abc')).toBeNull()
    expect(parseAge('1.5')).toBeNull()
    expect(parseAge('')).toBeNull()
    expect(parseAge('007')).toBe(7)
    expect(parseAge('120')).toBe(120)
  })

  it('입력에서 숫자만 남기고 세 자리까지', () => {
    expect(ageInput('3a2')).toBe('32')
    expect(ageInput('-12.5')).toBe('125')
    expect(ageInput('12345')).toBe('123')
  })
})

describe('취미 태그', () => {
  it('5개·10자, 띄어쓰기·대소문자만 다르면 같은 취미', () => {
    expect(addTags(['보드 게임'], '보드게임', HOBBY_RULE)).toEqual({ tags: ['보드 게임'], error: null, duplicate: true })
    expect(addTags(['1', '2', '3', '4', '5'], '6', HOBBY_RULE).error).toBe('취미는 5개까지 적을 수 있어요.')
    expect(addTags([], '#등산, Golf, golf', HOBBY_RULE).tags).toEqual(['등산', 'Golf'])
  })
})

describe('추천 칩', () => {
  it('누르면 더하고, 적고 있던 글은 그대로 둔다', () => {
    expect(pickTag({ tags: ['등산'], draft: '러닝', error: null }, '독서', HOBBY_RULE)).toEqual({
      tags: ['등산', '독서'],
      draft: '러닝',
      error: null,
    })
  })

  it('5개가 차서 입력 칸이 사라지면 적고 있던 글을 비운다(보이지 않는 글이 저장을 막지 않게)', () => {
    const state = { tags: ['1', '2', '3', '4'], draft: '가'.repeat(11), error: null }
    const picked = pickTag(state, '독서', HOBBY_RULE)
    expect(picked).toEqual({ tags: ['1', '2', '3', '4', '독서'], draft: '', error: null })
    expect(detailErrors({ ...filled, hobbies: picked })).toEqual({})
  })
})

describe('pendingTags', () => {
  it('적고 있던 글까지 넣고, 이미 있는 태그는 건너뛰고, 너무 길면 오류', () => {
    expect(pendingTags({ tags: ['등산'], draft: ' 러닝 ', error: null }, HOBBY_RULE)).toEqual({
      tags: ['등산', '러닝'],
      error: null,
      duplicate: false,
    })
    expect(pendingTags({ tags: ['보드 게임'], draft: '보드게임', error: null }, HOBBY_RULE).tags).toEqual(['보드 게임'])
    expect(pendingTags({ tags: [], draft: '가'.repeat(11), error: null }, HOBBY_RULE).error).toBe('취미는 10자 이하로 입력해 주세요.')
    expect(pendingTags({ tags: ['등산'], draft: '  ', error: null }, HOBBY_RULE)).toEqual({ tags: ['등산'], error: null })
  })
})

describe('detailsChange', () => {
  it('지금과 같으면 바뀌지 않았다', () => {
    expect(detailsChange(me, filled)).toEqual({ details, changed: false })
    // 공백만 다른 직급도 같다(서버가 정리한 값과 비교).
    expect(detailsChange(me, { ...filled, jobTitle: ' 개발팀   매니저 ' }).changed).toBe(false)
  })

  it('하나라도 다르면 바뀌었고 저장할 값을 정리해 준다', () => {
    expect(detailsChange(me, { ...filled, hobbies: { tags: ['독서', '등산'], draft: '', error: null } }).changed).toBe(true)
    const result = detailsChange(me, { ...filled, age: '033', hobbies: { tags: ['등산', '독서'], draft: '러닝', error: null } })
    expect(result).toEqual({ details: { ...details, age: 33, hobbies: ['등산', '독서', '러닝'] }, changed: true })
  })

  it('처음 채우면 바뀐 것이고, 오류가 있으면 저장할 값이 없다', () => {
    expect(detailsChange({ ...me, details: null }, filled).changed).toBe(true)
    expect(detailsChange(me, { ...filled, personalColor: '' }).details).toBeNull()
  })

  it('관리자 회원 상세처럼 상세 프로필만 있는 값과도 비교한다', () => {
    expect(detailsChange({ details }, filled)).toEqual({ details, changed: false })
    expect(detailsChange({ details: null }, filled).changed).toBe(true)
  })
})

describe('isBlankDetailsForm', () => {
  const blank = initialDetailsForm(null)

  it('다섯 항목이 모두 비었으면 빈 폼이다(공백만 적은 칸도 빈 칸)', () => {
    expect(isBlankDetailsForm(blank)).toBe(true)
    expect(isBlankDetailsForm({ ...blank, age: ' ', jobTitle: '  ', hobbies: { tags: [], draft: ' ', error: null } })).toBe(true)
  })

  it('하나라도 고르거나 적었으면 빈 폼이 아니다(취미는 더하지 않은 글도)', () => {
    expect(isBlankDetailsForm(filled)).toBe(false)
    expect(isBlankDetailsForm({ ...blank, mbti: ['E', null, null, null] })).toBe(false)
    expect(isBlankDetailsForm({ ...blank, personalColor: 'SPRING_WARM' })).toBe(false)
    expect(isBlankDetailsForm({ ...blank, hobbies: { tags: [], draft: '러닝', error: null } })).toBe(false)
    expect(isBlankDetailsForm({ ...blank, age: '3' })).toBe(false)
    expect(isBlankDetailsForm({ ...blank, jobTitle: '팀장' })).toBe(false)
  })
})

describe('personalColorLabel', () => {
  it('화면 라벨', () => {
    expect(personalColorLabel('SPRING_WARM')).toBe('봄 웜')
    expect(personalColorLabel('WINTER_COOL')).toBe('겨울 쿨')
  })
})
