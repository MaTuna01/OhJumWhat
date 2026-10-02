import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { josa } from '../lib/josa.ts'
import { buttonClass, inputClass } from '../lib/ui.ts'
import { type Organization, useCreateOrganization } from '../queries/orgs.ts'
import Button from './Button.tsx'
import InviteLinkField from './InviteLinkField.tsx'
import Modal from './Modal.tsx'

export default function CreateOrgModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} onClose={onClose} title="조직 만들기">
      <CreateOrgForm onClose={onClose} />
    </Modal>
  )
}

function CreateOrgForm({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('')
  const [created, setCreated] = useState<Organization | null>(null)
  const create = useCreateOrganization()

  if (created) {
    return (
      <div>
        <p className="text-sm text-text-secondary">
          <strong className="text-text-primary">{created.name}</strong>{josa(created.name, '을/를')} 만들었어요. 초대 링크를 메신저로 공유해 멤버를 모으세요.
        </p>
        <div className="mt-4">
          <InviteLinkField token={created.inviteToken} />
        </div>
        <div className="mt-6 flex justify-end">
          <Link to={`/orgs/${created.id}`} onClick={onClose} className={buttonClass('primary')}>
            조직으로 가기
          </Link>
        </div>
      </div>
    )
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    create.mutate(name.trim(), { onSuccess: setCreated })
  }

  return (
    <form onSubmit={submit}>
      <label htmlFor="org-name" className="text-sm font-medium">
        조직 이름
      </label>
      <input
        id="org-name"
        autoFocus
        value={name}
        onChange={(e) => setName(e.target.value)}
        maxLength={50}
        placeholder="예: 개발팀"
        className={`${inputClass} mt-1.5`}
      />
      {create.error && (
        <p role="alert" className="mt-2 text-sm text-text-danger">
          {create.error.message}
        </p>
      )}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button type="submit" disabled={!name.trim() || create.isPending}>
          만들기
        </Button>
      </div>
    </form>
  )
}
