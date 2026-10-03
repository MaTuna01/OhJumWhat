import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../lib/api.ts'

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
  lastVisitedOrgId: number | null
  /** 관리자면 프로필 메뉴에 관리자 콘솔이 보인다 */
  admin: boolean
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
    mutationFn: () => api<void>('/logout', { method: 'POST' }),
    onSettled: () => {
      queryClient.clear()
      navigate('/login', { replace: true })
    },
  })
}

/**
 * 프로필(이름·사진)을 바꾼 응답을 반영한다. 이름과 사진은 멤버 목록·투표 명단 등 여러 화면에 나오므로
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
