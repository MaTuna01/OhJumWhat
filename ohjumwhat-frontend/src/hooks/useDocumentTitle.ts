import { useEffect } from 'react'

/** 브라우저 탭 제목: "투표 상세 · 오점왓". 값이 아직 없으면(로딩 중) "오점왓" */
export function useDocumentTitle(...parts: (string | null | undefined)[]) {
  const title = [...parts.filter(Boolean), '오점왓'].join(' · ')
  useEffect(() => {
    document.title = title
  }, [title])
}
