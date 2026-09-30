import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import { type Me, meQueryKey } from './me.ts'

export type MyOrganization = {
  id: number
  name: string
  memberCount: number
  hasOpenPollToday: boolean
}

export type Organization = {
  id: number
  name: string
  inviteToken: string
  memberCount: number
}

export type Member = {
  userId: number
  name: string
  profileImageUrl: string | null
  joinedAt: string
}

export type Invite = {
  organizationId: number
  name: string
  memberCount: number
  alreadyMember: boolean
}

// 조직 정보는 자주 바뀌지 않으므로 화면 전환·포커스마다 다시 불러오지 않게 한다.
const ORG_STALE_TIME = 30_000

export const orgKeys = {
  mine: ['orgs', 'mine'] as const,
  detail: (orgId: number) => ['orgs', orgId] as const,
  members: (orgId: number) => ['orgs', orgId, 'members'] as const,
  invite: (token: string) => ['invites', token] as const,
}

export function useMyOrganizations() {
  return useQuery({
    queryKey: orgKeys.mine,
    queryFn: () => api<MyOrganization[]>('/api/me/orgs'),
    staleTime: ORG_STALE_TIME,
  })
}

/** 조회하면 서버가 "최근 들어간 조직"을 갱신하므로, 내 정보 캐시도 맞춰 둔다. */
export function useOrganization(orgId: number) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: orgKeys.detail(orgId),
    queryFn: async () => {
      const org = await api<Organization>(`/api/orgs/${orgId}`)
      queryClient.setQueryData<Me>(meQueryKey, (me) => me && { ...me, lastVisitedOrgId: org.id })
      return org
    },
    enabled: Number.isInteger(orgId),
    staleTime: ORG_STALE_TIME,
  })
}

export function useMembers(orgId: number) {
  return useQuery({
    queryKey: orgKeys.members(orgId),
    queryFn: () => api<Member[]>(`/api/orgs/${orgId}/members`),
    staleTime: ORG_STALE_TIME,
  })
}

export function useCreateOrganization() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => api<Organization>('/api/orgs', { method: 'POST', body: { name } }),
    onSuccess: (org) => {
      queryClient.setQueryData(orgKeys.detail(org.id), org)
      queryClient.invalidateQueries({ queryKey: orgKeys.mine })
    },
  })
}

export function useRenameOrganization(orgId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => api<Organization>(`/api/orgs/${orgId}`, { method: 'PATCH', body: { name } }),
    onSuccess: (org) => {
      queryClient.setQueryData(orgKeys.detail(org.id), org)
      queryClient.invalidateQueries({ queryKey: orgKeys.mine })
    },
  })
}

export function useLeaveOrganization() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (orgId: number) =>
      api<{ organizationDeleted: boolean }>(`/api/orgs/${orgId}/membership`, { method: 'DELETE' }),
    onSuccess: (_, orgId) => {
      queryClient.removeQueries({ queryKey: orgKeys.detail(orgId) })
      queryClient.invalidateQueries({ queryKey: orgKeys.mine })
      queryClient.invalidateQueries({ queryKey: meQueryKey })
    },
  })
}

export function useInvite(token: string) {
  return useQuery({
    queryKey: orgKeys.invite(token),
    queryFn: () => api<Invite>(`/api/invites/${encodeURIComponent(token)}`),
  })
}

export function useJoinInvite(token: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api<{ organizationId: number }>(`/api/invites/${encodeURIComponent(token)}/join`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: orgKeys.mine })
      queryClient.invalidateQueries({ queryKey: orgKeys.invite(token) })
    },
  })
}
