import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
  profileImageUrl: string | null
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
 * 별명 정하기·바꾸기(null이면 구글 이름으로). 이름은 멤버 목록·투표 명단 등 여러 화면에 나오므로
 * 내 정보는 응답으로 바로 바꾸고 나머지 쿼리는 다시 불러온다.
 */
export function useChangeNickname() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (nickname: string | null) => api<Me>('/api/me/nickname', { method: 'PUT', body: { nickname } }),
    onSuccess: (me) => {
      queryClient.setQueryData(meQueryKey, me)
      queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== meQueryKey[0] })
    },
  })
}
