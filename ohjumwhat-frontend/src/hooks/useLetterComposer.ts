import { createContext, useContext } from 'react'
import type { Letter } from '../queries/letters.ts'
import type { Person } from '../queries/polls.ts'

/**
 * 쪽지 쓰기를 무엇으로 여는지
 * - new: 받는 사람이 있으면 고정(멤버 프로필의 「쪽지 보내기」), 없으면 조직·멤버를 고른다(쪽지함의 「쪽지 쓰기」)
 * - reply: 받은 쪽지에 답장
 */
export type ComposeTarget = { kind: 'new'; organizationId?: number; recipient?: Person } | { kind: 'reply'; letter: Letter }

export type LetterComposer = {
  compose: (target: ComposeTarget) => void
  /** 화면 아래에 잠깐 안내를 띄운다(「쪽지를 보냈어요」, 「신고했어요」) */
  notify: (message: string) => void
}

export const LetterComposerContext = createContext<LetterComposer | null>(null)

/** AppLayout 안에서만 쓴다(LetterComposerProvider). */
export function useLetterComposer(): LetterComposer {
  const composer = useContext(LetterComposerContext)
  if (!composer) throw new Error('LetterComposerProvider 밖에서 쪽지 쓰기를 열 수 없어요.')
  return composer
}
