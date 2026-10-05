import { ApiError } from '../lib/api.ts'
import { formatDay } from '../lib/time.ts'
import { useNow } from '../hooks/useNow.ts'
import { useLetterBlocks, useUnblockLetterSender } from '../queries/letters.ts'
import AnonymousAvatar from './AnonymousAvatar.tsx'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

/**
 * 차단한 사람 관리(Figma 10-M4). 실명으로 차단한 사람은 사진·이름, 익명 쪽지에서 차단한 사람은 이름 없이
 * 「익명 쪽지를 보낸 사람」과 그 쪽지의 첫 줄을 보여준다. 「풀기」는 바로 푼다.
 */
export default function LetterBlocksModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const blocks = useLetterBlocks(open)
  const unblock = useUnblockLetterSender()
  const now = useNow(60_000)

  return (
    <Modal open={open} onClose={onClose} title="차단한 사람" closable>
      <p className="text-xs text-text-tertiary">차단한 사람의 쪽지는 받지 않아요. 풀면 차단하기 전에 받은 쪽지가 다시 보여요.</p>
      {blocks.isPending ? (
        <p className="py-6 text-center text-sm text-text-tertiary">불러오는 중…</p>
      ) : blocks.isError ? (
        <p className="py-6 text-center text-sm text-text-danger">차단 목록을 불러오지 못했어요.</p>
      ) : blocks.data.length === 0 ? (
        <p className="py-6 text-center text-sm text-text-tertiary">차단한 사람이 없어요</p>
      ) : (
        <ul className="mt-1 divide-y divide-border-default">
          {blocks.data.map((block) => {
            const name = block.anonymous ? '익명 쪽지를 보낸 사람' : (block.person?.name ?? '탈퇴한 사용자')
            return (
              <li key={block.id} className="flex items-center gap-3 py-3">
                {block.anonymous ? <AnonymousAvatar size="md" /> : <Avatar name={name} imageUrl={block.person?.profileImageUrl} size="md" />}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold">{name}</p>
                  <p className="truncate text-xs text-text-tertiary">
                    {block.anonymous && block.preview ? `“${block.preview}” · ` : ''}
                    {formatDay(block.createdAt, now)} 차단
                  </p>
                </div>
                <Button variant="secondary" className="py-1.5" disabled={unblock.isPending} onClick={() => unblock.mutate(block.id)}>
                  풀기
                </Button>
              </li>
            )
          })}
        </ul>
      )}
      {unblock.error && (
        <p role="alert" className="mt-2 text-sm text-text-danger">
          {unblock.error instanceof ApiError ? unblock.error.message : '풀지 못했어요.'}
        </p>
      )}
    </Modal>
  )
}
