import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../lib/api.ts'
import { releaseOnLogout } from '../lib/pushClient.ts'

export type Me = {
  id: number
  /** 화면에 보여줄 이름(별명, 없으면 구글 이름) */
  name: string
  /** 마이페이지에서 정한 별명. 없으면 null */
  nickname: string | null
  /** 구글 계정 이름 */
  googleName: string
  email: string
  /** 화면에 보여줄 사진(올린 사진, 없으면 구글 사진) */
  profileImageUrl: string | null
  /** 구글 프로필 사진. 「구글 사진으로 되돌리기」 미리보기에 쓴다 */
  googleProfileImageUrl: string | null
  /** 직접 올린 사진이 있으면 true */
  customPhoto: boolean
  /** 한줄 소개. 없으면 null */
  bio: string | null
  /** 좋아하는 음식(적은 순서, 최대 3개). 없으면 빈 배열 */
  foodTags: string[]
  /** 상세 프로필(MBTI·퍼스널컬러·취미·나이·직급). 채우지 않았으면 null */
  details: ProfileDetails | null
  lastVisitedOrgId: number | null
  /** 관리자면 프로필 메뉴에 관리자 콘솔이 보인다 */
  admin: boolean
}

export type PersonalColor = 'SPRING_WARM' | 'SUMMER_COOL' | 'AUTUMN_WARM' | 'WINTER_COOL'

/** 상세 프로필(Notion 「22. 프로필 항목 추가」). 다섯 항목은 함께 채워지고 함께 비어 있다. */
export type ProfileDetails = {
  /** 예: ENFP */
  mbti: string
  personalColor: PersonalColor
  /** 취미(적은 순서, 1~5개) */
  hobbies: string[]
  /** 1~120 */
  age: number
  /** 직급(15자) */
  jobTitle: string
}

export const meQueryKey = ['me'] as const

export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: () => api<Me>('/api/me'),
    staleTime: 60_000,
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: async () => {
      // 이 기기의 푸시 등록을 먼저 푼다(1.5초까지만 기다리고, 실패해도 로그아웃한다).
      await releaseOnLogout()
      return api<void>('/logout', { method: 'POST' })
    },
    onSettled: () => {
      queryClient.clear()
      navigate('/login', { replace: true })
    },
  })
}

/**
 * 프로필(이름·사진·소개)을 바꾼 응답을 반영한다. 이름과 사진은 멤버 목록·투표 명단 등 여러 화면에 나오므로
 * 내 정보는 응답으로 바로 바꾸고 나머지 쿼리는 다시 불러온다.
 */
function applyMe(queryClient: QueryClient, me: Me) {
  queryClient.setQueryData(meQueryKey, me)
  queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== meQueryKey[0] })
}

/** 별명 정하기·바꾸기(null이면 구글 이름으로) */
export function useChangeNickname() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (nickname: string | null) => api<Me>('/api/me/nickname', { method: 'PUT', body: { nickname } }),
    onSuccess: (me) => applyMe(queryClient, me),
  })
}

/** 한줄 소개와 좋아하는 음식 정하기(통째로 바꾼다. bio가 null이면 소개를, 빈 배열이면 음식을 지운다) */
export function useChangeIntro() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (intro: { bio: string | null; foodTags: string[] }) =>
      api<Me>('/api/me/profile', { method: 'PUT', body: intro }),
    onSuccess: (me) => applyMe(queryClient, me),
  })
}

/** 상세 프로필 정하기(다섯 항목 모두 필수, 통째로 바꾼다) */
export function useChangeDetails() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (details: ProfileDetails) => api<Me>('/api/me/profile/details', { method: 'PUT', body: details }),
    onSuccess: (me) => applyMe(queryClient, me),
  })
}

export type PhotoChange = { kind: 'upload'; blob: Blob } | { kind: 'reset' }

/** 프로필 사진 올리기(정사각형으로 맞춘 JPEG) 또는 구글 사진으로 되돌리기 */
export function useChangePhoto() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (change: PhotoChange) => {
      if (change.kind === 'reset') {
        return api<Me>('/api/me/photo', { method: 'DELETE' })
      }
      const form = new FormData()
      form.append('photo', change.blob, 'photo.jpg')
      return api<Me>('/api/me/photo', { method: 'POST', body: form })
    },
    onSuccess: (me) => applyMe(queryClient, me),
  })
}
