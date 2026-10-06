import type { RankingEntry } from '../queries/ranking.ts'
import Avatar from './Avatar.tsx'

type Props = {
  /** 포디움 아래(4번째부터)의 사람들 */
  entries: RankingEntry[]
  meId: number | undefined
  onSelect: (entry: RankingEntry) => void
}

/** Figma 「4위부터」 카드: RankRow 목록. 줄을 누르면 채택 기록 모달(11-M)을 연다. */
export default function RankingList({ entries, meId, onSelect }: Props) {
  if (entries.length === 0) return null
  return (
    <section className="space-y-1 rounded-2xl border border-border-default bg-bg-surface px-2 pt-4 pb-3" aria-label="전체 순위">
      {/* 같은 횟수는 같은 순위라 포디움 아래 첫 줄이 4위가 아닐 수도 있다(예: 공동 2위가 셋). */}
      <h2 className="px-3 pb-1 font-bold">{entries[0].rank}위부터</h2>
      <ol className="space-y-1">
        {entries.map((entry) => (
          <li key={entry.user.userId}>
            <RankRow entry={entry} me={entry.user.userId === meId} onSelect={() => onSelect(entry)} />
          </li>
        ))}
      </ol>
    </section>
  )
}

/** Figma RankRow: 순위 · 사진 · 이름(나면 「나」) · 대표 채택 메뉴 · 채택 횟수. Me는 내 줄(bg/brand-soft). */
function RankRow({ entry, me, onSelect }: { entry: RankingEntry; me: boolean; onSelect: () => void }) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`${entry.rank}위 ${entry.user.name}${me ? '(나)' : ''}, ${entry.count}회 채택, 채택 기록 보기`}
      className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left focus-visible:outline-2 focus-visible:outline-border-brand ${
        me ? 'bg-bg-brand-soft' : 'hover:bg-bg-subtle'
      }`}
    >
      <span className={`w-5 shrink-0 text-center text-sm font-bold ${me ? 'text-text-brand-strong' : 'text-text-tertiary'}`}>{entry.rank}</span>
      <Avatar name={entry.user.name} imageUrl={entry.user.profileImageUrl} />
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5">
          <span className="truncate text-sm font-medium">{entry.user.name}</span>
          {me && <span className="shrink-0 rounded-full bg-bg-surface px-1.5 text-xs text-text-tertiary">나</span>}
        </span>
        <span className="block truncate text-xs text-text-tertiary">대표 메뉴 {entry.topMenu}</span>
      </span>
      <span className="shrink-0 text-sm font-bold">{entry.count}회</span>
      <span aria-hidden className="shrink-0 text-sm font-bold text-text-tertiary">
        ›
      </span>
    </button>
  )
}
