import type { ReactNode } from 'react'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  children: ReactNode
  confirmLabel: string
  danger?: boolean
  pending?: boolean
  error?: string | null
}

export default function ConfirmDialog({ open, onClose, onConfirm, title, children, confirmLabel, danger, pending, error }: Props) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="text-sm leading-relaxed text-stone-600">{children}</div>
      {error && (
        <p role="alert" className="mt-3 text-sm text-red-600">
          {error}
        </p>
      )}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} disabled={pending}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
