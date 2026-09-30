import type { Person } from '../queries/polls.ts'
import Avatar from './Avatar.tsx'

/** 참여자 명단의 한 사람. 나는 "(나)"를 붙이고 강조한다. */
export default function PersonChip({ person, isMe, tone = 'surface' }: { person: Person; isMe?: boolean; tone?: 'surface' | 'plain' }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full py-1 pr-2.5 pl-1 ${tone === 'surface' ? 'border border-border-default bg-bg-surface' : 'bg-bg-surface'}`}
    >
      <Avatar name={person.name} imageUrl={person.profileImageUrl} size="sm" />
      <span className={`text-xs font-medium ${isMe ? 'text-text-brand' : 'text-text-secondary'}`}>
        {person.name}
        {isMe && ' (나)'}
      </span>
    </span>
  )
}
