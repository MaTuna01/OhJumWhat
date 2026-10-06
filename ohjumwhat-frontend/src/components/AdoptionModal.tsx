import { useProfileViewer } from '../hooks/useProfileViewer.ts'
import { adoptedDayText, medalOf, type PeriodLabel } from '../lib/ranking.ts'
import type { RankingEntry } from '../queries/ranking.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  /** 기록을 볼 사람(없으면 닫힘) */
  entry: RankingEntry | null
  label: PeriodLabel
  today: string
  onClose: () => void
}

/**
 * 채택 기록(Figma 11-M): 보고 있는 기간에 그 사람이 제안해 채택된 메뉴를 이름별로 묶어 횟수 많은 순(같으면 최근 순)으로
 * 보여준다. 데이터는 랭킹 응답에 들어 있어 따로 불러오지 않는다. 「프로필 보기」는 이 모달을 닫고 멤버 프로필(07-P)을 연다.
 */
export default function AdoptionModal({ entry, label, today, onClose }: Props) {
  const openProfile = useProfileViewer()
  const medal = entry ? medalOf(entry.rank) : null

  return (
    <Modal open={entry !== null} onClose={onClose} title="채택 기록" closable>
      {entry && (
        <div className="space-y-3 pb-1">
          <div className="flex items-center gap-3">
            <Avatar name={entry.user.name} imageUrl={entry.user.profileImageUrl} size="lg" />
            <div className="min-w-0 space-y-0.5">
              <p className="truncate font-bold">{entry.user.name}</p>
              <p className={`text-xs font-medium ${medal ? 'text-text-warning' : 'text-text-tertiary'}`}>
                {medal && <span aria-hidden>{medal.emoji} </span>}
                {medal ? `${medal.title} · ` : ''}
                {label.name} {entry.rank}위
              </p>
            </div>
          </div>
          <div className="flex items-center justify-between gap-3 rounded-xl bg-bg-muted px-3.5 py-3">
            <p className="text-xs text-text-secondary">{label.summary}</p>
            <p className="shrink-0 text-lg font-bold tracking-tight">{entry.count}회 채택</p>
          </div>
          <div>
            <h3 className="text-xs font-medium text-text-tertiary">채택된 메뉴</h3>
            <ul>
              {entry.menus.map((menu) => (
                <li key={menu.name} className="flex items-center gap-2 border-b border-border-default py-2.5">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{menu.name}</p>
                    <p className="text-xs text-text-tertiary">
                      {menu.count > 1 ? '마지막 ' : ''}
                      {adoptedDayText(menu.lastAdoptedOn, today)}
                    </p>
                  </div>
                  <p className="shrink-0 text-sm font-bold">{menu.count}회</p>
                </li>
              ))}
            </ul>
          </div>
          {openProfile && (
            <Button
              variant="secondary"
              className="w-full"
              onClick={() => {
                onClose()
                openProfile(entry.user)
              }}
            >
              프로필 보기
            </Button>
          )}
        </div>
      )}
    </Modal>
  )
}
