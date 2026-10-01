import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../lib/api.ts'

export type Me = {
  id: number
  name: string
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
