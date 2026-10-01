import { useState } from 'react'
import { useNavigate } from 'react-router'
import { defaultCloseTime } from '../lib/time.ts'
import { useCreatePoll } from '../queries/polls.ts'
import Modal from './Modal.tsx'
import PollForm from './PollForm.tsx'

export default function CreatePollModal({ orgId, open, onClose }: { orgId: number; open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} onClose={onClose} title="투표 만들기">
      <CreatePollForm orgId={orgId} onClose={onClose} />
    </Modal>
  )
}

function CreatePollForm({ orgId, onClose }: { orgId: number; onClose: () => void }) {
  const create = useCreatePoll(orgId)
  const navigate = useNavigate()
  const [initial] = useState(() => ({ title: '점심', closesAt: defaultCloseTime(Date.now()) }))

  return (
    <PollForm
      initial={initial}
      submitLabel="만들기"
      hint="만들면 바로 열려요."
      pending={create.isPending}
      error={create.error?.message}
      onCancel={onClose}
      onSubmit={(values) =>
        create.mutate(values, {
          onSuccess: (poll) => {
            onClose()
            navigate(`/orgs/${orgId}/polls/${poll.id}`)
          },
        })
      }
    />
  )
}
