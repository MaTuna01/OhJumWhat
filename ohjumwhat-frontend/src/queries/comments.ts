import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import { withCommentCount } from '../lib/comments.ts'
import { type Person, type PollDetail, pollKeys } from './polls.ts'

/** 메뉴 댓글. author가 null이면 강제 탈퇴로 삭제된 회원("탈퇴한 사용자") */
export type MenuComment = {
  id: number
  author: Person | null
  body: string
  createdAt: string
  edited: boolean
  mine: boolean
}

export const commentKeys = {
  option: (optionId: number) => ['comments', optionId] as const,
}

const commentsUrl = (pollId: number, optionId: number) => `/api/polls/${pollId}/options/${optionId}/comments`

/** 메뉴의 댓글(오래된 순). 펼칠 때 받고, 실시간으로 다시 받지는 않는다(창이 다시 보일 때는 받는다). */
export function useOptionComments(pollId: number, optionId: number) {
  return useQuery({
    queryKey: commentKeys.option(optionId),
    queryFn: () => api<MenuComment[]>(commentsUrl(pollId, optionId)),
  })
}

/** 댓글 쓰기·고치기·지우기는 그 메뉴의 최신 댓글 목록을 돌려주므로 바로 캐시에 넣고, 투표 상세의 댓글 수도 맞춘다. */
function useCommentMutation<T>(pollId: number, optionId: number, request: (arg: T) => Promise<MenuComment[]>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (comments) => {
      queryClient.setQueryData(commentKeys.option(optionId), comments)
      queryClient.setQueryData<PollDetail>(pollKeys.detail(pollId), (detail) =>
        detail ? withCommentCount(detail, optionId, comments.length) : detail,
      )
    },
  })
}

export function useAddComment(pollId: number, optionId: number) {
  return useCommentMutation(pollId, optionId, (body: string) =>
    api<MenuComment[]>(commentsUrl(pollId, optionId), { method: 'POST', body: { body } }),
  )
}

export function useEditComment(pollId: number, optionId: number) {
  return useCommentMutation(pollId, optionId, ({ commentId, body }: { commentId: number; body: string }) =>
    api<MenuComment[]>(`${commentsUrl(pollId, optionId)}/${commentId}`, { method: 'PUT', body: { body } }),
  )
}

export function useDeleteComment(pollId: number, optionId: number) {
  return useCommentMutation(pollId, optionId, (commentId: number) =>
    api<MenuComment[]>(`${commentsUrl(pollId, optionId)}/${commentId}`, { method: 'DELETE' }),
  )
}
