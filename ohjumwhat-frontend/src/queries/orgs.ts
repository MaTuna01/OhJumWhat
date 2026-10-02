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
  /** 검색 지역(예: 역삼동). 「네이버 지도에서 찾기」 검색어 앞에 붙인다. 없으면 null */
  area: string | null
  /** 회사 위치 이름. 없으면 null */
  officeName: string | null
  /** 회사 위치 지도 링크. 없으면 null */
  officeLink: string | null
  /** 회사 주소. 근처 식당 검색·지도의 기준점. 없으면 null */
  officeAddress: string | null
  /** 근처 식당 검색 반경(m): 500, 1000, 2000 */
  searchRadius: number
}

/** 조직 위치(통째로 바꾼다, 빈 값은 지운다) */
export type OrgLocation = {
  area: string | null
  officeLink: string | null
  officeName: string | null
  officeAddress: string | null
  searchRadius: number
}

/** 근처 식당 검색 반경으로 고를 수 있는 값(m) */
export const SEARCH_RADII = [500, 1000, 2000] as const

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

/** 조직 위치 바꾸기(멤버 누구나). 회사 링크는 서버가 정리하고(네이버 공유 링크 → 장소 정식 링크), 회사 주소는 찾을 수 있는지 확인한다. */
export function useUpdateOrgLocation(orgId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (location: OrgLocation) => api<Organization>(`/api/orgs/${orgId}/location`, { method: 'PUT', body: location }),
    onSuccess: (org) => queryClient.setQueryData(orgKeys.detail(org.id), org),
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
