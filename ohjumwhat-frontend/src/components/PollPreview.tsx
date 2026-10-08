import type { Person, PollOption } from '../queries/polls.ts'
import OptionCard from './OptionCard.tsx'

const previewMe: Person = { userId: 1, name: '김오점', profileImageUrl: null }
const person = (userId: number, name: string): Person => ({ userId, name, profileImageUrl: null })
const previewOptions: PollOption[] = [
  { id: 1, name: '돈까스', link: null, placeName: null, placeAddress: null, kakaoPlaceId: null, placeQuery: null, createdBy: previewMe, voters: [previewMe, person(2, '김철수'), person(3, '정하늘')], mine: true, deletable: false, commentCount: 0 },
  { id: 2, name: '김치찌개', link: null, placeName: null, placeAddress: null, kakaoPlaceId: null, placeQuery: null, createdBy: person(4, '이영희'), voters: [person(5, '박민수'), person(6, '최지우')], mine: false, deletable: false, commentCount: 0 },
]

/** 로그인 화면(D01)과 소개 페이지(D00-W)의 투표 화면 미리보기. 눌리지 않는다(inert). */
export default function PollPreview({ className = '' }: { className?: string }) {
  return (
    <div inert className={`space-y-2.5 ${className}`}>
      {previewOptions.map((option) => (
        <OptionCard key={option.id} option={option} meId={previewMe.userId} selected={option.id === 1} />
      ))}
    </div>
  )
}
