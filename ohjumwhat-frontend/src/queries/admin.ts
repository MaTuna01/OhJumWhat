import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import type { MenuComment } from './comments.ts'
import type { Notice } from './notices.ts'
import type { PollDetail, PollStatus } from './polls.ts'
import type { Schedule } from './schedules.ts'

// 관리자 콘솔(/api/admin/**). 서버가 요청마다 관리자인지 확인한다(아니면 403).

export type Role = 'USER' | 'ADMIN'

export type AdminStats = {
  userCount: number
  organizationCount: number
  todayPollCount: number
  openPollCount: number
  /** 최근 7일 가입 */
  newUserCount: number
  blockedCount: number
}

export type AdminUser = {
  id: number
  /** 화면 이름(별명, 없으면 구글 이름) */
  name: string
  /** 구글 계정 이름 */
  googleName: string
  email: string
  profileImageUrl: string | null
  role: Role
  createdAt: string
  lastLoginAt: string | null
  organizationCount: number
}

export type AdminUserDetail = {
  user: AdminUser
  /** 세션의 마지막 요청 시각(최근 접속) */
  lastAccessAt: string | null
  organizations: { id: number; name: string; memberCount: number; joinedAt: string; lastVisitedAt: string | null }[]
  activity: { pollsCreated: number; menusAdded: number; responses: number }
}

export type AdminOrg = {
  id: number
  name: string
  createdAt: string
  memberCount: number
  pollCount: number
  /** 마지막 투표 날짜(한국 날짜 "YYYY-MM-DD") */
  lastPollDate: string | null
}

export type AdminMember = {
  userId: number
  name: string
  email: string
  profileImageUrl: string | null
  role: Role
  joinedAt: string
  lastVisitedAt: string | null
}

export type AdminPoll = {
  id: number
  title: string
  pollDate: string
  closesAt: string
  status: PollStatus
  scheduled: boolean
  optionCount: number
  responseCount: number
}

export type AdminOrgDetail = {
  organization: AdminOrg
  /** 가입 순. 맨 위가 가장 먼저 들어온 사람(대개 만든 사람) */
  members: AdminMember[]
  /** 최근 30개 */
  polls: AdminPoll[]
  schedules: Schedule[]
}

export type AdminBlock = { id: number; email: string; name: string; blockedAt: string; blockedByName: string | null }

/** 목록은 서버가 최대 이만큼만 준다. */
export const ADMIN_LIST_LIMIT = 100

export const adminKeys = {
  all: ['admin'] as const,
  stats: ['admin', 'stats'] as const,
  users: (q: string) => ['admin', 'users', q] as const,
  user: (userId: number) => ['admin', 'user', userId] as const,
  orgs: (q: string) => ['admin', 'orgs', q] as const,
  org: (orgId: number) => ['admin', 'org', orgId] as const,
  poll: (pollId: number) => ['admin', 'poll', pollId] as const,
  comments: (optionId: number) => ['admin', 'comments', optionId] as const,
  blocks: ['admin', 'blocks'] as const,
}

const search = (q: string) => (q ? `?q=${encodeURIComponent(q)}` : '')

export function useAdminStats() {
  return useQuery({ queryKey: adminKeys.stats, queryFn: () => api<AdminStats>('/api/admin/stats') })
}

export function useAdminUsers(q: string) {
  return useQuery({
    queryKey: adminKeys.users(q),
    queryFn: () => api<AdminUser[]>(`/api/admin/users${search(q)}`),
    placeholderData: keepPreviousData,
  })
}

export function useAdminUser(userId: number) {
  return useQuery({
    queryKey: adminKeys.user(userId),
    queryFn: () => api<AdminUserDetail>(`/api/admin/users/${userId}`),
    enabled: Number.isInteger(userId),
  })
}

export function useAdminOrgs(q: string) {
  return useQuery({
    queryKey: adminKeys.orgs(q),
    queryFn: () => api<AdminOrg[]>(`/api/admin/orgs${search(q)}`),
    placeholderData: keepPreviousData,
  })
}

export function useAdminOrg(orgId: number | undefined) {
  return useQuery({
    queryKey: adminKeys.org(orgId ?? -1),
    queryFn: () => api<AdminOrgDetail>(`/api/admin/orgs/${orgId}`),
    enabled: Number.isInteger(orgId),
  })
}

export function useAdminPoll(pollId: number) {
  return useQuery({
    queryKey: adminKeys.poll(pollId),
    queryFn: () => api<PollDetail>(`/api/admin/polls/${pollId}`),
    enabled: Number.isInteger(pollId),
  })
}

export function useAdminBlocks() {
  return useQuery({ queryKey: adminKeys.blocks, queryFn: () => api<AdminBlock[]>('/api/admin/blocks') })
}

/**
 * 관리자 쓰기 작업 공통: 성공하면 화면에 떠 있는 모든 쿼리를 다시 불러온다.
 * 관리자 자신이 속한 조직이나 투표가 지워졌을 수도 있고 새 소식(점·배너)도 바뀌므로, 관리자 콘솔 밖의 캐시도 함께 맞춘다.
 */
function useAdminMutation<T, V>(mutationFn: (variables: V) => Promise<T>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries(),
  })
}

export const useWithdrawUser = () => useAdminMutation((userId: number) => api<void>(`/api/admin/users/${userId}`, { method: 'DELETE' }))

export const useUnblock = () => useAdminMutation((blockId: number) => api<void>(`/api/admin/blocks/${blockId}`, { method: 'DELETE' }))

export const useDeleteOrg = () => useAdminMutation((orgId: number) => api<void>(`/api/admin/orgs/${orgId}`, { method: 'DELETE' }))

export const useRemoveMember = (orgId: number) =>
  useAdminMutation((userId: number) =>
    api<{ organizationDeleted: boolean }>(`/api/admin/orgs/${orgId}/members/${userId}`, { method: 'DELETE' }),
  )

export const useDeletePoll = () =>
  useAdminMutation(({ pollId, withSchedule }: { pollId: number; withSchedule: boolean }) =>
    api<void>(`/api/admin/polls/${pollId}${withSchedule ? '?withSchedule=true' : ''}`, { method: 'DELETE' }),
  )

export const useDeleteMenuOption = () =>
  useAdminMutation((optionId: number) => api<PollDetail>(`/api/admin/menu-options/${optionId}`, { method: 'DELETE' }))

/** 메뉴의 댓글(마감과 무관하게 지울 수 있다) */
export function useAdminOptionComments(optionId: number) {
  return useQuery({
    queryKey: adminKeys.comments(optionId),
    queryFn: () => api<MenuComment[]>(`/api/admin/menu-options/${optionId}/comments`),
  })
}

export const useDeleteMenuComment = () =>
  useAdminMutation((commentId: number) => api<MenuComment[]>(`/api/admin/menu-comments/${commentId}`, { method: 'DELETE' }))

export const useDeleteSchedule = () =>
  useAdminMutation((scheduleId: number) => api<void>(`/api/admin/schedules/${scheduleId}`, { method: 'DELETE' }))

/** 개발자 노트(새 소식). 업데이트 글은 저장소 파일이 원본이라 여기서 고칠 수 없다(서버 409). */
export type NoticeInput = { title: string; body: string }

export const useCreateNotice = () =>
  useAdminMutation((input: NoticeInput) => api<Notice>('/api/admin/notices', { method: 'POST', body: input }))

export const useUpdateNotice = () =>
  useAdminMutation(({ id, ...input }: NoticeInput & { id: number }) =>
    api<Notice>(`/api/admin/notices/${id}`, { method: 'PUT', body: input }),
  )

export const useDeleteNotice = () =>
  useAdminMutation((noticeId: number) => api<void>(`/api/admin/notices/${noticeId}`, { method: 'DELETE' }))
