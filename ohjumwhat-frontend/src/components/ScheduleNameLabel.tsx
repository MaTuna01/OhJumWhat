import { Fragment } from 'react'
import { scheduleNameParts } from '../lib/scheduleName.ts'

/** 정기 투표 규칙 이름. 날짜 토큰은 Figma DateToken(「오늘 날짜」 칩)으로 그린다. 읽을 때는 scheduleNameText처럼 「[오늘 날짜]」 */
export default function ScheduleNameLabel({ name }: { name: string }) {
  return (
    <>
      {scheduleNameParts(name).map((part, i) =>
        part.kind === 'today' ? <DateToken key={i} /> : <Fragment key={i}>{part.text}</Fragment>,
      )}
    </>
  )
}

function DateToken() {
  return (
    <span className="inline-block rounded-full bg-bg-brand-soft px-1.5 align-middle text-xs leading-5 font-medium text-text-brand">
      <span className="sr-only">[</span>오늘 날짜<span className="sr-only">]</span>
    </span>
  )
}
