import { createContext, useContext } from 'react'
import type { Person } from '../queries/polls.ts'

/** 사람을 받아 멤버 프로필 모달(Figma 07-P)을 연다. 조직 화면 밖에서는 null이다. */
export type OpenProfile = (person: Person) => void

export const ProfileViewerContext = createContext<OpenProfile | null>(null)

/** 프로필 모달 열기. ProfileViewerProvider(OrgLayout) 밖이면 null이라 사람을 눌러도 아무것도 열지 않는다. */
export function useProfileViewer() {
  return useContext(ProfileViewerContext)
}
