import { type ReactNode, useEffect, useRef } from 'react'

type Props = {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
}

/** 네이티브 <dialog> 모달. Esc와 바깥 클릭으로 닫힌다. 닫혀 있을 때는 내용을 렌더링하지 않아 입력 상태가 초기화된다. */
export default function Modal({ open, onClose, title, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose()
      }}
      aria-labelledby="modal-title"
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl bg-bg-surface p-0 text-text-primary shadow-xl backdrop:bg-bg-scrim"
    >
      {open && (
        <div className="p-5">
          <h2 id="modal-title" className="text-lg font-bold">
            {title}
          </h2>
          <div className="mt-4">{children}</div>
        </div>
      )}
    </dialog>
  )
}
