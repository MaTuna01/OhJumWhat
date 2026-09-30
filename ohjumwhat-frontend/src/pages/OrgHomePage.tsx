import { Section } from '../components/PageState.tsx'

// 오늘 열린 투표 카드와 투표 만들기는 4단계에서 추가한다.
export default function OrgHomePage() {
  return (
    <Section title="오늘 열린 투표">
      <p className="rounded-xl bg-bg-subtle px-4 py-8 text-center text-sm text-text-tertiary">오늘 열린 투표가 없어요.</p>
    </Section>
  )
}
